@echo off
setlocal

set "MODE=%~1"
if /I not "%MODE%"=="release" set "MODE=debug"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build_modules.ps1" "%MODE%"
exit /b %ERRORLEVEL%
