#!/usr/bin/env python3
"""
MaiClient 1.8.9 NetworkManager packet-flow patch.
Disables only the two early returns triggered by DirectionalPacketEvent.isCancelled()
inside channelRead0 and sendPacket. All other custom networking code is retained.
"""
import argparse
import shutil
import struct
import sys
import zipfile
from pathlib import Path

TARGET = "net/minecraft/network/NetworkManager.class"
METHODS = {
    "channelRead0": "(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V",
    "sendPacket": "(Lnet/minecraft/network/Packet;)V",
}

def u1(b, p): return b[p], p + 1
def u2(b, p): return struct.unpack_from(">H", b, p)[0], p + 2
def u4(b, p): return struct.unpack_from(">I", b, p)[0], p + 4

def parse_class(data):
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Target is not a Java class file")
    p = 8
    cp_count, p = u2(data, p)
    cp = [None] * cp_count
    i = 1
    while i < cp_count:
        tag, p = u1(data, p)
        if tag == 1:
            n, p = u2(data, p)
            cp[i] = (tag, data[p:p+n].decode("utf-8", "replace"))
            p += n
        elif tag in (3, 4):
            cp[i] = (tag, data[p:p+4]); p += 4
        elif tag in (5, 6):
            cp[i] = (tag, data[p:p+8]); p += 8; i += 1
        elif tag in (7, 8, 16, 19, 20):
            x, p = u2(data, p); cp[i] = (tag, x)
        elif tag in (9, 10, 11, 12, 17, 18):
            a, p = u2(data, p); c, p = u2(data, p); cp[i] = (tag, a, c)
        elif tag == 15:
            a, p = u1(data, p); c, p = u2(data, p); cp[i] = (tag, a, c)
        else:
            raise ValueError(f"Unknown constant-pool tag {tag}")
        i += 1

    def utf(idx):
        item = cp[idx]
        return item[1] if item and item[0] == 1 else None

    def methodref_name(idx):
        item = cp[idx]
        if not item or item[0] not in (10, 11): return None
        nt = cp[item[2]]
        return (utf(nt[1]), utf(nt[2])) if nt and nt[0] == 12 else None

    p += 6  # access_flags, this_class, super_class
    interfaces, p = u2(data, p)
    p += 2 * interfaces

    fields, p = u2(data, p)
    for _ in range(fields):
        p += 6
        ac, p = u2(data, p)
        for _ in range(ac):
            _, p = u2(data, p); ln, p = u4(data, p); p += ln

    methods_count, p = u2(data, p)
    found = {}
    for _ in range(methods_count):
        access, p = u2(data, p)
        name_idx, p = u2(data, p)
        desc_idx, p = u2(data, p)
        name, desc = utf(name_idx), utf(desc_idx)
        ac, p = u2(data, p)
        for _ in range(ac):
            attr_name_idx, p = u2(data, p)
            attr_len, p = u4(data, p)
            attr_start = p
            if utf(attr_name_idx) == "Code" and name in METHODS and desc == METHODS[name]:
                max_stack, q = u2(data, p)
                max_locals, q = u2(data, q)
                code_len, q = u4(data, q)
                code_start = q
                code = data[code_start:code_start+code_len]
                found[name] = (code_start, code, methodref_name)
            p = attr_start + attr_len
    return found

def instruction_length(code, pc):
    op = code[pc]
    if op in (0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19,
              0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc):
        return 2
    if op in (0x11, 0x13, 0x14, 0x84,
              0x99,0x9a,0x9b,0x9c,0x9d,0x9e,0x9f,0xa0,0xa1,0xa2,0xa3,0xa4,
              0xa5,0xa6,0xa7,0xa8,0xc6,0xc7,
              0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xbb,0xbd,0xc0,0xc1):
        return 3
    if op in (0xb9, 0xba, 0xc8, 0xc9):
        return 5
    if op == 0xc5: return 4
    if op == 0xc4:
        return 6 if code[pc+1] == 0x84 else 4
    if op == 0xaa:
        q = (pc + 4) & ~3
        low = struct.unpack_from(">i", code, q+4)[0]
        high = struct.unpack_from(">i", code, q+8)[0]
        return (q - pc) + 12 + 4 * (high-low+1)
    if op == 0xab:
        q = (pc + 4) & ~3
        n = struct.unpack_from(">i", code, q+4)[0]
        return (q - pc) + 8 + 8*n
    return 1

def patch_method(code, methodref_name, method):
    hits = []
    pc = 0
    while pc < len(code):
        op = code[pc]
        if op == 0xb6:
            cp_index = struct.unpack_from(">H", code, pc+1)[0]
            if methodref_name(cp_index) == ("isCancelled", "()Z"):
                branch = pc + 3
                if branch + 3 <= len(code) and code[branch] == 0x99:
                    ret = branch + 3
                    if ret < len(code) and code[ret] == 0xb1:
                        hits.append((branch, ret))
        step = instruction_length(code, pc)
        if step <= 0: raise ValueError("Invalid bytecode instruction length")
        pc += step

    if len(hits) != 1:
        raise ValueError(f"{method}: expected exactly 1 isCancelled/IFEQ/RETURN sequence; found {len(hits)}")
    branch, ret = hits[0]
    out = bytearray(code)
    # Consume the boolean result but ignore cancellation, retaining byte offsets.
    out[branch:branch+3] = bytes((0x57, 0x00, 0x00))  # POP, NOP, NOP
    out[ret] = 0x00                                  # NOP instead of RETURN
    return bytes(out)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jar", nargs="?", default="client/maiclient-1.8.9.jar")
    args = ap.parse_args()
    jar = Path(args.jar).resolve()
    if not jar.is_file():
        raise SystemExit(f"JAR not found: {jar}")
    backup = jar.with_suffix(jar.suffix + ".before-vanilla-network-patch.bak")
    if backup.exists():
        raise SystemExit(f"Backup already exists; refusing to overwrite: {backup}")
    shutil.copy2(jar, backup)

    tmp = jar.with_suffix(jar.suffix + ".patched.tmp")
    try:
        with zipfile.ZipFile(jar, "r") as src, zipfile.ZipFile(tmp, "w") as dst:
            if TARGET not in src.namelist():
                raise SystemExit(f"{TARGET} not found in {jar}")
            original = src.read(TARGET)
            methods = parse_class(original)
            if set(methods) != set(METHODS):
                raise SystemExit(f"Expected methods {sorted(METHODS)}, found {sorted(methods)}")
            patched = bytearray(original)
            counts = {}
            for name in METHODS:
                offset, code, ref_name = methods[name]
                new_code = patch_method(code, ref_name, name)
                patched[offset:offset+len(new_code)] = new_code
                counts[name] = 1
            for item in src.infolist():
                data = bytes(patched) if item.filename == TARGET else src.read(item.filename)
                dst.writestr(item, data)
        with zipfile.ZipFile(tmp, "r") as check:
            check.testzip()
            check.read(TARGET)
        tmp.replace(jar)
        print("PATCH SUCCESS")
        print(f"JAR: {jar}")
        print(f"BACKUP: {backup}")
        print("Patched: NetworkManager.channelRead0 + NetworkManager.sendPacket")
        print("Preserved: protocol adapter, transforms, packet queues, anticheat and all other classes")
    except Exception:
        if tmp.exists(): tmp.unlink()
        if backup.exists() and not jar.exists(): backup.replace(jar)
        raise

if __name__ == "__main__":
    main()
