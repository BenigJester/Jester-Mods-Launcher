[CmdletBinding()]
param(
    [string] $InputValue = $env:JESTER_MODULE_DROP_INPUT,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [string] $BuiltOutputRoot = ''
)

$ErrorActionPreference = 'Stop'

function Split-DroppedPaths([string] $Value) {
    $trimmed = $Value.Trim()
    if (-not $trimmed) {
        throw 'No module folder was provided.'
    }

    # Preserve an unquoted single path containing spaces. Explorer surrounds each path with
    # quotes when several folders are dragged into a console prompt.
    if (Test-Path -LiteralPath $trimmed -PathType Container) {
        return @($trimmed)
    }

    $paths = [System.Collections.Generic.List[string]]::new()
    $position = 0
    $pattern = [regex]'\G\s*(?:"([^"]+)"|''([^'']+)''|(\S+))'
    while ($position -lt $trimmed.Length) {
        $match = $pattern.Match($trimmed, $position)
        if (-not $match.Success) {
            throw "Could not read the dropped module folders near: $($trimmed.Substring($position))"
        }
        $path = if ($match.Groups[1].Success) {
            $match.Groups[1].Value
        } elseif ($match.Groups[2].Success) {
            $match.Groups[2].Value
        } else {
            $match.Groups[3].Value
        }
        if ($path) { $paths.Add($path) }
        $position = $match.Index + $match.Length
    }
    return @($paths)
}

$resolved = [System.Collections.Generic.List[object]]::new()
$seenPackages = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase
)

foreach ($path in @(Split-DroppedPaths $InputValue)) {
    $item = Get-Item -LiteralPath $path -ErrorAction Stop
    if (-not $item.PSIsContainer) {
        throw "Module path must be a folder: $path"
    }
    $configPath = Join-Path $item.FullName 'config.json'
    if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
        throw "Module folder must contain config.json: $($item.FullName)"
    }
    $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    $packageName = [string] $config.package_name
    if ($packageName -notmatch '^[A-Za-z0-9_.]{3,200}$') {
        throw "config.json package_name is invalid: $($item.FullName)"
    }
    if (-not $seenPackages.Add($packageName)) {
        throw "The same module was dropped more than once: $packageName"
    }
    $isBuilt = @('config.json', 'classes.dex', 'libmenu_native.so') | ForEach-Object {
        Test-Path -LiteralPath (Join-Path $item.FullName $_) -PathType Leaf
    } | Where-Object { -not $_ } | Measure-Object | Select-Object -ExpandProperty Count

    $resolved.Add([pscustomobject]@{
        Package = $packageName
        Path = $item.FullName
        Built = if ($isBuilt -eq 0) { '1' } else { '0' }
    })
}

if ($resolved.Count -eq 0) {
    throw 'No module folder was provided.'
}
if ($resolved.Count -gt 100) {
    throw 'At most 100 module folders can be staged at once.'
}

$parent = Split-Path -Parent $OutputPath
if ($parent -and -not (Test-Path -LiteralPath $parent -PathType Container)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
}
$lines = if ($BuiltOutputRoot) {
    $outputRoot = [System.IO.Path]::GetFullPath($BuiltOutputRoot)
    $resolved | ForEach-Object {
        $stagePath = if ($_.Built -eq '1') { $_.Path } else { Join-Path $outputRoot $_.Package }
        '{0}|{1}' -f $_.Package, $stagePath
    }
} else {
    $resolved | ForEach-Object { '{0}|{1}|{2}' -f $_.Package, $_.Path, $_.Built }
}
Set-Content -LiteralPath $OutputPath -Encoding Default -Value $lines
