param(
    [Parameter(Mandatory = $true)]
    [string]$Destination
)

$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
$sourcePrefix = $sourceRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
if ($destinationPath.Equals($sourceRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
    $destinationPath.StartsWith($sourcePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'The public export must be outside the private source tree.'
}
if (Test-Path -LiteralPath $destinationPath) {
    if ((Get-ChildItem -LiteralPath $destinationPath -Force | Select-Object -First 1)) {
        throw "The public export destination already exists and is not empty: $destinationPath"
    }
} else {
    New-Item -ItemType Directory -Path $destinationPath | Out-Null
}

$excluded = @(
    '.agents/',
    'docs/public/',
    'scripts/patch-soul-knight-nonroot.ps1'
)
$tracked = & git -C $sourceRoot ls-files
if ($LASTEXITCODE -ne 0) { throw 'Could not read the private repository file list.' }
foreach ($relative in $tracked) {
    $normalized = $relative.Replace('\', '/')
    if ($normalized -eq 'README.md' -or $excluded.Where({ $normalized.StartsWith($_) }, 'First').Count -gt 0) {
        continue
    }
    $source = Join-Path $sourceRoot $relative
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { continue }
    $target = Join-Path $destinationPath $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
}

$publicDocs = Join-Path $sourceRoot 'docs/public'
Copy-Item -LiteralPath (Join-Path $publicDocs 'README.md') -Destination (Join-Path $destinationPath 'README.md')
foreach ($name in @('SECURITY.md', 'PRIVACY.md', 'SOURCE_AVAILABLE.md', 'THIRD_PARTY_NOTICES.md')) {
    Copy-Item -LiteralPath (Join-Path $publicDocs $name) -Destination (Join-Path $destinationPath $name)
}

$licenseCopies = @{
    'third_party/BlackBox/LICENSE' = 'third_party/BlackBox/LICENSE'
    'third_party/AndKittyInjector/LICENSE' = 'third_party/AndKittyInjector/LICENSE'
    'third_party/AndKittyInjector/KittyMemoryEx/LICENSE' = 'third_party/AndKittyInjector/KittyMemoryEx/LICENSE'
}
foreach ($entry in $licenseCopies.GetEnumerator()) {
    $source = Join-Path $sourceRoot $entry.Key
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Required third-party license is missing: $($entry.Key)"
    }
    $target = Join-Path $destinationPath $entry.Value
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
}

$licenseBundle = Join-Path $destinationPath 'THIRD_PARTY_LICENSES'
New-Item -ItemType Directory -Force -Path $licenseBundle | Out-Null
Copy-Item -LiteralPath (Join-Path $sourceRoot 'third_party/BlackBox/LICENSE') `
    -Destination (Join-Path $licenseBundle 'Dobby-LICENSE')
$keystoneCopying = Join-Path $publicDocs 'Keystone-COPYING'
if (-not (Test-Path -LiteralPath $keystoneCopying -PathType Leaf)) {
    throw 'The reviewed Keystone license text is missing from docs/public/Keystone-COPYING.'
}
Copy-Item -LiteralPath $keystoneCopying -Destination (Join-Path $licenseBundle 'Keystone-COPYING')

Write-Host "Public transparency snapshot created at $destinationPath"
