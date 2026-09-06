[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ModuleDirectory,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath
)

$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $ModuleDirectory).Path
$required = @('config.json', 'classes.dex', 'libmenu_native.so')
$optional = @('features.json', 'local-test.json')
foreach ($name in $required) {
    $path = Join-Path $source $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Local test module is missing $name`: $source"
    }
}

$destination = [System.IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $destination
if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
}
if (Test-Path -LiteralPath $destination) {
    Remove-Item -LiteralPath $destination -Force
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open(
    $destination,
    [System.IO.Compression.ZipArchiveMode]::Create
)
try {
    foreach ($name in @($required + $optional)) {
        $path = Join-Path $source $name
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $archive,
                $path,
                $name,
                [System.IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
    }
} finally {
    $archive.Dispose()
}

Write-Output $destination
