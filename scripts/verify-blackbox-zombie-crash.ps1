[CmdletBinding()]
param(
    [ValidateRange(10, 300)]
    [int]$TimeoutSeconds = 90,

    [ValidateRange(5, 60)]
    [int]$StabilitySeconds = 15,

    [string]$Serial = ''
)

$ErrorActionPreference = 'Stop'
$LauncherPackage = 'com.moodtools.hub.nonroot'
$ZombiePackage = 'net.mobigame.zombietsunami'
$StateId = 'BB-PLAY-UID-001'

function Resolve-Adb {
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $candidate = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    throw 'adb.exe was not found.'
}

$Adb = Resolve-Adb
$AdbTarget = @()
if ($Serial) { $AdbTarget = @('-s', $Serial) }

function Invoke-Adb([string[]]$Arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Adb @AdbTarget @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw "adb failed: $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Get-LogText {
    return (Invoke-Adb @('logcat', '-d', '-v', 'threadtime')) -join [Environment]::NewLine
}

function Get-VendingFatalCount([string]$LogText) {
    return ([regex]::Matches($LogText, 'Process:\s+com\.android\.vending,\s+PID:')).Count
}

function Test-ReferenceState([string]$LogText) {
    $deliberateTrigger = $LogText -match [regex]::Escape(
        "$StateId forcing virtual Play Store provider-query failure"
    ) -or $LogText -match [regex]::Escape(
        "$StateId using service-bind fallback"
    )
    return $deliberateTrigger -and
        $LogText -match 'Process:\s+com\.android\.vending,\s+PID:' -and
        $LogText -match 'SecurityException:\s+Package com\.android\.vending does not belong to \d+' -and
        $LogText -match [regex]::Escape(
            "$StateId returned a stable dead binding after the reference crash"
        )
}

$devices = Invoke-Adb @('devices')
$readyDevices = @($devices | Where-Object { $_ -match '\sdevice$' })
if (-not $Serial -and $readyDevices.Count -ne 1) {
    throw "Expected exactly one connected device, found $($readyDevices.Count). Use -Serial when testing multiple devices."
}

$launcher = Invoke-Adb @('shell', 'pm', 'path', '--user', '0', $LauncherPackage)
if (($launcher -join '') -notmatch 'package:') {
    throw 'Install the non-root APK before running this verifier.'
}

[void](Invoke-Adb @('logcat', '-c'))
[void](Invoke-Adb @(
    'shell', 'monkey', '-p', $LauncherPackage,
    '-c', 'android.intent.category.LAUNCHER', '1'
))

Write-Host ''
Write-Host "Verifying $StateId" -ForegroundColor Cyan
Write-Host 'Open Zombie Tsunami from Jester Mods now.' -ForegroundColor Yellow
Write-Host "Waiting up to $TimeoutSeconds seconds for the exact Play Store crash and stable retry state..."

$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$captured = ''
do {
    Start-Sleep -Milliseconds 750
    $captured = Get-LogText
    if (Test-ReferenceState $captured) { break }
} while ([DateTime]::UtcNow -lt $deadline)

if (-not (Test-ReferenceState $captured)) {
    throw "Did not observe the complete $StateId signature. Confirm Zombie Tsunami was launched through Jester Mods."
}

$initialFatalCount = Get-VendingFatalCount $captured
Start-Sleep -Seconds $StabilitySeconds
$stableLogs = Get-LogText
$finalFatalCount = Get-VendingFatalCount $stableLogs
if ($finalFatalCount -ne $initialFatalCount) {
    throw "Play Store remained in a crash loop: fatal count changed from $initialFatalCount to $finalFatalCount during the stability window."
}

$zombiePid = (Invoke-Adb @('shell', 'pidof', $ZombiePackage)) -join ''
if ([string]::IsNullOrWhiteSpace($zombiePid)) {
    throw 'The Zombie Tsunami process is not alive after the Play Store crash.'
}
if ($stableLogs -match 'Process:\s+net\.mobigame\.zombietsunami') {
    throw 'Zombie Tsunami itself produced a fatal exception.'
}

$activities = (Invoke-Adb @('shell', 'dumpsys', 'activity', 'activities')) -join [Environment]::NewLine
if ($activities -notmatch 'mCurrentFocus=.*net\.mobigame\.zombietsunami/.+ZombieActivity') {
    throw 'ZombieActivity is alive but is not the focused game window.'
}

Write-Host ''
Write-Host "PASS: reproduced $StateId once and stabilized retries" -ForegroundColor Green
Write-Host "Zombie PID $zombiePid remains focused with no game fatal exception."
