@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
set "SCRIPT_PATH=%SCRIPT_DIR%scripts\standalone-menu.ps1"
set "POWERSHELL_EXE="

cd /d "%SCRIPT_DIR%" || (
    echo [ERROR] Failed to switch to script directory:
    echo         "%SCRIPT_DIR%"
    exit /b 1
)

if not exist "%SCRIPT_PATH%" (
    echo [ERROR] Standalone menu script was not found:
    echo         "%SCRIPT_PATH%"
    exit /b 2
)

where powershell.exe >nul 2>nul && set "POWERSHELL_EXE=powershell.exe"

if not defined POWERSHELL_EXE (
    where pwsh.exe >nul 2>nul && set "POWERSHELL_EXE=pwsh.exe"
)

if not defined POWERSHELL_EXE (
    echo [ERROR] Could not find Windows PowerShell or PowerShell 7 in PATH.
    echo         Install PowerShell or repair your PATH, then run this tool again.
    exit /b 3
)

"%POWERSHELL_EXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_PATH%"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] Standalone tools exited with code %EXIT_CODE%.
    if /I not "%JESTER_NO_ERROR_PAUSE%"=="1" (
        echo.
        pause
    )
)

exit /b %EXIT_CODE%
