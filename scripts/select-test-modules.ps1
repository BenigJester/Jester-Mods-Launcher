[CmdletBinding()]
param(
    [string] $ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [switch] $AllowMultiple,
    [switch] $AllowLauncherOnly,
    [string] $OutputPath = ''
)

$ErrorActionPreference = 'Stop'

function Get-ModuleTitle([string] $ConfigPath, [string] $Fallback) {
    try {
        $config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
        if ($config.title) { return [string] $config.title }
        if ($config.package_name) { return [string] $config.package_name }
    } catch {
        # Keep the selector usable even if one local config is malformed.
    }
    return $Fallback
}

function Get-TestModuleCandidates {
    $byPackage = [ordered]@{}
    $roots = @(
        @{ Path = Join-Path $ProjectRoot 'module-output'; Source = 'built' },
        @{ Path = Join-Path $ProjectRoot 'modules'; Source = 'source' }
    )

    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root.Path -PathType Container)) { continue }
        foreach ($dir in Get-ChildItem -LiteralPath $root.Path -Directory | Sort-Object Name) {
            if ($dir.Name -eq 'com.example.module') { continue }
            $configPath = Join-Path $dir.FullName 'config.json'
            if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) { continue }

            $packageName = $dir.Name
            try {
                $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
                if ($config.package_name) { $packageName = [string] $config.package_name }
            } catch {
                # Directory name remains a good fallback for local testing.
            }

            if (-not $byPackage.Contains($packageName)) {
                $builtPath = Join-Path (Join-Path $ProjectRoot 'module-output') $packageName
                $byPackage[$packageName] = [pscustomobject]@{
                    Package = $packageName
                    Title = Get-ModuleTitle $configPath $packageName
                    HasBuiltOutput = (
                        (Test-Path -LiteralPath (Join-Path $builtPath 'config.json') -PathType Leaf) -and
                        (Test-Path -LiteralPath (Join-Path $builtPath 'classes.dex') -PathType Leaf) -and
                        (Test-Path -LiteralPath (Join-Path $builtPath 'libmenu_native.so') -PathType Leaf)
                    )
                }
            }
        }
    }

    return @($byPackage.Values | Sort-Object Title, Package)
}

function Expand-Selection([string] $InputValue, [object[]] $Modules) {
    $selected = [System.Collections.Generic.List[string]]::new()
    foreach ($part in ($InputValue -split ',')) {
        $token = $part.Trim()
        if (-not $token) { continue }
        if ($token -match '^\d+$') {
            $index = [int] $token
            if ($index -lt 1 -or $index -gt $Modules.Count) { throw "Module number $index is out of range." }
            $selected.Add($Modules[$index - 1].Package)
            continue
        }
        if ($token -match '^(\d+)-(\d+)$') {
            $start = [int] $Matches[1]
            $end = [int] $Matches[2]
            if ($start -lt 1 -or $end -gt $Modules.Count -or $start -gt $end) {
                throw "Module range $token is out of range."
            }
            for ($i = $start; $i -le $end; $i++) { $selected.Add($Modules[$i - 1].Package) }
            continue
        }
        $match = @($Modules | Where-Object {
            $_.Package.Equals($token, [StringComparison]::OrdinalIgnoreCase) -or
            $_.Title.Equals($token, [StringComparison]::OrdinalIgnoreCase)
        })
        if ($match.Count -eq 1) {
            $selected.Add($match[0].Package)
            continue
        }
        throw "Unknown module selection: $token"
    }

    return @($selected | Select-Object -Unique)
}

$modules = @(Get-TestModuleCandidates)
if ($modules.Count -eq 0) {
    throw "No testable modules were found under module-output or modules."
}

while ($true) {
    Write-Host ''
    Write-Host 'Choose module target:'
    for ($index = 0; $index -lt $modules.Count; $index++) {
        $module = $modules[$index]
        $status = if ($module.HasBuiltOutput) { 'built' } else { 'source only' }
        Write-Host ('  {0,2}. {1} ({2}) [{3}]' -f ($index + 1), $module.Title, $module.Package, $status)
    }
    if ($AllowMultiple) { Write-Host '   A. All modules' }
    if ($AllowLauncherOnly) { Write-Host '   L. Launcher only' }
    Write-Host '   B. Back / cancel'
    Write-Host ''

    $hint = if ($AllowMultiple) { 'number, comma list, range, A, L, or B' } else { 'number, L, or B' }
    $choice = (Read-Host "Select target [$hint]").Trim()
    if (-not $choice) { continue }
    if ($choice -match '^[bB]$') { exit 2 }
    if ($AllowLauncherOnly -and $choice -match '^[lL]$') {
        $result = 'launcher'
        break
    }
    if ($AllowMultiple -and $choice -match '^[aA]$') {
        $result = ($modules.Package -join ',')
        break
    }

    try {
        $selected = @(Expand-Selection $choice $modules)
        if ($selected.Count -eq 0) { throw 'No module was selected.' }
        if (-not $AllowMultiple -and $selected.Count -gt 1) { throw 'Choose one module for this action.' }
        $result = ($selected -join ',')
        break
    } catch {
        Write-Host $_.Exception.Message -ForegroundColor Yellow
    }
}

if ($OutputPath) {
    Set-Content -LiteralPath $OutputPath -Encoding ASCII -Value $result
} else {
    $result
}
