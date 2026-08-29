[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ModuleBundle,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9._-]{2,63}$')]
    [string]$Scope,

    [ValidateSet('root', 'nonroot')]
    [string]$Flavor = 'nonroot',

    [ValidateSet('debug', 'release')]
    [string]$BuildType = 'release',

    [string]$WebsiteCatalogPath = '',

    [ValidatePattern('^$|^\d+\.\d+\.\d+$')]
    [string]$VersionName = ''
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$bundlePath = (Resolve-Path -LiteralPath $ModuleBundle).Path
if ([System.IO.Path]::GetExtension($bundlePath) -ne '.zip') {
    throw 'The embedded module bundle must be a ZIP file.'
}

if (-not [string]::IsNullOrWhiteSpace($WebsiteCatalogPath)) {
    $catalogPath = (Resolve-Path -LiteralPath $WebsiteCatalogPath).Path
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($bundlePath)
    try {
        $configEntry = $archive.GetEntry('config.json')
        if ($null -eq $configEntry) { throw 'The module bundle does not contain config.json.' }
        $reader = [System.IO.StreamReader]::new($configEntry.Open(), [System.Text.Encoding]::UTF8, $true)
        try {
            $config = $reader.ReadToEnd().TrimStart([char]0xFEFF) | ConvertFrom-Json
        } finally {
            $reader.Dispose()
        }
    } finally {
        $archive.Dispose()
    }
    $packageName = [string]$config.package_name
    if ([string]::IsNullOrWhiteSpace($packageName)) { $packageName = [string]$config.target_package }
    $catalog = @(Get-Content -Raw -LiteralPath $catalogPath | ConvertFrom-Json | ForEach-Object { $_ })
    $matching = @($catalog | Where-Object { [string]$_.packageName -ceq $packageName })
    if ($matching.Count -ne 1 -or [string]$matching[0].privateScope -cne $Scope) {
        throw "The website catalog must contain exactly one $packageName entry with privateScope '$Scope'."
    }
}

$flavorTaskName = $Flavor.Substring(0, 1).ToUpperInvariant() + $Flavor.Substring(1)
$buildTaskName = $BuildType.Substring(0, 1).ToUpperInvariant() + $BuildType.Substring(1)
$gradleTask = ":app:assemble$flavorTaskName$buildTaskName"
$gradle = Join-Path $repositoryRoot 'gradlew.bat'
$gradleArguments = [System.Collections.Generic.List[string]]::new()
$gradleArguments.Add($gradleTask)
$gradleArguments.Add("-PprivateModuleBundle=$bundlePath")
$gradleArguments.Add("-PprivateModuleScope=$Scope")
if (-not [string]::IsNullOrWhiteSpace($VersionName)) {
    $versionCodeText = $VersionName.Replace('.', '')
    $versionCode = 0L
    if (-not [long]::TryParse($versionCodeText, [ref]$versionCode) -or $versionCode -le 0) {
        throw "Version '$VersionName' cannot be represented as a positive build number."
    }
    $gradleArguments.Add("-PlauncherVersionCode=$versionCode")
    $gradleArguments.Add("-PlauncherVersionName=$VersionName")
}
$gradleArguments.Add('--no-build-cache')

Push-Location $repositoryRoot
try {
    & $gradle @gradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

$apk = Join-Path $repositoryRoot "app/build/outputs/apk/$Flavor/$BuildType/app-$Flavor-$BuildType.apk"
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
    throw "The build completed but the expected APK was not found: $apk"
}

Write-Output $apk
