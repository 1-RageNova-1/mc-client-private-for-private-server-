@echo off
setlocal EnableExtensions EnableDelayedExpansion
title MaiClient 1.8.9
chcp 65001 >nul 2>&1
cd /d "%~dp0"
if not defined MAICLIENT_API set "MAICLIENT_API=http://127.0.0.1:8787"
set "MC_DIR=%APPDATA%\.minecraft"
if not "%~1"=="" set "MC_DIR=%~1"
set "BEST_JAVA_EXE="
set /a BEST_JAVA_MAJOR=0
call :consider "%JAVA_HOME%\bin\java.exe"
for /f "delims=" %%P in ('where java 2^>nul') do call :consider "%%P"
for %%D in ("%ProgramFiles%\Java" "%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Zulu" "%ProgramFiles%\Amazon Corretto" "%LocalAppData%\Programs") do if exist "%%~D" for /f "usebackq delims=" %%F in (`dir /s /b "%%~D\java.exe" 2^>nul`) do call :consider "%%F"
if not defined BEST_JAVA_EXE (echo [MaiClient] Java 21+ required. https://adoptium.net & pause & exit /b 1)
if !BEST_JAVA_MAJOR! LSS 21 (echo [MaiClient] Need Java 21+ & pause & exit /b 1)
echo [MaiClient] Java=%BEST_JAVA_EXE% API=%MAICLIENT_API%
set "NATIVE_PATH=%~dp0natives"
set "CP=%~dp0maiclient-1.8.9.jar"
for %%J in ("%~dp0libraries\*.jar") do set "CP=!CP!;%%~fJ"
"%BEST_JAVA_EXE%" -Xmx2G -Djava.library.path="%NATIVE_PATH%" -Dmaiclient.api="%MAICLIENT_API%" -cp "%CP%" net.minecraft.client.main.Main --version MaiClient-1.8.9 --gameDir "%MC_DIR%" --assetsDir "%MC_DIR%\assets" --assetIndex 1.8 --userProperties {} --username Player --uuid 0 --accessToken 0 --userType legacy
goto :eof
:consider
set "CAND=%~1"
if not exist "%CAND%" goto :eof
for /f "tokens=3" %%V in ('"%CAND%" -version 2^>^&1 ^| findstr /i "version"') do (
  set "VER=%%V" & set "VER=!VER:"=!"
  for /f "tokens=1 delims=." %%M in ("!VER!") do set /a MAJ=%%M
  if !MAJ! GEQ 21 (
    if !BEST_JAVA_MAJOR! EQU 0 (set "BEST_JAVA_EXE=!CAND!" & set /a BEST_JAVA_MAJOR=!MAJ!)
    if !MAJ! LSS !BEST_JAVA_MAJOR! (set "BEST_JAVA_EXE=!CAND!" & set /a BEST_JAVA_MAJOR=!MAJ!)
    if !MAJ! EQU 21 (set "BEST_JAVA_EXE=!CAND!" & set /a BEST_JAVA_MAJOR=21)
  )
)
goto :eof
