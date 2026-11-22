@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Adjust Client 1.8.9
chcp 65001 >nul 2>&1

rem ===========================================================================
rem  Adjust Client 1.8.9 - launcher
rem
rem  - autodetects the Minecraft directory (%%APPDATA%%\.minecraft) and a Java
rem    runtime (Java 21 or newer is required)
rem  - runs ONLY the binaries shipped next to this script:
rem        adjust-client-1.8.9.jar, libraries\*.jar, natives\*.dll
rem
rem  Optional: pass a custom .minecraft folder as the first argument, e.g.
rem        launcher.bat "D:\Games\.minecraft"
rem  Optional JVM flags can be set through the ADJUST_JVM_ARGS environment
rem  variable, e.g.  set ADJUST_JVM_ARGS=-Xmx4G
rem ===========================================================================

cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem 1. Locate the Minecraft directory (game dir + assets dir)
rem ---------------------------------------------------------------------------
set "MC_DIR=%USERPROFILE%\AppData\Roaming\.minecraft"
if defined APPDATA set "MC_DIR=%APPDATA%\.minecraft"
if not "%~1"=="" set "MC_DIR=%~1"

if exist "%MC_DIR%\." (
    echo [launcher] Minecraft directory: %MC_DIR%
) else (
    echo [launcher] WARNING: "%MC_DIR%" not found yet - the client will create it.
)
if not exist "%MC_DIR%\assets\." (
    echo [launcher] WARNING: no assets folder in "%MC_DIR%" - run vanilla Minecraft once to download sounds, or the game will be silent.
)

rem ---------------------------------------------------------------------------
rem 2. Locate a Java runtime (required: Java 21 or newer)
rem    Candidates are checked in priority order; the lowest major version that
rem    is at least 21 wins, with an exact Java 21 short-circuiting the search.
rem ---------------------------------------------------------------------------
set "BEST_JAVA_EXE="
set /a BEST_JAVA_MAJOR=0

call :consider "%JAVA_HOME%\bin\java.exe"
for /f "delims=" %%P in ('where java 2^>nul') do call :consider "%%P"
for %%D in (
    "%ProgramFiles%\Java"
    "%ProgramFiles%\Eclipse Adoptium"
    "%ProgramFiles%\Microsoft"
    "%ProgramFiles%\Zulu"
    "%ProgramFiles%\Amazon Corretto"
    "%ProgramFiles%\BellSoft"
    "%ProgramFiles%\AdoptOpenJDK"
    "%ProgramFiles%\GraalVM"
    "%ProgramFiles(x86)%\Java"
    "%ProgramFiles(x86)%\Eclipse Adoptium"
    "%LocalAppData%\Programs"
    "%USERPROFILE%\.jdks"
    "%USERPROFILE%\.gradle\jdks"
) do if exist "%%~D" for /f "usebackq delims=" %%F in (`dir /s /b "%%~D\java.exe" 2^>nul`) do call :consider "%%F"

if !BEST_JAVA_MAJOR! LSS 21 (
    echo.
    echo [launcher] ERROR: no Java 21 or newer runtime was found.
    echo [launcher] Please install Java 21 ^(e.g. Eclipse Temurin from https://adoptium.net^)
    echo [launcher] and/or set the JAVA_HOME environment variable to it.
    pause
    exit /b 1
)
set "JAVA_EXE=!BEST_JAVA_EXE!"
echo [launcher] Using Java !BEST_JAVA_MAJOR!: !JAVA_EXE!

rem ---------------------------------------------------------------------------
rem 3. Verify the shipped binaries
rem ---------------------------------------------------------------------------
if not exist "adjust-client-1.8.9.jar" (
    echo [launcher] ERROR: adjust-client-1.8.9.jar is missing next to this script.
    pause
    exit /b 1
)
if not exist "natives\opus.dll" (
    echo [launcher] ERROR: natives\opus.dll is missing next to this script.
    pause
    exit /b 1
)
if not exist "natives\lwjgl.dll" (
    echo [launcher] ERROR: natives\lwjgl.dll is missing next to this script.
    pause
    exit /b 1
)

rem ---------------------------------------------------------------------------
rem 4. Launch (same JVM and game arguments as the dev runClient task)
rem ---------------------------------------------------------------------------
echo [launcher] Starting Adjust Client 1.8.9 ...
echo.
"!JAVA_EXE!" -Xms512M -Xmx2G -Dfile.encoding=UTF-8 "--add-opens=java.base/java.nio=ALL-UNNAMED" "-Djava.library.path=%~dp0natives" "-Dorg.lwjgl.librarypath=%~dp0natives" "-Dorg.lwjgl.opus.libname=%~dp0natives\opus.dll" !ADJUST_JVM_ARGS! -jar "%~dp0adjust-client-1.8.9.jar" --version 1.8.9 --accessToken 0 --gameDir "%MC_DIR%" --assetsDir "%MC_DIR%\assets" --assetIndex 1.8 --userProperties {}
set "EXITCODE=!errorlevel!"
echo.
echo [launcher] Client exited with code !EXITCODE!.
if not "!EXITCODE!"=="0" echo [launcher] A crash report ^(if any^) is in "%MC_DIR%\crash-reports".
pause
exit /b !EXITCODE!

rem ---------------------------------------------------------------------------
rem Helpers
rem ---------------------------------------------------------------------------

rem :consider <java.exe path>
rem Keeps the best candidate so far: any major >= 21, preferring the lowest
rem major (closest to the required 21). An exact 21 short-circuits the search.
:consider
if !BEST_JAVA_MAJOR! EQU 21 goto :eof
if not exist "%~1" goto :eof
call :javamajor "%~1" CAND_MAJOR
if !CAND_MAJOR! LSS 21 goto :eof
if !BEST_JAVA_MAJOR! GTR 0 (
    if !CAND_MAJOR! GEQ !BEST_JAVA_MAJOR! goto :eof
)
set "BEST_JAVA_EXE=%~1"
set /a BEST_JAVA_MAJOR=!CAND_MAJOR!
goto :eof

rem :javamajor <java.exe path> <out var name>
rem Probes the JVM and parses the major version ("1.8.0_202" -> 8,
rem "21.0.8" -> 21). Writes 0 when the version cannot be determined.
:javamajor
set "%~2=0"
set "VSTR="
"%~1" -XshowSettings:properties -version > "%TEMP%\adjust_java_probe.txt" 2>&1
for /f "usebackq tokens=1,2 delims== " %%A in ("%TEMP%\adjust_java_probe.txt") do (
    if not defined VSTR if /i "%%A"=="java.version" set "VSTR=%%B"
)
if not defined VSTR goto :eof
set "MAJOR1="
set "MAJOR2="
for /f "tokens=1,2 delims=._-" %%a in ("!VSTR!") do (
    set "MAJOR1=%%a"
    set "MAJOR2=%%b"
)
if /i "!MAJOR1!"=="1" (set /a "%~2=!MAJOR2!+0") else set /a "%~2=!MAJOR1!+0"
goto :eof
