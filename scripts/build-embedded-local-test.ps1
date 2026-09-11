[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ModuleBundle,

    [ValidateSet('root', 'nonroot', 'both')]
    [string] $Flavor = 'nonroot',

    [ValidateSet('debug', 'release')]
    [string] $BuildType = 'debug',

    [ValidatePattern('^$|^\d+\.\d+\.\d+$')]
    [string] $VersionName = ''
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$bundlePath = (Resolve-Path -LiteralPath $ModuleBundle).Path
if ([System.IO.Path]::GetExtension($bundlePath) -ne '.zip') {
    throw 'The embedded local test module bundle must be a ZIP file.'
}

$buildTaskName = $BuildType.Substring(0, 1).ToUpperInvariant() + $BuildType.Substring(1)
$tasks = switch ($Flavor) {
    'root' { @(":app:assembleRoot$buildTaskName") }
    'nonroot' { @(":app:assembleNonroot$buildTaskName") }
    'both' { @(":app:assembleRoot$buildTaskName", ":app:assembleNonroot$buildTaskName") }
}
$arguments = [System.Collections.Generic.List[string]]::new()
$tasks | ForEach-Object { $arguments.Add($_) }
$arguments.Add("-PlocalTestModuleBundle=$bundlePath")
if ($BuildType -eq 'release') {
    if ([string]::IsNullOrWhiteSpace($VersionName)) {
        throw 'A release build requires -VersionName in major.minor.patch format.'
    }
    $versionCode = 0L
    if (-not [long]::TryParse($VersionName.Replace('.', ''), [ref]$versionCode) -or $versionCode -le 0) {
        throw "Version '$VersionName' cannot be represented as a positive build number."
    }
    $arguments.Add("-PlauncherVersionCode=$versionCode")
    $arguments.Add("-PlauncherVersionName=$VersionName")
}
$arguments.Add('--no-daemon')

Push-Location $repositoryRoot
try {
    & (Join-Path $repositoryRoot 'gradlew.bat') @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

$selectedFlavors = if ($Flavor -eq 'both') { @('root', 'nonroot') } else { @($Flavor) }
foreach ($selectedFlavor in $selectedFlavors) {
    $apk = Join-Path $repositoryRoot "app/build/outputs/apk/$selectedFlavor/$BuildType/app-$selectedFlavor-$BuildType.apk"
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        throw "The build completed but the expected APK was not found: $apk"
    }
    Write-Output $apk
}
