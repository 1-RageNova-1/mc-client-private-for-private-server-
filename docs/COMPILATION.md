# Client compilation report

## What dist.rar actually is

| Item | Value |
|------|--------|
| Layout | `dist/adjust-client-1.8.9.jar` + `libraries/*.jar` + `natives/*.dll` + `launcher.bat` |
| Minecraft | **1.8.9** (jar name + net.minecraft packages) |
| Main-Class | `net.minecraft.client.main.Main` |
| Java | **21+** (launcher requirement) |
| Build system in archive | **None** — no Gradle/Maven/source. Binary distribution only |
| CFR | Can recover `.java` from the fat client jar (decompiled, not original source) |

## Why a full “working JAR” is not claimed here

1. The archive is a **prebuilt client**, not a source project.
2. Rebuilding requires either:
   - bytecode patching (inject `com.maiclient.api` classes into the existing jar + replace the three account classes), or
   - full decompile of ~5k classes + recompile against the library classpath (large, error-prone, not original source).
3. This environment has **no complete recompile of net.minecraft + Adjust** completed successfully.

## Recommended approach on Windows (bytecode-level merge)

```powershell
# 1. Copy dist folder
# 2. Compile only the new/changed classes against the client jar + libraries
javac --release 21 `
  -cp "dist\adjust-client-1.8.9.jar;dist\libraries\*" `
  -d out `
  client-src\com\maiclient\api\*.java `
  client-src\com\maiclient\ui\account\login\auth\MicrosoftCookieAccount.java `
  client-src\com\maiclient\ui\account\login\auth\MicrosoftCredentialAccount.java `
  client-src\com\maiclient\ui\account\login\auth\MicrosoftRefreshTokenAccount.java

# 3. Package patched jar (update existing jar)
copy dist\adjust-client-1.8.9.jar dist\maiclient-1.8.9.jar
jar uf dist\maiclient-1.8.9.jar -C out .

# Note: package paths inside jar must be com/maiclient/... 
# Account classes in original jar are still com/adjustclient/...
# Either:
#   A) Keep package com.adjustclient for the three Account classes only (match jar paths), or
#   B) Full package rename of entire client (much larger change).
```

### Critical packaging note

Original classes live under `com/adjustclient/...`.  
Integration snapshots use `com.maiclient`.  

**For minimal patch without full rename:** recompile the three account classes as **`com.adjustclient.ui.account.login.auth`** and put API under **`com.maiclient.api`** (new package is fine). Update imports in the three account files accordingly.

## Compilation attempted in this workspace

- Extracted jar + libraries: **OK**
- Decompiled three account classes with CFR: **OK**
- Full client javac of all classes: **NOT attempted** (no source tree of entire client)
- Patched jar produced: **NOT claimed**


## Compilation attempt log (Phase 7)

### Environment
- `java` runtime: OpenJDK 21.0.10
- `javac` default: **17.0.18** (no JDK 21 compiler package in apt)
- Client jar class file version: **65.0 (Java 21)**

### Result
| Target | Result |
|--------|--------|
| `com.maiclient.api.*` (stdlib only) | Compiles with javac 17 |
| Three `Microsoft*Account` against client jar | **FAIL** — classpath classes are v65, javac 17 expects ≤61 |

### Required on Windows
Install **JDK 21** (Temurin/Oracle), ensure `javac -version` shows 21, then:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21..."
$cp = "dist\adjust-client-1.8.9.jar;" + ((Get-ChildItem dist\libraries\*.jar) -join ';')
javac --release 21 -encoding UTF-8 -cp $cp -d out `
  client-src-patch\com\maiclient\api\*.java `
  client-src-patch\com\adjustclient\ui\account\login\auth\*.java
jar uf dist\maiclient-1.8.9.jar -C out .
```

