@echo off
cd /d "%~dp0"
if not defined JWT_SECRET set "JWT_SECRET=dev_only_change_me_16chars"
set "MEMORY_DB=1"
set "PORT=8787"
echo Starting MaiClient API (MEMORY_DB) on port %PORT% ...
node src\standalone.js
pause
