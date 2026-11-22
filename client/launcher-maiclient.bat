
@echo off
setlocal EnableExtensions EnableDelayedExpansion
title MaiClient 1.8.9
chcp 65001 >nul 2>&1

cd /d "%~dp0"

if not defined MAICLIENT_API set "MAICLIENT_API=http://127.0.0.1:8787"

set "MC_DIR=%APPDATA%\.minecraft"
if not "%~1"=="" set "MC_DIR=%~1"

REM Check Java
where java >nul 2>&1
if errorlevel 1 (
    echo [MaiClient] Java not found in PATH.
    pause
    exit /b 1
)

for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "VER=%%~V"
)

for /f "tokens=1 delims=." %%M in ("!VER!") do set /a JAVA_MAJOR=%%M

if not defined JAVA_MAJOR (
    echo [MaiClient] Could not detect Java version.
    pause
    exit /b 1
)

if !JAVA_MAJOR! LSS 21 (
    echo [MaiClient] Java 21 or newer is required. Found !VER!
    pause
    exit /b 1
)

echo [MaiClient] Java version: !VER!
echo [MaiClient] API: %MAICLIENT_API%

set "NATIVE_PATH=%~dp0natives"
set "CP=%~dp0maiclient-1.8.9.jar"

for %%J in ("%~dp0libraries\*.jar") do (
    if exist "%%~fJ" set "CP=!CP!;%%~fJ"
)

java -Xmx2G ^
-Djava.library.path="%NATIVE_PATH%" ^
-Dmaiclient.api="%MAICLIENT_API%" ^
-cp "%CP%" ^
net.minecraft.client.main.Main ^
--version MaiClient-1.8.9 ^
--gameDir "%MC_DIR%" ^
--assetsDir "%MC_DIR%\assets" ^
--assetIndex 1.8 ^
--userProperties {} ^
--username Player ^
--uuid 0 ^
--accessToken 0 ^
--userType legacy

echo.
echo [MaiClient] Client exited with code !ERRORLEVEL!
pause
