[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot

function Write-Header([string]$Subtitle = '') {
    Clear-Host
    Write-Host '============================================================' -ForegroundColor DarkMagenta
    Write-Host ' JESTER MODS - STANDALONE MENU MANAGER' -ForegroundColor Magenta
    if ($Subtitle) { Write-Host " $Subtitle" -ForegroundColor Gray }
    Write-Host '============================================================' -ForegroundColor DarkMagenta
    Write-Host ''
}

function Wait-ForUser {
    Write-Host ''
    if (-not [Console]::IsInputRedirected) { [void](Read-Host 'Press Enter to return') }
}

function Read-Choice([string]$Prompt, [string[]]$Allowed) {
    while ($true) {
        $answer = Read-Host $Prompt
        if ($null -eq $answer) { exit 0 }
        $answer = $answer.Trim().ToLowerInvariant()
        if ($Allowed -contains $answer) { return $answer }
        Write-Host "Choose one of: $($Allowed -join ', ')." -ForegroundColor Yellow
    }
}

function Read-YesNo([string]$Prompt, [bool]$Default = $false) {
    $suffix = if ($Default) { '[Y/n]' } else { '[y/N]' }
    while ($true) {
        $answer = (Read-Host "$Prompt $suffix").Trim().ToLowerInvariant()
        if (-not $answer) { return $Default }
        if ($answer -in @('y', 'yes')) { return $true }
        if ($answer -in @('n', 'no')) { return $false }
        Write-Host 'Enter Y or N.' -ForegroundColor Yellow
    }
}

function Read-Required([string]$Prompt, [string]$Default = '') {
    while ($true) {
        $suffix = if ($Default) { " (default $Default)" } else { '' }
        $value = (Read-Host "$Prompt$suffix").Trim()
        if ($value) { return $value }
        if ($Default) { return $Default }
        Write-Host 'This value is required.' -ForegroundColor Yellow
    }
}

function Read-PositiveLong([string]$Prompt, [long]$Default = 0) {
    while ($true) {
        $value = Read-Required $Prompt $(if ($Default -gt 0) { [string]$Default } else { '' })
        $number = 0L
        if ([long]::TryParse($value, [ref]$number) -and $number -gt 0) { return $number }
        Write-Host 'Enter a whole number greater than zero.' -ForegroundColor Yellow
    }
}

function Convert-VersionToBuild([string]$Version) {
    $normalized = $Version.Trim()
    if ($normalized -notmatch '^\d+\.\d+\.\d+$') {
        throw "Version '$Version' must use numeric major.minor.patch format."
    }
    $buildText = $normalized.Replace('.', '')
    $build = 0L
    if (-not [long]::TryParse($buildText, [ref]$build) -or $build -le 0) {
        throw "Version '$Version' cannot be represented as a positive build number."
    }
    return $build
}

function Invoke-Checked([string]$Label, [scriptblock]$Action) {
    Write-Host ''
    Write-Host "Running: $Label" -ForegroundColor Magenta
    Write-Host ''
    $global:LASTEXITCODE = 0
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE." }
}

function Get-ModuleNames {
    return @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'modules') -Directory |
        Where-Object {
            (Test-Path -LiteralPath (Join-Path $_.FullName 'config.json')) -and
            (Test-Path -LiteralPath (Join-Path $_.FullName 'features.json'))
        } | Sort-Object Name | Select-Object -ExpandProperty Name)
}

function Select-Module {
    $modules = @(Get-ModuleNames)
    if ($modules.Count -eq 0) { throw 'No buildable modules were found.' }
    Write-Host ''
    for ($index = 0; $index -lt $modules.Count; $index++) {
        Write-Host "  $($index + 1). $($modules[$index])"
    }
    Write-Host '  B. Back'
    while ($true) {
        $choice = (Read-Host 'Module').Trim()
        if ($choice -match '^[bB]$') { return $null }
        $selected = 0
        if ([int]::TryParse($choice, [ref]$selected) -and $selected -ge 1 -and $selected -le $modules.Count) {
            return $modules[$selected - 1]
        }
        Write-Host 'Choose a listed module number.' -ForegroundColor Yellow
    }
}

function Invoke-ModuleBuild([string]$Mode, [string]$Module = '') {
    $arguments = @{ Mode = $Mode; SkipApp = $true }
    if ($Module) { $arguments.Module = $Module }
    Invoke-Checked "Build $Mode module assets" { & (Join-Path $PSScriptRoot 'build_modules.ps1') @arguments }
}

function Show-ModuleMenu {
    while ($true) {
        Write-Header 'Build module packages'
        Write-Host '  1. Build all modules (debug)'
        Write-Host '  2. Build all modules (release)'
        Write-Host '  3. Build one module (debug)'
        Write-Host '  4. Build one module (release)'
        Write-Host '  B. Back'
        switch (Read-Choice 'Select an action' @('1','2','3','4','b')) {
            '1' { Invoke-ModuleBuild 'debug'; Wait-ForUser }
            '2' { Invoke-ModuleBuild 'release'; Wait-ForUser }
            '3' { $module = Select-Module; if ($module) { Invoke-ModuleBuild 'debug' $module; Wait-ForUser } }
            '4' { $module = Select-Module; if ($module) { Invoke-ModuleBuild 'release' $module; Wait-ForUser } }
            'b' { return }
        }
    }
}

function Get-LauncherTasks([string]$Kind, [string]$Flavor) {
    $suffix = switch ($Kind) { 'debug' { 'Debug' }; 'hardened' { 'HardenedTest' }; 'release' { 'Release' } }
    $flavors = if ($Flavor -eq 'both') { @('Root', 'Nonroot') } else { @((Get-Culture).TextInfo.ToTitleCase($Flavor)) }
    return @($flavors | ForEach-Object { ":app:assemble${_}${suffix}" })
}

function Invoke-LauncherBuild([string]$Kind, [string]$Flavor, [long]$VersionCode = 0, [string]$VersionName = '') {
    $arguments = [System.Collections.Generic.List[string]]::new()
    Get-LauncherTasks $Kind $Flavor | ForEach-Object { $arguments.Add($_) }
    $arguments.Add('--no-daemon')
    if ($Kind -in @('release', 'hardened')) {
        if (-not $VersionName) { $VersionName = Read-Required 'Version name' '1.1.5' }
        $derivedVersionCode = Convert-VersionToBuild $VersionName
        if ($VersionCode -gt 0 -and $VersionCode -ne $derivedVersionCode) {
            throw "Version $VersionName requires build $derivedVersionCode, not $VersionCode."
        }
        $VersionCode = $derivedVersionCode
        Write-Host "Build derived from version: $VersionCode" -ForegroundColor Gray
        $arguments.Add("-PlauncherVersionCode=$VersionCode")
        $arguments.Add("-PlauncherVersionName=$VersionName")
    }
    Invoke-Checked "Build $Kind launcher ($Flavor)" { & (Join-Path $ProjectRoot 'gradlew.bat') @arguments }
}

function Show-LauncherMenu {
    while ($true) {
        Write-Header 'Build launcher APKs'
        Write-Host '  1. Debug - Root'
        Write-Host '  2. Debug - Non-root'
        Write-Host '  3. Debug - Both'
        Write-Host '  4. Hardened test - Both'
        Write-Host '  5. Production release - Root'
        Write-Host '  6. Production release - Non-root'
        Write-Host '  7. Production release - Both'
        Write-Host '  B. Back'
        $selection = Read-Choice 'Select an action' @('1','2','3','4','5','6','7','b')
        switch ($selection) {
            '1' { Invoke-LauncherBuild 'debug' 'root' }
            '2' { Invoke-LauncherBuild 'debug' 'nonroot' }
            '3' { Invoke-LauncherBuild 'debug' 'both' }
            '4' { Invoke-LauncherBuild 'hardened' 'both' }
            '5' { Invoke-LauncherBuild 'release' 'root' }
            '6' { Invoke-LauncherBuild 'release' 'nonroot' }
            '7' { Invoke-LauncherBuild 'release' 'both' }
            'b' { return }
        }
        if ($selection -ne 'b') { Wait-ForUser }
    }
}

function Build-CompleteRelease {
    Write-Header 'Build complete production package'
    $versionName = Read-Required 'Version name' '1.1.5'
    $versionCode = Convert-VersionToBuild $versionName
    Write-Host "Build derived from version: $versionCode" -ForegroundColor Gray
    Write-Host ''
    Write-Host 'This builds every local module plus Root and Non-root release APKs.' -ForegroundColor Gray
    if (-not (Read-YesNo 'Start the complete release build?')) { return }
    Invoke-ModuleBuild 'release'
    Invoke-LauncherBuild 'release' 'both' $versionCode $versionName
    Show-BuildOutputs
}

function Start-DeviceTest {
    Write-Header 'Build, install, and test on device'
    Write-Host 'Device module tests are local-only.' -ForegroundColor Gray
    Write-Host 'Drag/drop one or more module-output or module source folders; nothing is downloaded from the live catalog.' -ForegroundColor Gray

    Write-Host ''
    Write-Host 'Choose test target:'
    Write-Host '  1. Launcher only'
    Write-Host '  2. Launcher + local module folder(s)'
    Write-Host '  3. Local module folder(s) only - reuse the installed launcher'
    $targetChoice = Read-Choice 'Select target' @('1','2','3','launcher','module','module-only','moduleonly')
    $moduleSelection = 'launcher'
    $moduleFolder = ''
    $moduleOnly = $targetChoice -in @('3','module-only','moduleonly')
    if ($targetChoice -in @('2','3','module','module-only','moduleonly')) {
        $moduleFolder = (Read-Host 'Module folder(s) (drag/drop together, then press Enter)').Trim()
        if (-not $moduleFolder) { throw 'Choose at least one module folder.' }
        $moduleSelection = ''
    }

    Write-Host ''
    Write-Host 'Choose launcher mode:'
    Write-Host '  1. Root'
    Write-Host '  2. Non-root'
    Write-Host '  3. Both'
    $mode = switch (Read-Choice 'Select mode' @('1','2','3','root','nonroot','non-root','both')) {
        { $_ -in @('1','root') } { 'root'; break }
        { $_ -in @('2','nonroot','non-root') } { 'nonroot'; break }
        default { 'both' }
    }

    Write-Host ''
    Write-Host $(if ($moduleOnly) { 'Choose the installed launcher build:' } else { 'Choose launcher build to test:' })
    Write-Host '  1. Debug        - debuggable local staging'
    Write-Host '  2. Release      - production behavior with ADB staging'
    $build = switch (Read-Choice 'Select build' @('1','2','3','debug','release','hardened','hardenedtest','hardened-test')) {
        { $_ -in @('1','debug') } { 'debug'; break }
        { $_ -in @('2','release') } { 'release'; break }
        default { 'release' }
    }

    $setup = 'launcher-only'
    $skipModuleBuild = ''
    if ($moduleSelection -ne 'launcher') {
        Write-Host ''
        Write-Host 'Module test: local stage only'
        Write-Host '  The selected local module folders will be copied into Launcher storage.'
        if ($build -eq 'release') {
            Write-Host '  Release staging uses an ADB-only local import, not the live catalog.' -ForegroundColor Gray
        }
        $setup = 'stage'
        if (Read-YesNo 'Use existing module-output without rebuilding?' $true) {
            $skipModuleBuild = '1'
        }
    }

    $previousModules = $env:JESTER_MODULES
    $previousModuleDir = $env:JESTER_MODULE_DIR
    $previousMode = $env:JESTER_MODE
    $previousBuild = $env:JESTER_LAUNCHER_BUILD
    $previousSetup = $env:JESTER_MODULE_SETUP
    $previousSkipBuild = $env:JESTER_SKIP_MODULE_BUILD
    $previousModuleOnly = $env:JESTER_MODULE_ONLY_TEST
    $previousNoErrorPause = $env:JESTER_NO_ERROR_PAUSE
    try {
        if ($moduleSelection) { $env:JESTER_MODULES = $moduleSelection }
        else { Remove-Item Env:\JESTER_MODULES -ErrorAction SilentlyContinue }
        if ($moduleFolder) { $env:JESTER_MODULE_DIR = $moduleFolder }
        else { Remove-Item Env:\JESTER_MODULE_DIR -ErrorAction SilentlyContinue }
        $env:JESTER_LAUNCHER_BUILD = $build
        $env:JESTER_MODULE_SETUP = $setup
        $env:JESTER_NO_ERROR_PAUSE = '1'
        if ($skipModuleBuild) { $env:JESTER_SKIP_MODULE_BUILD = $skipModuleBuild }
        else { Remove-Item Env:\JESTER_SKIP_MODULE_BUILD -ErrorAction SilentlyContinue }
        if ($moduleOnly) { $env:JESTER_MODULE_ONLY_TEST = '1' }
        else { Remove-Item Env:\JESTER_MODULE_ONLY_TEST -ErrorAction SilentlyContinue }

        $testModes = if ($mode -eq 'both') { @('root', 'nonroot') } else { @($mode) }
        foreach ($testMode in $testModes) {
            $env:JESTER_MODE = $testMode
            $modeLabel = if ($testMode -eq 'root') { 'Root' } else { 'Non-root' }
            $testLabel = if ($moduleOnly) { "Module-only device test ($modeLabel)" } else { "Device test helper ($modeLabel)" }
            Invoke-Checked $testLabel { & (Join-Path $ProjectRoot 'test_helper.cmd') }
            if ($mode -eq 'both' -and $setup -eq 'stage') {
                $env:JESTER_SKIP_MODULE_BUILD = '1'
            }
        }
    } catch {
        Write-Host ''
        Write-Host 'Device test failed. The standalone manager is still running.' -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
    } finally {
        if ($null -eq $previousModules) { Remove-Item Env:\JESTER_MODULES -ErrorAction SilentlyContinue } else { $env:JESTER_MODULES = $previousModules }
        if ($null -eq $previousModuleDir) { Remove-Item Env:\JESTER_MODULE_DIR -ErrorAction SilentlyContinue } else { $env:JESTER_MODULE_DIR = $previousModuleDir }
        if ($null -eq $previousMode) { Remove-Item Env:\JESTER_MODE -ErrorAction SilentlyContinue } else { $env:JESTER_MODE = $previousMode }
        if ($null -eq $previousBuild) { Remove-Item Env:\JESTER_LAUNCHER_BUILD -ErrorAction SilentlyContinue } else { $env:JESTER_LAUNCHER_BUILD = $previousBuild }
        if ($null -eq $previousSetup) { Remove-Item Env:\JESTER_MODULE_SETUP -ErrorAction SilentlyContinue } else { $env:JESTER_MODULE_SETUP = $previousSetup }
        if ($null -eq $previousSkipBuild) { Remove-Item Env:\JESTER_SKIP_MODULE_BUILD -ErrorAction SilentlyContinue } else { $env:JESTER_SKIP_MODULE_BUILD = $previousSkipBuild }
        if ($null -eq $previousModuleOnly) { Remove-Item Env:\JESTER_MODULE_ONLY_TEST -ErrorAction SilentlyContinue } else { $env:JESTER_MODULE_ONLY_TEST = $previousModuleOnly }
        if ($null -eq $previousNoErrorPause) { Remove-Item Env:\JESTER_NO_ERROR_PAUSE -ErrorAction SilentlyContinue } else { $env:JESTER_NO_ERROR_PAUSE = $previousNoErrorPause }
    }
}

function Install-Apk {
    Write-Header 'Install an existing launcher APK'
    $path = (Read-Host 'APK path (drag the file here)').Trim().Trim('"').Trim("'")
    if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or [IO.Path]::GetExtension($path) -ne '.apk') {
        throw 'Choose an existing .apk file.'
    }
    $adb = Get-Command adb.exe -ErrorAction SilentlyContinue
    if (-not $adb) {
        $candidate = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
        if (Test-Path -LiteralPath $candidate) { $adb = Get-Item -LiteralPath $candidate }
    }
    if (-not $adb) { throw 'adb.exe was not found.' }
    if (Read-YesNo 'Install with replacement and downgrade support?') {
        $adbPath = if ($adb.Source) { $adb.Source } else { $adb.FullName }
        Invoke-Checked 'Install launcher APK' { & $adbPath install -r -d $path }
    }
}

function Show-BuildOutputs {
    Write-Header 'Generated build outputs'
    $files = @(
        Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'app\build\outputs\apk') -Filter '*.apk' -Recurse -ErrorAction SilentlyContinue
        Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'module-output') -Filter '*.zip' -ErrorAction SilentlyContinue
    ) | Sort-Object LastWriteTime -Descending
    if ($files.Count -eq 0) { Write-Host 'No APK or module ZIP output exists yet.' -ForegroundColor Yellow }
    else { $files | Select-Object LastWriteTime, Length, FullName | Format-Table -AutoSize }
}

function Clean-BuildOutputs {
    Write-Header 'Clean generated build files'
    Write-Host 'Gradle and module output folders will be regenerated by the next build.' -ForegroundColor Gray
    if (-not (Read-YesNo 'Run the project clean task?')) { return }
    Invoke-Checked 'Clean project' { & (Join-Path $ProjectRoot 'gradlew.bat') clean --no-daemon }
    $moduleOutput = Join-Path $ProjectRoot 'module-output'
    if (Test-Path -LiteralPath $moduleOutput) { Remove-Item -LiteralPath $moduleOutput -Recurse -Force }
    Write-Host 'Generated launcher and module outputs were removed.' -ForegroundColor Green
}

function Run-ProjectTests {
    Write-Header 'Run project tests'
    if (Read-YesNo 'Run all Gradle unit tests now?') {
        Invoke-Checked 'Gradle tests' { & (Join-Path $ProjectRoot 'gradlew.bat') test --no-daemon }
    }
}

try {
    Set-Location -LiteralPath $ProjectRoot
    while ($true) {
        Write-Header 'One helper for modules, launchers, device testing, and outputs'
        Write-Host '  1. Build modules'
        Write-Host '  2. Build launcher'
        Write-Host '  3. Build complete production package'
        Write-Host '  4. Build/install/test on device'
        Write-Host '  5. Install an existing APK'
        Write-Host '  6. View build outputs'
        Write-Host '  7. Run project tests'
        Write-Host '  8. Clean generated builds'
        Write-Host '  0. Exit'
        switch (Read-Choice 'Select an action' @('0','1','2','3','4','5','6','7','8')) {
            '1' { Show-ModuleMenu }
            '2' { Show-LauncherMenu }
            '3' { Build-CompleteRelease; Wait-ForUser }
            '4' { Start-DeviceTest; Wait-ForUser }
            '5' { Install-Apk; Wait-ForUser }
            '6' { Show-BuildOutputs; Wait-ForUser }
            '7' { Run-ProjectTests; Wait-ForUser }
            '8' { Clean-BuildOutputs; Wait-ForUser }
            '0' { exit 0 }
        }
    }
} catch {
    Write-Host ''
    Write-Host 'Standalone Menu Manager could not continue:' -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
