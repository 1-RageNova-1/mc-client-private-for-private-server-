#!/usr/bin/env python3
"""
Patch MaiClient's 1.8.9 NetworkManager so DirectionalPacketEvent listeners
cannot silently drop packets during connection/login.

Only the cancellation guard is neutralized. EventBus.post still runs, and
DirectionalPacketEvent.packet is still read, so packet listeners may continue
to transform/replace packets. All other NetworkManager logic is retained.

Usage (from the repository root):
    py tools/patch-networkmanager.py
    py tools/patch-networkmanager.py client/maiclient-1.8.9.jar
"""

from __future__ import annotations

import os
import shutil
import struct
import sys
import tempfile
import zipfile
from pathlib import Path

TARGET_CLASS = "net/minecraft/network/NetworkManager.class"
TARGET_METHODS = {"channelRead0", "sendPacket"}
EVENT_OWNER = "com/adjustclient/event/DirectionalPacketEvent"
EVENT_METHOD = "isCancelled"
EVENT_DESC = "()Z"


def u2(data: bytes | bytearray, pos: int) -> int:
    return struct.unpack_from(">H", data, pos)[0]


def u4(data: bytes | bytearray, pos: int) -> int:
    return struct.unpack_from(">I", data, pos)[0]


def parse_constant_pool(data: bytes | bytearray):
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Not a Java class file (bad magic).")

    count = u2(data, 8)
    cp = [None] * count
    pos = 10
    i = 1

    while i < count:
        tag = data[pos]
        pos += 1

        if tag == 1:  # CONSTANT_Utf8
            size = u2(data, pos)
            pos += 2
            cp[i] = ("utf8", bytes(data[pos:pos + size]).decode("utf-8", "replace"))
            pos += size
        elif tag in (3, 4):  # Integer, Float
            cp[i] = (tag,)
            pos += 4
        elif tag in (5, 6):  # Long, Double (two constant-pool slots)
            cp[i] = (tag,)
            pos += 8
            i += 1
        elif tag in (7, 8, 16, 19, 20):  # Class, String, MethodType, Module, Package
            cp[i] = (tag, u2(data, pos))
            pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):  # refs, NameAndType, Dynamic
            cp[i] = (tag, u2(data, pos), u2(data, pos + 2))
            pos += 4
        elif tag == 15:  # MethodHandle
            cp[i] = (tag, data[pos], u2(data, pos + 1))
            pos += 3
        else:
            raise ValueError(f"Unsupported constant-pool tag {tag}.")

        i += 1

    return cp, pos


def cp_utf8(cp, index: int) -> str:
    item = cp[index]
    if not item or item[0] != "utf8":
        raise ValueError(f"Constant-pool entry {index} is not UTF-8.")
    return item[1]


def method_ref_matches(cp, index: int) -> bool:
    item = cp[index]
    if not item or item[0] not in (10, 11):
        return False

    class_item = cp[item[1]]
    nt_item = cp[item[2]]
    if not class_item or class_item[0] != 7 or not nt_item or nt_item[0] != 12:
        return False

    owner = cp_utf8(cp, class_item[1])
    name = cp_utf8(cp, nt_item[1])
    desc = cp_utf8(cp, nt_item[2])
    return owner == EVENT_OWNER and name == EVENT_METHOD and desc == EVENT_DESC


def skip_attributes(data, pos: int, cp):
    count = u2(data, pos)
    pos += 2
    for _ in range(count):
        length = u4(data, pos + 2)
        pos += 6 + length
    return pos


def locate_methods(data: bytearray, cp, after_cp: int):
    pos = after_cp
    pos += 6  # access_flags, this_class, super_class

    interface_count = u2(data, pos)
    pos += 2 + interface_count * 2

    field_count = u2(data, pos)
    pos += 2
    for _ in range(field_count):
        pos += 6  # access_flags, name_index, descriptor_index
        pos = skip_attributes(data, pos, cp)

    method_count = u2(data, pos)
    pos += 2
    found = []

    for _ in range(method_count):
        access = u2(data, pos)
        name_index = u2(data, pos + 2)
        desc_index = u2(data, pos + 4)
        name = cp_utf8(cp, name_index)
        desc = cp_utf8(cp, desc_index)
        attr_count = u2(data, pos + 6)
        pos += 8

        for _ in range(attr_count):
            attr_name = cp_utf8(cp, u2(data, pos))
            attr_len = u4(data, pos + 2)
            info = pos + 6

            if attr_name == "Code":
                code_length = u4(data, info + 4)
                code_start = info + 8
                found.append({
                    "name": name,
                    "desc": desc,
                    "code_start": code_start,
                    "code_end": code_start + code_length,
                })

            pos = info + attr_len

    return found


def cancellation_sites(code: bytearray, start: int, end: int, ref_indices):
    sites = []

    for pos in range(start, end - 7):
        if code[pos] != 0xB6:  # invokevirtual
            continue
        ref_index = u2(code, pos + 1)
        if ref_index not in ref_indices:
            continue

        # The compiled methods use ALOAD_n or ALOAD index immediately before
        # isCancelled(), followed by IFEQ/IFNE, then RETURN.
        if pos >= start + 1 and 0x2A <= code[pos - 1] <= 0x2D:
            site_start = pos - 1
        elif pos >= start + 2 and code[pos - 2] == 0x19:  # ALOAD index
            site_start = pos - 2
        else:
            continue

        branch = pos + 3
        if branch + 3 >= end or code[branch] not in (0x99, 0x9A):
            continue

        branch_offset = struct.unpack_from(">h", code, branch + 1)[0]
        return_pos = branch + 3
        if branch_offset != 4 or code[return_pos] != 0xB1:  # RETURN
            continue

        sites.append((site_start, return_pos + 1))

    return sites


def patch_class(class_bytes: bytes):
    data = bytearray(class_bytes)
    cp, after_cp = parse_constant_pool(data)

    refs = {
        i for i in range(1, len(cp))
        if cp[i] and cp[i][0] in (10, 11) and method_ref_matches(cp, i)
    }
    if not refs:
        raise RuntimeError(
            "Could not find DirectionalPacketEvent.isCancelled() in NetworkManager."
        )

    methods = locate_methods(data, cp, after_cp)
    patched = {}

    for method_name in TARGET_METHODS:
        candidates = [m for m in methods if m["name"] == method_name]
        if not candidates:
            raise RuntimeError(f"NetworkManager method {method_name} was not found.")

        method_sites = []
        for method in candidates:
            method_sites.extend(
                cancellation_sites(
                    data, method["code_start"], method["code_end"], refs
                )
            )

        if len(method_sites) != 1:
            raise RuntimeError(
                f"Expected exactly one cancellable-event guard in {method_name}; "
                f"found {len(method_sites)}. No JAR changes have been made."
            )

        start, end = method_sites[0]
        data[start:end] = b"\x00" * (end - start)
        patched[method_name] = end - start

    return bytes(data), patched


def is_signature_file(name: str) -> bool:
    upper = name.upper()
    if not upper.startswith("META-INF/"):
        return False
    base = upper.rsplit("/", 1)[-1]
    return (
        base.endswith(".SF")
        or base.endswith(".RSA")
        or base.endswith(".DSA")
        or base.endswith(".EC")
        or base.startswith("SIG-")
    )


def patch_jar(jar_path: Path):
    if not jar_path.is_file():
        raise FileNotFoundError(f"JAR not found: {jar_path}")

    backup = jar_path.with_name(jar_path.name + ".pre-network-fix.bak")
    if backup.exists():
        raise FileExistsError(
            f"Backup already exists: {backup}\n"
            "Rename that backup first if you intentionally want to patch again."
        )

    with zipfile.ZipFile(jar_path, "r") as source:
        try:
            original_class = source.read(TARGET_CLASS)
        except KeyError:
            raise RuntimeError(f"{TARGET_CLASS} is missing from {jar_path}")

        patched_class, counts = patch_class(original_class)

        shutil.copy2(jar_path, backup)
        fd, temp_name = tempfile.mkstemp(
            prefix=jar_path.stem + ".", suffix=".tmp", dir=jar_path.parent
        )
        os.close(fd)
        temp_path = Path(temp_name)

        try:
            with zipfile.ZipFile(temp_path, "w", allowZip64=True) as output:
                output.comment = source.comment
                for info in source.infolist():
                    if is_signature_file(info.filename):
                        continue
                    payload = (
                        patched_class
                        if info.filename == TARGET_CLASS
                        else source.read(info.filename)
                    )
                    output.writestr(info, payload)

            # Verify the output archive and confirm both cancellation guards
            # are gone before replacing the original.
            with zipfile.ZipFile(temp_path, "r") as check:
                verified, _ = patch_class_probe(check.read(TARGET_CLASS))
                if not verified:
                    raise RuntimeError("Post-write verification failed.")

            os.replace(temp_path, jar_path)
        except Exception:
            if temp_path.exists():
                temp_path.unlink()
            raise

    return backup, counts


def patch_class_probe(class_bytes: bytes):
    data = bytearray(class_bytes)
    cp, after_cp = parse_constant_pool(data)
    refs = {
        i for i in range(1, len(cp))
        if cp[i] and cp[i][0] in (10, 11) and method_ref_matches(cp, i)
    }
    methods = locate_methods(data, cp, after_cp)
    remaining = 0
    for method in methods:
        if method["name"] in TARGET_METHODS:
            remaining += len(
                cancellation_sites(
                    data, method["code_start"], method["code_end"], refs
                )
            )
    return remaining == 0, remaining


def main():
    default_path = Path("client/maiclient-1.8.9.jar")
    jar_path = Path(sys.argv[1]) if len(sys.argv) > 1 else default_path
    jar_path = jar_path.resolve()

    backup, counts = patch_jar(jar_path)

    print("PATCH SUCCESSFUL")
    print(f"JAR:    {jar_path}")
    print(f"BACKUP: {backup}")
    for method, byte_count in counts.items():
        print(f"{method}: disabled cancellation guard ({byte_count} bytecode bytes)")
    print("EventBus and packet replacement remain enabled.")
    print("Only the two silent-cancellation guards were bypassed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"PATCH FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
