@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "REMOTE_TMP=/data/local/tmp/jester-moods-test"

 echo.
 echo ============================================================
 echo   Jester Mods - module build/install/test helper
 echo ============================================================
 echo.

rem ---- Select game module(s) --------------------------------------------------
set "TARGET_GAME=%~1"
set "EXECUTION_MODE=%~2"
set "DIAGNOSTIC_MODE=%~3"
set "MODULE_SETUP_MODE=%~4"
set "LAUNCHER_BUILD=%~5"
set "MODULE_DROP=%~6"
set "SKIP_MODULE_BUILD="
set "MODULE_ONLY_TEST="
set "MODULE_BUILD_MODE=debug"
set "EXIT_CODE=0"
set "FAIL_CONTEXT="

goto main

:fail
if not defined EXIT_CODE set "EXIT_CODE=1"
if "!EXIT_CODE!"=="0" set "EXIT_CODE=1"
if defined GRADLE_SUBST_DRIVE (
    "%SystemRoot%\System32\subst.exe" !GRADLE_SUBST_DRIVE! /d >nul 2>nul
)
if defined REMOTE_TMP if defined ADB_EXE if defined DEVICE_SERIAL (
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "rm -rf %REMOTE_TMP%" >nul 2>nul
)
if defined LOCAL_TEST_TOKEN_FILE if exist "!LOCAL_TEST_TOKEN_FILE!" del /q "!LOCAL_TEST_TOKEN_FILE!" >nul 2>nul
if defined MODULE_INPUT_FILE if exist "!MODULE_INPUT_FILE!" del /q "!MODULE_INPUT_FILE!" >nul 2>nul
if defined MODULE_STAGE_FILE if exist "!MODULE_STAGE_FILE!" del /q "!MODULE_STAGE_FILE!" >nul 2>nul
if defined INSTALLED_LAUNCHER_FILE if exist "!INSTALLED_LAUNCHER_FILE!" del /q "!INSTALLED_LAUNCHER_FILE!" >nul 2>nul
if defined LOCAL_TEST_ROOT if defined ADB_EXE if defined DEVICE_SERIAL (
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "rm -rf !LOCAL_TEST_ROOT!" >nul 2>nul
)
echo.
echo ============================================================
echo   Jester Mods test helper failed
echo ============================================================
echo [FAILED] Exit code !EXIT_CODE!
if defined FAIL_CONTEXT echo [WHERE] !FAIL_CONTEXT!
echo.
echo The full command output above is left visible so the real build/install
echo error can be copied instead of disappearing with the window.
if /I not "!JESTER_NO_ERROR_PAUSE!"=="1" (
    echo.
    pause
)
exit /b !EXIT_CODE!

:main
rem Backward compatibility: a single root/nonroot argument still targets Cooking Madness.
if /I "%~1"=="root" (
    set "TARGET_GAME=cooking"
    set "EXECUTION_MODE=root"
)
if /I "%~1"=="nonroot" (
    set "TARGET_GAME=cooking"
    set "EXECUTION_MODE=nonroot"
)
if /I "%~1"=="non-root" (
    set "TARGET_GAME=cooking"
    set "EXECUTION_MODE=nonroot"
)

if not defined TARGET_GAME if defined JESTER_MODULES set "TARGET_GAME=%JESTER_MODULES%"
if not defined TARGET_GAME if defined JESTER_GAME set "TARGET_GAME=%JESTER_GAME%"
if not defined EXECUTION_MODE if defined JESTER_MODE set "EXECUTION_MODE=%JESTER_MODE%"
if not defined MODULE_SETUP_MODE if defined JESTER_MODULE_SETUP set "MODULE_SETUP_MODE=%JESTER_MODULE_SETUP%"
if not defined LAUNCHER_BUILD if defined JESTER_LAUNCHER_BUILD set "LAUNCHER_BUILD=%JESTER_LAUNCHER_BUILD%"
if not defined MODULE_DROP if defined JESTER_MODULE_DIR set "MODULE_DROP=%JESTER_MODULE_DIR%"
if defined JESTER_SKIP_MODULE_BUILD set "SKIP_MODULE_BUILD=%JESTER_SKIP_MODULE_BUILD%"
if /I "%JESTER_MODULE_ONLY_TEST%"=="1" set "MODULE_ONLY_TEST=1"
if defined JESTER_MODULE_BUILD_MODE set "MODULE_BUILD_MODE=%JESTER_MODULE_BUILD_MODE%"

if defined TARGET_GAME if exist "%TARGET_GAME%\config.json" (
    set "MODULE_DROP=%TARGET_GAME%"
    set "TARGET_GAME="
)

rem Let direct callers omit the blank diagnostic placeholder:
rem   test_helper.cmd cooking root stage release
rem instead of:
rem   test_helper.cmd cooking root "" stage release
if not defined LAUNCHER_BUILD (
    if /I "!DIAGNOSTIC_MODE!"=="download" (
        echo [ERROR] The download/catalog module test path was removed.
        echo         Use stage/local so tests always use locally staged module files.
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=Download module tests are disabled."
        goto fail
    ) else if /I "!DIAGNOSTIC_MODE!"=="stage" (
        set "LAUNCHER_BUILD=!MODULE_SETUP_MODE!"
        set "MODULE_SETUP_MODE=stage"
        set "DIAGNOSTIC_MODE="
    ) else if /I "!DIAGNOSTIC_MODE!"=="local" (
        set "LAUNCHER_BUILD=!MODULE_SETUP_MODE!"
        set "MODULE_SETUP_MODE=stage"
        set "DIAGNOSTIC_MODE="
    )
)
if not defined MODULE_SETUP_MODE (
    if /I "!DIAGNOSTIC_MODE!"=="debug" (
        set "LAUNCHER_BUILD=debug"
        set "DIAGNOSTIC_MODE="
    ) else if /I "!DIAGNOSTIC_MODE!"=="release" (
        set "LAUNCHER_BUILD=release"
        set "DIAGNOSTIC_MODE="
    ) else if /I "!DIAGNOSTIC_MODE!"=="hardened" (
        set "LAUNCHER_BUILD=release"
        set "DIAGNOSTIC_MODE="
    ) else if /I "!DIAGNOSTIC_MODE!"=="hardenedtest" (
        set "LAUNCHER_BUILD=release"
        set "DIAGNOSTIC_MODE="
    ) else if /I "!DIAGNOSTIC_MODE!"=="hardened-test" (
        set "LAUNCHER_BUILD=release"
        set "DIAGNOSTIC_MODE="
    )
)

if not defined TARGET_GAME if not defined MODULE_DROP (
    echo.
    echo Choose test target:
    echo   [1] Launcher only
    echo   [2] Launcher + local module folder^(s^) ^(drag/drop together^)
    echo   [3] Local module folder^(s^) only ^(reuse installed launcher^)
    echo.
    choice /C 123 /N /M "Select target [1/2/3]: "
    if errorlevel 3 (
        set "MODULE_ONLY_TEST=1"
        set /p "MODULE_DROP=Module folder(s): "
        if not defined MODULE_DROP (
            echo [ERROR] No module folder was provided.
            set "EXIT_CODE=1"
            set "FAIL_CONTEXT=No module folder was provided."
            goto fail
        )
    ) else if errorlevel 2 (
        set /p "MODULE_DROP=Module folder(s): "
        if not defined MODULE_DROP (
            echo [ERROR] No module folder was provided.
            set "EXIT_CODE=1"
            set "FAIL_CONTEXT=No module folder was provided."
            goto fail
        )
    ) else (
        set "TARGET_GAME=launcher"
    )
)

if defined MODULE_DROP (
    set "MODULE_INPUT_FILE=%TEMP%\jester-moods-module-drop-%RANDOM%.txt"
    set "JESTER_MODULE_DROP_INPUT=!MODULE_DROP!"
    powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%CD%\scripts\resolve-test-module-drops.ps1" -OutputPath "!MODULE_INPUT_FILE!"
    if errorlevel 1 (
        if exist "!MODULE_INPUT_FILE!" del /q "!MODULE_INPUT_FILE!" >nul 2>nul
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=One or more manual module folders are invalid."
        goto fail
    )
    set "TARGET_GAME=manual-drops"
)

if not defined TARGET_GAME (
    echo [ERROR] No test target was selected.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=No test target was selected."
    goto fail
)

if /I "!TARGET_GAME!"=="1" set "TARGET_GAME=com.biglime.cookingmadness"
if /I "!TARGET_GAME!"=="2" set "TARGET_GAME=com.ChillyRoom.DungeonShooter"
if /I "!TARGET_GAME!"=="3" set "TARGET_GAME=launcher"
if /I "!TARGET_GAME!"=="cookingmadness" set "TARGET_GAME=cooking"
if /I "!TARGET_GAME!"=="com.biglime.cookingmadness" set "TARGET_GAME=cooking"
if /I "!TARGET_GAME!"=="avatar" set "TARGET_GAME=com.pazugames.avatarworld"
if /I "!TARGET_GAME!"=="avatarworld" set "TARGET_GAME=com.pazugames.avatarworld"
if /I "!TARGET_GAME!"=="avatar-world" set "TARGET_GAME=com.pazugames.avatarworld"
if /I "!TARGET_GAME!"=="launcher-only" set "TARGET_GAME=launcher"
if /I "!TARGET_GAME!"=="soulknight" set "TARGET_GAME=com.ChillyRoom.DungeonShooter"
if /I "!TARGET_GAME!"=="soul-knight" set "TARGET_GAME=com.ChillyRoom.DungeonShooter"

if /I "!TARGET_GAME!"=="cooking" set "TARGET_GAME=com.biglime.cookingmadness"

set "TARGET_MODULES="
set "TARGET_LABEL=Selected modules"
set /a MODULE_COUNT=0

if /I "!TARGET_GAME!"=="launcher" (
    set "LAUNCHER_ONLY=1"
    set "TARGET_LABEL=Launcher only"
) else if defined MODULE_INPUT_FILE (
    for /f "usebackq tokens=1,2,* delims=|" %%M in ("!MODULE_INPUT_FILE!") do (
        set "TARGET_MODULES=!TARGET_MODULES! %%M"
        if not defined TARGET_MODULE set "TARGET_MODULE=%%M"
        set /a MODULE_COUNT+=1
    )
    if !MODULE_COUNT! EQU 1 (
        set "TARGET_LABEL=!TARGET_MODULE!"
    ) else (
        set "TARGET_LABEL=!MODULE_COUNT! selected modules"
    )
) else (
    if not "!TARGET_GAME:,=!"=="!TARGET_GAME!" (
        echo [ERROR] Multiple-module device tests were removed.
        echo         Drop or name exactly one local module folder/package.
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=Multiple-module device tests are disabled."
        goto fail
    )
    if /I "!TARGET_GAME!"=="all" (
        echo [ERROR] All-module device tests were removed.
        echo         Drop or name exactly one local module folder/package.
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=All-module device tests are disabled."
        goto fail
    )
    set "MODULE_PACKAGE=!TARGET_GAME!"
    if /I "!MODULE_PACKAGE!"=="cooking" set "MODULE_PACKAGE=com.biglime.cookingmadness"
    if /I "!MODULE_PACKAGE!"=="soulknight" set "MODULE_PACKAGE=com.ChillyRoom.DungeonShooter"
    if /I "!MODULE_PACKAGE!"=="soul-knight" set "MODULE_PACKAGE=com.ChillyRoom.DungeonShooter"
    if /I "!MODULE_PACKAGE!"=="wild" set "MODULE_PACKAGE=com.ctugames.km2"
    if /I "!MODULE_PACKAGE!"=="wild-darkness" set "MODULE_PACKAGE=com.ctugames.km2"
    if /I "!MODULE_PACKAGE!"=="darkness" set "MODULE_PACKAGE=com.ctugames.km2"
    if not exist "%CD%\module-output\!MODULE_PACKAGE!\config.json" if not exist "%CD%\modules\!MODULE_PACKAGE!\config.json" (
        echo [ERROR] Unknown test module: !MODULE_PACKAGE!
        echo         Drop a module-output folder, drop a module source folder, or pass one known package.
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=Unknown test module: !MODULE_PACKAGE!"
        goto fail
    )
    set "TARGET_MODULE=!MODULE_PACKAGE!"
    set "TARGET_MODULES=!MODULE_PACKAGE!"
    set /a MODULE_COUNT=1
    set "TARGET_LABEL=!MODULE_PACKAGE!"
)

rem ---- Select root / non-root execution mode ----------------------------------
if not defined EXECUTION_MODE (
    echo.
    echo Choose launcher mode:
    echo   [1] Root     - inject into the installed game with root
    echo   [2] Non-root - launch through the non-root/BlackBox mode
    echo   [3] Both     - run the same test for Root, then Non-root
    echo.
    choice /C 123 /N /M "Select mode [1/2/3]: "
    if errorlevel 3 (
        set "EXECUTION_MODE=both"
    ) else if errorlevel 2 (
        set "EXECUTION_MODE=nonroot"
    ) else (
        set "EXECUTION_MODE=root"
    )
)

if /I "!EXECUTION_MODE!"=="1" set "EXECUTION_MODE=root"
if /I "!EXECUTION_MODE!"=="2" set "EXECUTION_MODE=nonroot"
if /I "!EXECUTION_MODE!"=="3" set "EXECUTION_MODE=both"
if /I "!EXECUTION_MODE!"=="non-root" set "EXECUTION_MODE=nonroot"

rem ---- Select launcher build type ---------------------------------------------
if not defined LAUNCHER_BUILD (
    echo.
    echo Choose launcher build:
    echo   [1] Debug        - debuggable, supports local module staging
    echo   [2] Release      - production-signed behavior, supports local ADB staging
    echo.
    choice /C 12 /N /M "Select launcher build [1/2]: "
    if errorlevel 2 (
        set "LAUNCHER_BUILD=release"
    ) else (
        set "LAUNCHER_BUILD=debug"
    )
)

if /I "!LAUNCHER_BUILD!"=="1" set "LAUNCHER_BUILD=debug"
if /I "!LAUNCHER_BUILD!"=="2" set "LAUNCHER_BUILD=release"
if /I "!LAUNCHER_BUILD!"=="3" set "LAUNCHER_BUILD=release"
if /I "!LAUNCHER_BUILD!"=="hardened" set "LAUNCHER_BUILD=release"
if /I "!LAUNCHER_BUILD!"=="hardenedtest" set "LAUNCHER_BUILD=release"
if /I "!LAUNCHER_BUILD!"=="hardened-test" set "LAUNCHER_BUILD=release"

rem ---- Select locally staged module test --------------------------------------
if defined LAUNCHER_ONLY set "MODULE_SETUP_MODE=launcher-only"
if not defined MODULE_SETUP_MODE (
    set "MODULE_SETUP_MODE=stage"
    echo.
    echo Module test: local stage only
    echo   !MODULE_COUNT! selected local module folder^(s^) will be copied into Launcher storage.
    if /I "!LAUNCHER_BUILD!"=="release" (
        echo   Release staging uses an ADB-only local import, not the live catalog.
    )
)

if /I "!MODULE_SETUP_MODE!"=="1" set "MODULE_SETUP_MODE=stage"
if /I "!MODULE_SETUP_MODE!"=="2" set "MODULE_SETUP_MODE=stage"
if /I "!MODULE_SETUP_MODE!"=="local" set "MODULE_SETUP_MODE=stage"

if /I "!MODULE_SETUP_MODE!"=="download" (
    echo [ERROR] The download/catalog module test path was removed.
    echo         Use stage/local so tests always use locally staged module files.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Download module tests are disabled."
    goto fail
)

if /I not "!MODULE_SETUP_MODE!"=="stage" if /I not "!MODULE_SETUP_MODE!"=="launcher-only" (
    echo [ERROR] Unknown module test: !MODULE_SETUP_MODE!
    echo         Use stage/local, or omit it for local stage.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Unknown module setup mode: !MODULE_SETUP_MODE!"
    goto fail
)
rem ---- Optional diagnostic mode ---------------------------------------------
set "GOOGLE_SIGNIN_DIAG=0"
if /I "!DIAGNOSTIC_MODE!"=="google" set "GOOGLE_SIGNIN_DIAG=1"
if /I "!DIAGNOSTIC_MODE!"=="google-diag" set "GOOGLE_SIGNIN_DIAG=1"
if /I "!DIAGNOSTIC_MODE!"=="signin" set "GOOGLE_SIGNIN_DIAG=1"
if /I "!DIAGNOSTIC_MODE!"=="diag" set "GOOGLE_SIGNIN_DIAG=1"
if "!GOOGLE_SIGNIN_DIAG!"=="1" (
    if defined LAUNCHER_ONLY (
        echo [ERROR] Google sign-in diagnostics require a game target.
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=Google sign-in diagnostics require a game target."
        goto fail
    )
    if not "!MODULE_COUNT!"=="1" (
        echo [ERROR] Google sign-in diagnostics require exactly one module target.
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=Google sign-in diagnostics require exactly one module target."
        goto fail
    )
    if /I "!EXECUTION_MODE!"=="both" (
        echo [ERROR] Google sign-in diagnostics cannot run in Both mode.
        echo         Select Non-root so the interactive capture runs only once.
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=Google sign-in diagnostics do not support Both mode."
        goto fail
    ) else if /I not "!EXECUTION_MODE!"=="nonroot" (
        echo [ERROR] Google sign-in diagnostics require non-root mode.
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=Google sign-in diagnostics require non-root mode."
        goto fail
    )
)

rem Keep direct test_helper.cmd use consistent with standalone-tools.cmd. Run each flavor in
rem its own process so all flavor-specific package, build, install, and staging state stays isolated.
if /I "!EXECUTION_MODE!"=="both" (
    echo.
    echo ============================================================
    echo   Running Root launcher test
    echo ============================================================
    call "%~f0" "!TARGET_GAME!" root "!DIAGNOSTIC_MODE!" "!MODULE_SETUP_MODE!" "!LAUNCHER_BUILD!" "!MODULE_DROP!"
    if errorlevel 1 (
        set "EXIT_CODE=!ERRORLEVEL!"
        set "FAIL_CONTEXT=Root launcher pass failed in Both mode."
        goto fail
    )
    set "JESTER_SKIP_MODULE_BUILD=1"
    echo.
    echo ============================================================
    echo   Running Non-root launcher test
    echo ============================================================
    call "%~f0" "!TARGET_GAME!" nonroot "!DIAGNOSTIC_MODE!" "!MODULE_SETUP_MODE!" "!LAUNCHER_BUILD!" "!MODULE_DROP!"
    if errorlevel 1 (
        set "EXIT_CODE=!ERRORLEVEL!"
        set "FAIL_CONTEXT=Non-root launcher pass failed in Both mode."
        goto fail
    )
    echo.
    echo [DONE] Root and Non-root launcher tests completed successfully.
    exit /b 0
)

if /I "!EXECUTION_MODE!"=="root" (
    set "APP_ID=com.moodtools.hub.root"
    set "MODE_LABEL=Root"
) else if /I "!EXECUTION_MODE!"=="nonroot" (
    set "APP_ID=com.moodtools.hub.nonroot"
    set "MODE_LABEL=Non-root"
) else (
    echo [ERROR] Unknown mode: !EXECUTION_MODE!
    echo         Use root, nonroot, both, 1, 2, or 3.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Unknown launcher mode: !EXECUTION_MODE!"
    goto fail
)

if /I "!LAUNCHER_BUILD!"=="debug" (
    set "BUILD_DIR=debug"
    set "BUILD_SUFFIX=Debug"
    set "APK_SUFFIX=debug"
) else if /I "!LAUNCHER_BUILD!"=="release" (
    set "BUILD_DIR=release"
    set "BUILD_SUFFIX=Release"
    set "APK_SUFFIX=release"
    if not defined JESTER_MODULE_BUILD_MODE set "MODULE_BUILD_MODE=release"
) else (
    echo [ERROR] Unknown launcher build: !LAUNCHER_BUILD!
    echo         Use debug, release, 1, or 2.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Unknown launcher build: !LAUNCHER_BUILD!"
    goto fail
)

if /I "!EXECUTION_MODE!"=="root" (
    set "GRADLE_TASK=:app:assembleRoot!BUILD_SUFFIX!"
    set "APK_PATH=%CD%\app\build\outputs\apk\root\!BUILD_DIR!\app-root-!APK_SUFFIX!.apk"
    set "BUILD_LABEL=root!BUILD_SUFFIX!"
) else (
    set "GRADLE_TASK=:app:assembleNonroot!BUILD_SUFFIX!"
    set "APK_PATH=%CD%\app\build\outputs\apk\nonroot\!BUILD_DIR!\app-nonroot-!APK_SUFFIX!.apk"
    set "BUILD_LABEL=nonroot!BUILD_SUFFIX!"
)

if not defined LAUNCHER_ONLY set "GOOGLE_DIAG_DIR=%CD%\diagnostics\google-signin-!TARGET_MODULE!"
if not defined LAUNCHER_ONLY set "GOOGLE_DIAG_ZIP=%CD%\diagnostics\google-signin-!TARGET_MODULE!.zip"

if defined LAUNCHER_ONLY (
    echo [TARGET] Launcher only
) else (
    echo [GAME] !TARGET_LABEL!
    for %%M in (!TARGET_MODULES!) do echo        - %%~M
)
echo [MODE] !MODE_LABEL! launcher ^(!BUILD_LABEL!^)
if defined MODULE_ONLY_TEST echo [SCOPE] Module only - reuse the installed launcher
if not defined LAUNCHER_ONLY echo [MODULE] !MODULE_SETUP_MODE! test
if /I "!LAUNCHER_BUILD!"=="release" if /I "!MODULE_SETUP_MODE!"=="stage" echo [NOTE] Release staging uses ADB-only local import; it does not publish or use the live catalog.
if "!GOOGLE_SIGNIN_DIAG!"=="1" echo [DIAG] Google sign-in capture enabled
echo.

rem ---- Android SDK / ADB ----------------------------------------------------
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME if defined LOCALAPPDATA set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"

set "ADB_EXE="
if defined ADB if exist "%ADB%" set "ADB_EXE=%ADB%"
if not defined ADB_EXE if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB_EXE=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB_EXE for %%I in (adb.exe) do if not "%%~$PATH:I"=="" set "ADB_EXE=%%~$PATH:I"
if not defined ADB_EXE (
    echo [ERROR] adb.exe was not found.
    echo         Install Android SDK Platform-Tools or set ANDROID_HOME.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=adb.exe was not found."
    goto fail
)

rem build_modules.ps1 calls d8 by name, so expose the newest installed build-tools.
where d8.bat >nul 2>nul
if errorlevel 1 if defined ANDROID_HOME (
    for /f "usebackq delims=" %%I in (`powershell -NoProfile -Command "$p='%ANDROID_HOME%\build-tools'; if(Test-Path -LiteralPath $p){Get-ChildItem -LiteralPath $p -Directory ^| Sort-Object {[version]$_.Name} -Descending ^| Where-Object {Test-Path (Join-Path $_.FullName 'd8.bat')} ^| Select-Object -First 1 -ExpandProperty FullName}"`) do set "BUILD_TOOLS=%%I"
    if defined BUILD_TOOLS set "PATH=!BUILD_TOOLS!;!PATH!"
)
where d8.bat >nul 2>nul
if errorlevel 1 (
    echo [ERROR] d8.bat was not found in Android SDK build-tools.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=d8.bat was not found."
    goto fail
)

rem ---- Select Java ----------------------------------------------------------
rem Do not force JDK 17 here. Gradle 9.4.1 can run on Java 25/26.
rem Prefer an explicit JESTER_JAVA_HOME, then JAVA_HOME, then the Java on PATH.
if defined JESTER_JAVA_HOME (
    if not exist "%JESTER_JAVA_HOME%\bin\java.exe" (
        echo [ERROR] JESTER_JAVA_HOME does not contain bin\java.exe:
        echo         %JESTER_JAVA_HOME%
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=JESTER_JAVA_HOME is missing bin\java.exe."
        goto fail
    )
    if not exist "%JESTER_JAVA_HOME%\bin\javac.exe" (
        echo [ERROR] JESTER_JAVA_HOME does not contain bin\javac.exe:
        echo         %JESTER_JAVA_HOME%
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=JESTER_JAVA_HOME is missing bin\javac.exe."
        goto fail
    )
    set "JAVA_HOME=%JESTER_JAVA_HOME%"
    set "PATH=%JESTER_JAVA_HOME%\bin;%PATH%"
) else if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" if exist "%JAVA_HOME%\bin\javac.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
)

where java.exe >nul 2>nul
if errorlevel 1 (
    echo [ERROR] java.exe was not found. Set JAVA_HOME or JESTER_JAVA_HOME to a JDK.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=java.exe was not found."
    goto fail
)
where javac.exe >nul 2>nul
if errorlevel 1 (
    echo [ERROR] javac.exe was not found. A full JDK is required, not just a JRE.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=javac.exe was not found."
    goto fail
)

set "JAVA_VERSION_FILE=%TEMP%\jester-moods-java-version-%RANDOM%.txt"
java -version >"%JAVA_VERSION_FILE%" 2>&1
set "JAVA_VERSION_LINE="
set /p JAVA_VERSION_LINE=<"%JAVA_VERSION_FILE%"
del /q "%JAVA_VERSION_FILE%" >nul 2>nul
echo [JAVA] !JAVA_VERSION_LINE!

rem ---- Select Gradle --------------------------------------------------------
if defined MODULE_ONLY_TEST goto gradle_selection_complete
rem Priority: explicit override -> GRADLE_HOME -> Gradle on PATH -> wrappers.
rem This intentionally lets a locally installed Gradle 9.4.1 win over the
rem third_party\BlackBox Gradle 8.13 wrapper.
set "GRADLE_CMD="
set "GRADLE_KIND="

if defined JESTER_GRADLE (
    if exist "%JESTER_GRADLE%" (
        set "GRADLE_CMD=%JESTER_GRADLE%"
        set "GRADLE_KIND=system"
    ) else (
        echo [ERROR] JESTER_GRADLE does not exist:
        echo         %JESTER_GRADLE%
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=JESTER_GRADLE path does not exist."
        goto fail
    )
)

if not defined GRADLE_CMD if defined GRADLE_HOME if exist "%GRADLE_HOME%\bin\gradle.bat" (
    set "GRADLE_CMD=%GRADLE_HOME%\bin\gradle.bat"
    set "GRADLE_KIND=system"
)

if not defined GRADLE_CMD (
    for %%I in (gradle.bat) do if not "%%~$PATH:I"=="" (
        set "GRADLE_CMD=%%~$PATH:I"
        set "GRADLE_KIND=system"
    )
)

if not defined GRADLE_CMD if exist "%CD%\gradlew.bat" (
    set "GRADLE_CMD=%CD%\gradlew.bat"
    set "GRADLE_KIND=root-wrapper"
)

if not defined GRADLE_CMD if exist "%CD%\third_party\BlackBox\gradlew.bat" (
    set "GRADLE_CMD=%CD%\third_party\BlackBox\gradlew.bat"
    set "GRADLE_KIND=blackbox-wrapper"
)

if not defined GRADLE_CMD (
    echo [ERROR] Gradle was not found.
    echo         Put Gradle 9.4.1 on PATH, set GRADLE_HOME, or set JESTER_GRADLE
    echo         to the full path of gradle.bat.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Gradle was not found."
    goto fail
)

set "GRADLE_VERSION_FILE=%TEMP%\jester-moods-gradle-version-%RANDOM%.txt"
call "%GRADLE_CMD%" --version >"%GRADLE_VERSION_FILE%" 2>&1
if errorlevel 1 (
    type "%GRADLE_VERSION_FILE%"
    del /q "%GRADLE_VERSION_FILE%" >nul 2>nul
    echo [ERROR] Selected Gradle failed to start: %GRADLE_CMD%
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Selected Gradle failed to start."
    goto fail
)
set "GRADLE_VERSION="
for /f "tokens=2" %%V in ('findstr /b /c:"Gradle " "%GRADLE_VERSION_FILE%"') do if not defined GRADLE_VERSION set "GRADLE_VERSION=%%V"
del /q "%GRADLE_VERSION_FILE%" >nul 2>nul
if not defined GRADLE_VERSION set "GRADLE_VERSION=unknown"
echo [GRADLE] Using Gradle !GRADLE_VERSION!: !GRADLE_CMD!

:gradle_selection_complete

rem ---- Select an authorized ADB target --------------------------------------
"%ADB_EXE%" start-server >nul 2>nul
set "DEVICE_SERIAL=!ADB_SERIAL!"
set "DEVICE_STATE="
set "ADB_LAST_STATE="
set /a ADB_DETECT_TRY=0

:detect_adb_device
set /a ADB_DETECT_TRY+=1
set "DEVICE_STATE="
set "ADB_LAST_STATE="

if defined DEVICE_SERIAL (
    for /f "usebackq delims=" %%S in (`"%ADB_EXE%" -s "!DEVICE_SERIAL!" get-state 2^>nul`) do set "DEVICE_STATE=%%S"
    if /I "!DEVICE_STATE!"=="device" goto adb_device_ready
) else (
    for /f "usebackq delims=" %%L in (`"%ADB_EXE%" devices 2^>nul`) do (
        set "ADB_DEVICE_LINE=%%L"
        rem ADB separates the serial and state with a tab. Parse the state from
        rem the end so mDNS serials containing spaces, such as "name (2)._adb...",
        rem remain intact instead of shifting the state out of column two.
        if /I "!ADB_DEVICE_LINE:~-7!"=="	device" if not defined DEVICE_SERIAL (
            set "DEVICE_SERIAL=!ADB_DEVICE_LINE:~0,-7!"
            set "DEVICE_STATE=device"
        )
        if /I "!ADB_DEVICE_LINE:~-13!"=="	unauthorized" set "ADB_LAST_STATE=unauthorized"
        if /I "!ADB_DEVICE_LINE:~-8!"=="	offline" if not defined ADB_LAST_STATE set "ADB_LAST_STATE=offline"
    )
    if defined DEVICE_SERIAL goto adb_device_ready
)

if !ADB_DETECT_TRY! LSS 8 (
    >nul 2>nul ping 127.0.0.1 -n 2
    goto detect_adb_device
)

echo.
echo [ERROR] No authorized ADB device is available after !ADB_DETECT_TRY! checks.
if defined DEVICE_SERIAL (
    echo         ADB_SERIAL=!DEVICE_SERIAL! state=!DEVICE_STATE!
) else if /I "!ADB_LAST_STATE!"=="unauthorized" (
    echo         Device state: unauthorized
    echo         Unlock the phone and accept the USB debugging RSA prompt.
) else if /I "!ADB_LAST_STATE!"=="offline" (
    echo         Device state: offline
    echo         Reconnect USB, then run: adb kill-server ^& adb start-server
) else (
    echo         ADB did not report an attached device.
    echo         Check the USB cable/mode and USB debugging.
)
echo.
echo [ADB] Current device list:
"%ADB_EXE%" devices -l
set "EXIT_CODE=1"
set "FAIL_CONTEXT=No authorized ADB device is available."
goto fail

:adb_device_ready
echo [ADB] Using !DEVICE_SERIAL!

rem ---- Build or validate selected standalone module output ---------------------
if not defined LAUNCHER_ONLY if /I "!MODULE_SETUP_MODE!"=="stage" (
    echo.
    set "MODULE_BUILD_TARGETS="
    if defined MODULE_INPUT_FILE for /f "usebackq tokens=1,2,3 delims=|" %%M in ("!MODULE_INPUT_FILE!") do if "%%O"=="0" (
        if defined MODULE_BUILD_TARGETS (
            set "MODULE_BUILD_TARGETS=!MODULE_BUILD_TARGETS!,%%M"
        ) else (
            set "MODULE_BUILD_TARGETS=%%M"
        )
    )
    if /I "!SKIP_MODULE_BUILD!"=="1" (
        if defined MODULE_ONLY_TEST (
            echo [1/2] Using existing module-output files for !TARGET_LABEL!...
        ) else (
            echo [1/4] Using existing module-output files for !TARGET_LABEL!...
        )
    ) else (
        if defined MODULE_INPUT_FILE (
            if defined MODULE_BUILD_TARGETS (
                if defined MODULE_ONLY_TEST (echo [1/2] Building !TARGET_LABEL! module...) else (echo [1/4] Building !TARGET_LABEL! module...)
                powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%CD%\scripts\build_modules.ps1" -Mode !MODULE_BUILD_MODE! -Module "!MODULE_BUILD_TARGETS!" -SkipApp
            ) else (
                if defined MODULE_ONLY_TEST (echo [1/2] Using dropped built module files for !TARGET_LABEL!...) else (echo [1/4] Using dropped built module files for !TARGET_LABEL!...)
            )
        ) else (
            if defined MODULE_ONLY_TEST (echo [1/2] Building !TARGET_LABEL! module...) else (echo [1/4] Building !TARGET_LABEL! module...)
            powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%CD%\scripts\build_modules.ps1" -Mode !MODULE_BUILD_MODE! -Module "!TARGET_MODULE!" -SkipApp
        )
        if errorlevel 1 (
            set "EXIT_CODE=!ERRORLEVEL!"
            set "FAIL_CONTEXT=Module build failed for !TARGET_LABEL!."
            goto fail
        )
    )

    set "MODULE_STAGE_FILE=%TEMP%\jester-moods-module-stage-%RANDOM%.txt"
    if defined MODULE_INPUT_FILE (
        powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%CD%\scripts\resolve-test-module-drops.ps1" -OutputPath "!MODULE_STAGE_FILE!" -BuiltOutputRoot "%CD%\module-output"
        if errorlevel 1 (
            set "EXIT_CODE=!ERRORLEVEL!"
            set "FAIL_CONTEXT=Could not resolve selected module staging folders."
            goto fail
        )
    ) else (
        >"!MODULE_STAGE_FILE!" echo !TARGET_MODULE!^|%CD%\module-output\!TARGET_MODULE!
    )
    for /f "usebackq tokens=1,* delims=|" %%M in ("!MODULE_STAGE_FILE!") do for %%F in (config.json classes.dex libmenu_native.so) do (
        if not exist "%%N\%%F" (
            echo [ERROR] Module output missing for %%M: %%F
            echo         Drop a built module-output folder, build it first, or rerun this helper without JESTER_SKIP_MODULE_BUILD=1.
            set "EXIT_CODE=1"
            set "FAIL_CONTEXT=Module output missing for %%M: %%F"
            goto fail
        )
    )
)

rem ---- Build selected launcher flavor -----------------------------------------
if defined MODULE_ONLY_TEST goto validate_existing_launcher
echo.
if defined LAUNCHER_ONLY (
    echo [1/2] Building Jester Mods !BUILD_LABEL!...
) else (
    echo [2/4] Building Jester Mods !BUILD_LABEL!...
)
if not exist "%CD%\settings.gradle" if not exist "%CD%\settings.gradle.kts" (
    echo [ERROR] Root settings.gradle/settings.gradle.kts is missing.
    echo         Restore the project-level Gradle files in this workspace first.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Root Gradle settings file is missing."
    goto fail
)
if not exist "%CD%\build.gradle" if not exist "%CD%\build.gradle.kts" (
    echo [ERROR] Root build.gradle/build.gradle.kts is missing.
    echo         Restore the project-level Gradle files in this workspace first.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Root Gradle build file is missing."
    goto fail
)

rem ndk-build cannot reliably consume APP_BUILD_SCRIPT paths containing spaces.
rem Build through a temporary SUBST drive when necessary, then remove only the
rem mapping created by this helper as soon as Gradle exits.
set "GRADLE_PROJECT_DIR=%CD%"
set "GRADLE_SUBST_DRIVE="
if not "%CD%"=="%CD: =%" (
    for %%D in (S T U V W X Y Z) do if not defined GRADLE_SUBST_DRIVE if not exist "%%D:\" (
        "%SystemRoot%\System32\subst.exe" %%D: "%CD%" >nul 2>nul
        if not errorlevel 1 (
            set "GRADLE_SUBST_DRIVE=%%D:"
            set "GRADLE_PROJECT_DIR=%%D:\."
        )
    )
    if not defined GRADLE_SUBST_DRIVE (
        echo [ERROR] Could not create a temporary drive for the space-containing workspace path.
        echo         ndk-build requires a project path without spaces.
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=Could not create temporary SUBST drive for Gradle."
        goto fail
    )
    echo [BUILD] Using temporary project path !GRADLE_PROJECT_DIR!
)

set "GRADLE_PROJECT_CACHE=%TEMP%\jester-moods-gradle-project-cache"
if defined LOCALAPPDATA set "GRADLE_PROJECT_CACHE=%LOCALAPPDATA%\JesterMoods\gradle-project-cache"
if not exist "!GRADLE_PROJECT_CACHE!" mkdir "!GRADLE_PROJECT_CACHE!" >nul 2>nul
if not exist "!GRADLE_PROJECT_CACHE!" (
    echo [ERROR] Could not create Gradle project cache:
    echo         !GRADLE_PROJECT_CACHE!
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Could not create Gradle project cache."
    goto fail
)

call "!GRADLE_CMD!" -p "!GRADLE_PROJECT_DIR!" !GRADLE_TASK! --no-daemon --project-cache-dir "!GRADLE_PROJECT_CACHE!"
set "GRADLE_EXIT_CODE=!ERRORLEVEL!"
if defined GRADLE_SUBST_DRIVE (
    "%SystemRoot%\System32\subst.exe" !GRADLE_SUBST_DRIVE! /d >nul 2>nul
)
if not "!GRADLE_EXIT_CODE!"=="0" (
    set "EXIT_CODE=!GRADLE_EXIT_CODE!"
    set "FAIL_CONTEXT=Gradle build failed for !BUILD_LABEL! using task !GRADLE_TASK!."
    goto fail
)
if not exist "%APK_PATH%" (
    echo [ERROR] APK not found: %APK_PATH%
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Expected launcher APK was not created."
    goto fail
)
rem ---- Install launcher ------------------------------------------------------
echo.
if defined LAUNCHER_ONLY (
    echo [2/2] Installing Jester Mods !BUILD_LABEL!...
) else (
    echo [3/4] Installing Jester Mods !BUILD_LABEL!...
)
echo [INSTALL] Jester Mods launcher...
"%ADB_EXE%" -s "!DEVICE_SERIAL!" install -r -d "%APK_PATH%"
if errorlevel 1 (
    set "EXIT_CODE=%ERRORLEVEL%"
    set "FAIL_CONTEXT=ADB install failed for !BUILD_LABEL!."
    goto fail
)

goto launcher_install_complete

:validate_existing_launcher
echo.
echo [LAUNCHER] Reusing the installed !MODE_LABEL! launcher...
set "INSTALLED_LAUNCHER_APK="
set "INSTALLED_LAUNCHER_FILE=%TEMP%\jester-moods-installed-launcher-%RANDOM%.txt"
"%ADB_EXE%" -s "!DEVICE_SERIAL!" shell pm path %APP_ID% >"!INSTALLED_LAUNCHER_FILE!" 2>nul
if exist "!INSTALLED_LAUNCHER_FILE!" (
    set /p INSTALLED_LAUNCHER_APK=<"!INSTALLED_LAUNCHER_FILE!"
    del /q "!INSTALLED_LAUNCHER_FILE!" >nul 2>nul
)
if not defined INSTALLED_LAUNCHER_APK (
    echo [ERROR] The !MODE_LABEL! launcher is not installed: %APP_ID%
    echo         Install it once, or choose Launcher + local module^(s^) instead.
    set "EXIT_CODE=1"
    set "FAIL_CONTEXT=Installed !MODE_LABEL! launcher was not found."
    goto fail
)
if /I "!LAUNCHER_BUILD!"=="debug" (
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "run-as %APP_ID% id" >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] The installed %APP_ID% cannot use debug run-as staging.
        echo         Choose Release if it is a release launcher, or reinstall the Debug launcher.
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=Installed launcher does not match the selected Debug build."
        goto fail
    )
)
echo [LAUNCHER] Found !INSTALLED_LAUNCHER_APK!

:launcher_install_complete

rem ---- Stage or prepare module into the debuggable launcher's private files ---
if defined LAUNCHER_ONLY goto module_setup_complete
echo.
if defined MODULE_ONLY_TEST (echo [2/2] Staging !TARGET_LABEL! module...) else (echo [4/4] Staging !TARGET_LABEL! module...)
if /I "!LAUNCHER_BUILD!"=="release" (
    set "ADB_HAS_SU=0"
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "su -c id" >nul 2>nul
    if not errorlevel 1 set "ADB_HAS_SU=1"

    if "!ADB_HAS_SU!"=="1" (
        echo [STAGE] Root access detected; writing release test modules directly into launcher storage.
        set "APP_UID="
        set "APP_OWNER_FILE=%TEMP%\jester-moods-app-owner-%RANDOM%.txt"
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "pm list packages -U --user 0 %APP_ID%" >"!APP_OWNER_FILE!" 2>nul
        if exist "!APP_OWNER_FILE!" (
            for /f "usebackq tokens=3 delims=:" %%U in ("!APP_OWNER_FILE!") do if not defined APP_UID set "APP_UID=%%U"
            del /q "!APP_OWNER_FILE!" >nul 2>nul
        )
        if not defined APP_UID (
            set "EXIT_CODE=1"
            set "FAIL_CONTEXT=Could not read private storage owner for %APP_ID%."
            goto fail
        )
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "rm -rf %REMOTE_TMP% && mkdir -p %REMOTE_TMP% && chmod 0777 %REMOTE_TMP%"
        if errorlevel 1 (
            set "EXIT_CODE=!ERRORLEVEL!"
            set "FAIL_CONTEXT=Could not prepare remote temporary staging directory."
            goto fail
        )
        rem Create every launcher-owned directory as the launcher UID. Creating the parent as
        rem root can leave files/ root-owned after an experiment cleanup, which prevents the
        rem release launcher from caching its catalog, changelog, and launcher updates.
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "su !APP_UID! -c 'mkdir -p /data/data/%APP_ID%/files/menus && test -w /data/data/%APP_ID%/files/menus'"
        if errorlevel 1 (
            set "EXIT_CODE=!ERRORLEVEL!"
            set "FAIL_CONTEXT=Could not prepare private launcher module storage."
            goto fail
        )
        for /f "usebackq tokens=1,* delims=|" %%M in ("!MODULE_STAGE_FILE!") do (
            "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "mkdir -p %REMOTE_TMP%/%%~M"
            if errorlevel 1 (
                set "EXIT_CODE=!ERRORLEVEL!"
                set "FAIL_CONTEXT=Could not create remote staging directory for %%~M."
                goto fail
            )
            for %%F in (config.json classes.dex libmenu_native.so) do (
                "%ADB_EXE%" -s "!DEVICE_SERIAL!" push "%%N\%%F" "%REMOTE_TMP%/%%~M/%%F" >nul
                if errorlevel 1 (
                    set "EXIT_CODE=!ERRORLEVEL!"
                    set "FAIL_CONTEXT=Could not push %%F for %%~M."
                    goto fail
                )
                "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "chmod 0644 %REMOTE_TMP%/%%~M/%%F" >nul 2>nul
            )
            "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "su !APP_UID! -c 'rm -rf /data/data/%APP_ID%/files/menus/%%~M.local-test-next && mkdir -p /data/data/%APP_ID%/files/menus/%%~M.local-test-next'"
            if errorlevel 1 (
                set "EXIT_CODE=!ERRORLEVEL!"
                set "FAIL_CONTEXT=Could not create private local-test staging for %%~M."
                goto fail
            )
            for %%F in (config.json classes.dex libmenu_native.so) do (
                "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "su !APP_UID! -c 'cp %REMOTE_TMP%/%%~M/%%F /data/data/%APP_ID%/files/menus/%%~M.local-test-next/%%F && chmod 0600 /data/data/%APP_ID%/files/menus/%%~M.local-test-next/%%F'"
                if errorlevel 1 (
                    set "EXIT_CODE=!ERRORLEVEL!"
                    set "FAIL_CONTEXT=Could not copy %%F into private local-test staging for %%~M."
                    goto fail
                )
            )
            "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "su !APP_UID! -c 'echo {} > /data/data/%APP_ID%/files/menus/%%~M.local-test-next/local-test.json && chmod 0600 /data/data/%APP_ID%/files/menus/%%~M.local-test-next/local-test.json && rm -rf /data/data/%APP_ID%/files/menus/%%~M && mv /data/data/%APP_ID%/files/menus/%%~M.local-test-next /data/data/%APP_ID%/files/menus/%%~M'"
            if errorlevel 1 (
                set "EXIT_CODE=!ERRORLEVEL!"
                set "FAIL_CONTEXT=Could not commit private local-test module for %%~M."
                goto fail
            )
            "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "su !APP_UID! -c 'test -f /data/data/%APP_ID%/files/menus/%%~M/local-test.json && test -s /data/data/%APP_ID%/files/menus/%%~M/config.json && test -s /data/data/%APP_ID%/files/menus/%%~M/classes.dex && test -s /data/data/%APP_ID%/files/menus/%%~M/libmenu_native.so'"
            if errorlevel 1 (
                set "EXIT_CODE=!ERRORLEVEL!"
                set "FAIL_CONTEXT=Private local-test verification failed for %%~M."
                goto fail
            )
            echo [STAGE] %%~M marked as local TEST in launcher storage.
        )
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "rm -rf %REMOTE_TMP%" >nul 2>nul
        set "LOCAL_TEST_DIRECT=1"
        echo [STAGE] Release module files are staged locally through private ADB/root storage.
    ) else (
        set "LOCAL_TEST_ROOT=/sdcard/Android/data/%APP_ID%/files/jester-local-modules"
        set "LOCAL_TEST_TOKEN_FILE=%TEMP%\jester-moods-local-stage-token-%RANDOM%.txt"
        set "LOCAL_TEST_TOKEN=JesterLocalStage!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!!RANDOM!"
        if not defined LOCAL_TEST_TOKEN (
            set "EXIT_CODE=1"
            set "FAIL_CONTEXT=Could not create local release staging token."
            goto fail
        )
        >"!LOCAL_TEST_TOKEN_FILE!" echo !LOCAL_TEST_TOKEN!

        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "rm -rf !LOCAL_TEST_ROOT! && mkdir -p !LOCAL_TEST_ROOT!"
        if errorlevel 1 (
            set "EXIT_CODE=!ERRORLEVEL!"
            set "FAIL_CONTEXT=Could not prepare app-external local release staging directory."
            goto fail
        )

        set "LOCAL_TEST_PACKAGES="
        for /f "usebackq tokens=1,* delims=|" %%M in ("!MODULE_STAGE_FILE!") do (
            if defined LOCAL_TEST_PACKAGES (
                set "LOCAL_TEST_PACKAGES=!LOCAL_TEST_PACKAGES!,%%~M"
            ) else (
                set "LOCAL_TEST_PACKAGES=%%~M"
            )
            "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "mkdir -p !LOCAL_TEST_ROOT!/%%~M"
            if errorlevel 1 (
                set "EXIT_CODE=!ERRORLEVEL!"
                set "FAIL_CONTEXT=Could not create app-external local staging directory for %%~M."
                goto fail
            )
            for %%F in (config.json classes.dex libmenu_native.so) do (
                "%ADB_EXE%" -s "!DEVICE_SERIAL!" push "%%N\%%F" "!LOCAL_TEST_ROOT!/%%~M/%%F" >nul
                if errorlevel 1 (
                    set "EXIT_CODE=!ERRORLEVEL!"
                    set "FAIL_CONTEXT=Could not push %%F for %%M into app-external local staging."
                    goto fail
                )
                "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "chmod 0644 !LOCAL_TEST_ROOT!/%%~M/%%F" >nul 2>nul
            )
        )
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" push "!LOCAL_TEST_TOKEN_FILE!" "!LOCAL_TEST_ROOT!/stage-token.txt" >nul
        if errorlevel 1 (
            set "EXIT_CODE=!ERRORLEVEL!"
            set "FAIL_CONTEXT=Could not push local release staging token."
            goto fail
        )
        del /q "!LOCAL_TEST_TOKEN_FILE!" >nul 2>nul
        set "LOCAL_TEST_URI=moodtools-local-test://stage?token=!LOCAL_TEST_TOKEN!&packages=!LOCAL_TEST_PACKAGES!"
        echo [STAGE] Release module files are staged locally through app-external ADB import.
    )
) else (
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "rm -rf %REMOTE_TMP% && mkdir -p %REMOTE_TMP% && chmod 0777 %REMOTE_TMP%"
    if errorlevel 1 (
        set "EXIT_CODE=%ERRORLEVEL%"
        set "FAIL_CONTEXT=Could not prepare remote temporary staging directory."
        goto fail
    )

    for /f "usebackq tokens=1,* delims=|" %%M in ("!MODULE_STAGE_FILE!") do (
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "mkdir -p %REMOTE_TMP%/%%~M"
        if errorlevel 1 (
            set "EXIT_CODE=!ERRORLEVEL!"
            set "FAIL_CONTEXT=Could not create remote staging directory for %%~M."
            goto fail
        )
        for %%F in (config.json classes.dex libmenu_native.so) do (
            "%ADB_EXE%" -s "!DEVICE_SERIAL!" push "%%N\%%F" "%REMOTE_TMP%/%%~M/%%F" >nul
            if errorlevel 1 (
                set "EXIT_CODE=!ERRORLEVEL!"
                set "FAIL_CONTEXT=Could not push %%F for %%~M."
                goto fail
            )
            "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "chmod 0644 %REMOTE_TMP%/%%~M/%%F"
            if errorlevel 1 (
                set "EXIT_CODE=!ERRORLEVEL!"
                set "FAIL_CONTEXT=Could not chmod %%F for %%~M."
                goto fail
            )
        )

        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "run-as %APP_ID% mkdir -p files/menus/%%~M"
        if errorlevel 1 (
            echo [ERROR] run-as failed for %APP_ID%.
            echo         Confirm !BUILD_LABEL! was installed and is debuggable.
            set "EXIT_CODE=1"
            set "FAIL_CONTEXT=run-as failed for %APP_ID%."
            goto fail
        )
        for %%F in (config.json classes.dex libmenu_native.so) do (
            "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "run-as %APP_ID% cp %REMOTE_TMP%/%%~M/%%F files/menus/%%~M/%%F"
            if errorlevel 1 (
                set "EXIT_CODE=!ERRORLEVEL!"
                set "FAIL_CONTEXT=Could not copy %%F into launcher storage for %%~M."
                goto fail
            )
        )
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "run-as %APP_ID% sh -c 'echo {} > files/menus/%%~M/local-test.json'"
        if errorlevel 1 (
            set "EXIT_CODE=!ERRORLEVEL!"
            set "FAIL_CONTEXT=Could not mark %%~M as a local test module."
            goto fail
        )
    )
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "rm -rf %REMOTE_TMP%" >nul 2>nul

    rem Sanity-check staged files before opening the launcher.
    for %%M in (!TARGET_MODULES!) do (
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell "run-as %APP_ID% ls -l files/menus/%%~M/config.json files/menus/%%~M/classes.dex files/menus/%%~M/libmenu_native.so files/menus/%%~M/local-test.json"
        if errorlevel 1 (
            set "EXIT_CODE=!ERRORLEVEL!"
            set "FAIL_CONTEXT=Staged file sanity check failed for %%~M."
            goto fail
        )
    )
)

:module_setup_complete
if defined MODULE_INPUT_FILE if exist "!MODULE_INPUT_FILE!" del /q "!MODULE_INPUT_FILE!" >nul 2>nul
if defined MODULE_STAGE_FILE if exist "!MODULE_STAGE_FILE!" del /q "!MODULE_STAGE_FILE!" >nul 2>nul
set "JESTER_MODULE_DROP_INPUT="
echo.
if "!GOOGLE_SIGNIN_DIAG!"=="1" (
    if exist "!GOOGLE_DIAG_DIR!" rmdir /s /q "!GOOGLE_DIAG_DIR!"
    if exist "!GOOGLE_DIAG_ZIP!" del /q "!GOOGLE_DIAG_ZIP!"
    mkdir "!GOOGLE_DIAG_DIR!" >nul 2>nul

    echo [DIAG] Preparing Google sign-in capture...
    >"!GOOGLE_DIAG_DIR!\capture_info.txt" echo Jester Mods Google sign-in diagnostic
    >>"!GOOGLE_DIAG_DIR!\capture_info.txt" echo Target: !TARGET_LABEL! ^(!TARGET_MODULE!^)
    >>"!GOOGLE_DIAG_DIR!\capture_info.txt" echo Mode: !MODE_LABEL!
    >>"!GOOGLE_DIAG_DIR!\capture_info.txt" echo Device: !DEVICE_SERIAL!
    >>"!GOOGLE_DIAG_DIR!\capture_info.txt" echo Started: !DATE! !TIME!

    >"!GOOGLE_DIAG_DIR!\package_paths.txt" echo ===== !TARGET_LABEL! =====
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell pm path !TARGET_MODULE! >>"!GOOGLE_DIAG_DIR!\package_paths.txt" 2>&1
    >>"!GOOGLE_DIAG_DIR!\package_paths.txt" echo.
    >>"!GOOGLE_DIAG_DIR!\package_paths.txt" echo ===== Google Play services =====
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell pm path com.google.android.gms >>"!GOOGLE_DIAG_DIR!\package_paths.txt" 2>&1
    >>"!GOOGLE_DIAG_DIR!\package_paths.txt" echo.
    >>"!GOOGLE_DIAG_DIR!\package_paths.txt" echo ===== Google Play Store =====
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell pm path com.android.vending >>"!GOOGLE_DIAG_DIR!\package_paths.txt" 2>&1
    >>"!GOOGLE_DIAG_DIR!\package_paths.txt" echo.
    >>"!GOOGLE_DIAG_DIR!\package_paths.txt" echo ===== Google Services Framework =====
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell pm path com.google.android.gsf >>"!GOOGLE_DIAG_DIR!\package_paths.txt" 2>&1

    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys package !TARGET_MODULE! >"!GOOGLE_DIAG_DIR!\target_package.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys package com.google.android.gms >"!GOOGLE_DIAG_DIR!\gms_package.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys package com.android.vending >"!GOOGLE_DIAG_DIR!\play_store_package.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys package com.google.android.gsf >"!GOOGLE_DIAG_DIR!\gsf_package.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell getprop >"!GOOGLE_DIAG_DIR!\device_properties.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" logcat -b all -c
)

if defined LAUNCHER_ONLY (
    echo [DONE] Jester Mods !MODE_LABEL! is installed.
    echo        Opening the Launcher now. Use + to browse available modules.
) else if defined MODULE_ONLY_TEST (
    echo [DONE] !TARGET_LABEL! module is built and staged in the installed Jester Mods !MODE_LABEL! launcher.
    echo        The launcher APK was not rebuilt or reinstalled.
) else (
    echo [DONE] Jester Mods !MODE_LABEL! and !TARGET_LABEL! module are installed and staged.
    if /I "!LAUNCHER_BUILD!"=="release" (
        if defined LOCAL_TEST_DIRECT (
            echo        Opening Jester Mods now. Local TEST modules are already in launcher storage.
        ) else (
            echo        Opening Jester Mods now. Local staged modules will be imported before live catalog use.
        )
    ) else (
        echo        Opening Jester Mods now. Tap a staged module, then use Play.
    )
)
"%ADB_EXE%" -s "!DEVICE_SERIAL!" shell am force-stop %APP_ID% >nul 2>nul
if /I "!LAUNCHER_BUILD!"=="debug" (
    if defined LAUNCHER_ONLY (
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell am start -n %APP_ID%/com.moodtools.hub.LauncherActivity --ez moodtools.test_bypass_unlock true --ez moodtools.test_module_updates true
    ) else (
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell am start -n %APP_ID%/com.moodtools.hub.LauncherActivity
    )
) else (
    if defined LOCAL_TEST_URI (
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell am start -n %APP_ID%/com.moodtools.hub.LauncherActivity -a android.intent.action.VIEW -d "!LOCAL_TEST_URI!"
    ) else (
        "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell am start -n %APP_ID%/com.moodtools.hub.LauncherActivity
    )
)

if "!GOOGLE_SIGNIN_DIAG!"=="1" (
    echo.
    echo ============================================================
    echo   GOOGLE SIGN-IN DIAGNOSTIC IS RECORDING
    echo ============================================================
    echo.
    echo On the phone:
    echo   1. Open !TARGET_LABEL! through Jester Mods.
    echo   2. Trigger the Google/Play Games linking or sign-in action.
    echo   3. Wait until the game shows Linking failed / Verification error.
    echo   4. Return to this window and press any key.
    echo.
    pause >nul

    echo [DIAG] Capturing logs and runtime state...
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" logcat -b all -d -v threadtime >"!GOOGLE_DIAG_DIR!\full_logcat.txt" 2>&1
    findstr /I /C:"GoogleSignInCompat" /C:"GMS bind request" /C:"FLAG_SYSTEM cluster" /C:"com.google.android.gms" /C:"com.google.android.play.games" /C:"GoogleApiManager" /C:"GmsClient" /C:"GmsClientSupervisor" /C:"IGmsServiceBroker" /C:"GetServiceRequest" /C:"GamesSignIn" /C:"GamesNativeSDK" /C:"SignInHubActivity" /C:"GoogleSignIn" /C:"ApiException" /C:"DEVELOPER_ERROR" /C:"SIGN_IN_FAILED" /C:"RESOLUTION_REQUIRED" /C:"PendingIntent" /C:"IntentSender" /C:"AccountManager" /C:"Authenticator" /C:"!TARGET_MODULE!" /C:"NonRootBlackBox" /C:"BlackBox" "!GOOGLE_DIAG_DIR!\full_logcat.txt" >"!GOOGLE_DIAG_DIR!\google_signin_focus.txt" 2>nul

    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell ps -A >"!GOOGLE_DIAG_DIR!\processes.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys activity activities >"!GOOGLE_DIAG_DIR!\activity_activities.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys activity processes >"!GOOGLE_DIAG_DIR!\activity_processes.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys activity services >"!GOOGLE_DIAG_DIR!\activity_services.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys activity services com.google.android.gms >"!GOOGLE_DIAG_DIR!\gms_services.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys account >"!GOOGLE_DIAG_DIR!\accounts.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys window windows >"!GOOGLE_DIAG_DIR!\windows.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys activity top >"!GOOGLE_DIAG_DIR!\activity_top.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys package com.google.android.gms >"!GOOGLE_DIAG_DIR!\gms_package_after.txt" 2>&1
    "%ADB_EXE%" -s "!DEVICE_SERIAL!" shell dumpsys package !TARGET_MODULE! >"!GOOGLE_DIAG_DIR!\target_package_after.txt" 2>&1
    >>"!GOOGLE_DIAG_DIR!\capture_info.txt" echo Captured: !DATE! !TIME!

    echo [DIAG] Building ZIP...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '!GOOGLE_DIAG_DIR!\*' -DestinationPath '!GOOGLE_DIAG_ZIP!' -Force"
    if errorlevel 1 (
        echo [ERROR] Could not create diagnostic ZIP.
        echo         Raw files are still in: !GOOGLE_DIAG_DIR!
        set "EXIT_CODE=1"
        set "FAIL_CONTEXT=Could not create Google sign-in diagnostic ZIP."
        goto fail
    )
    echo.
    echo [DIAG] Diagnostic package ready:
    echo        !GOOGLE_DIAG_ZIP!
    echo        Upload that ZIP here for analysis.
)

echo.
echo Tip: run without arguments for interactive target + mode selection.
echo      Launcher only Root debug:       test_helper.cmd launcher root debug
echo      Launcher only Non-root release: test_helper.cmd launcher nonroot release
echo      Launcher only Both release:     test_helper.cmd launcher both release
echo      Dropped module Root release:    test_helper.cmd "module-output\com.biglime.cookingmadness" root "" stage release
echo      Multiple dropped modules:       choose Manual local module folder^(s^), then drag them together
echo      Cooking Madness Root stage:     test_helper.cmd cooking root "" stage debug
echo      Cooking Root release stage:     test_helper.cmd cooking root "" stage release
echo      Soul Knight Non-root stage:     test_helper.cmd soul-knight nonroot "" stage release
echo      Cooking Google diagnostic:      test_helper.cmd cooking nonroot google-diag stage debug
echo      Legacy one-argument mode: test_helper.cmd nonroot
echo      Optional: set JESTER_MODULE_DIR to one or more quoted local module folders.
echo      Optional: set JESTER_SKIP_MODULE_BUILD=1 to use existing built output for staging.
echo      Optional: set JESTER_MODULE_ONLY_TEST=1 to reuse the installed launcher.
echo      Set ADB_SERIAL to target a specific device.
exit /b 0
