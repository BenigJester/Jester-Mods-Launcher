[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ModuleBundle,

    [ValidateSet('root', 'nonroot', 'both')]
    [string] $Flavor = 'nonroot'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$bundlePath = (Resolve-Path -LiteralPath $ModuleBundle).Path
if ([System.IO.Path]::GetExtension($bundlePath) -ne '.zip') {
    throw 'The embedded local test module bundle must be a ZIP file.'
}

$tasks = switch ($Flavor) {
    'root' { @(':app:assembleRootDebug') }
    'nonroot' { @(':app:assembleNonrootDebug') }
    'both' { @(':app:assembleRootDebug', ':app:assembleNonrootDebug') }
}
$arguments = @($tasks) + @("-PlocalTestModuleBundle=$bundlePath", '--no-daemon')

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
    $apk = Join-Path $repositoryRoot "app/build/outputs/apk/$selectedFlavor/debug/app-$selectedFlavor-debug.apk"
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        throw "The build completed but the expected APK was not found: $apk"
    }
    Write-Output $apk
}
