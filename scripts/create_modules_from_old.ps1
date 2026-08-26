[CmdletBinding()]
param(
    [switch] $RefreshNative
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$OldMenuRoot = Join-Path (Split-Path -Parent (Split-Path -Parent $ProjectRoot)) 'Mod-menu'
$ModulesRoot = Join-Path $ProjectRoot 'modules'
$TemplateRoot = Join-Path $ModulesRoot 'com.example.module'

$Games = @(
    @{ Old = 'Mine-Survival'; Package = 'com.WildSoda.MineSurvival'; Title = 'Mine Survival'; Version = '2.9.4' },
    @{ Old = 'Otherworld Legend'; Package = 'com.chillyroom.zhmr.gp'; Title = 'Otherworld Legends'; Version = '3.4.0' },
    @{ Old = 'Soul-Knight'; Package = 'com.ChillyRoom.DungeonShooter'; Title = 'Soul Knight'; Version = '8.5.0' },
    @{ Old = 'Survivor-Island'; Package = 'com.jlyt.SurvivorIsland'; Title = 'Survivor Island'; Version = '202' },
    @{ Old = 'The-Wild-Darkness'; Package = 'com.ctugames.km2'; Title = 'The Wild Darkness'; Version = '1.4.31' },
    @{ Old = 'Zombie-Tsunami'; Package = 'net.mobigame.zombietsunami'; Title = 'Zombie Tsunami'; Version = '4.7.3' },
    @{ Old = 'Candy Crash Saga'; Package = 'com.king.candycrushsaga'; Title = 'Candy Crush Saga'; Version = '1.333.2.1' },
    @{ Old = 'Cooking Madness'; Package = 'com.biglime.cookingmadness'; Title = 'Cooking Madness'; Version = '3.3.7' },
    @{ Old = 'Dungeon Village 2'; Package = 'net.kairosoft.android.bouken2'; Title = 'Dungeon Village 2'; Version = '1.6.0' },
    @{ Old = 'Game Dev Tycoon'; Package = 'com.greenheartgames.gdt'; Title = 'Game Dev Tycoon'; Version = '1.6.18' },
    @{ Old = 'Kingdom Adventurers'; Package = 'net.kairosoft.android.kingdom_en'; Title = 'Kingdom Adventurers'; Version = '2.6.2' },
    @{ Old = 'Kingshot'; Package = 'com.run.tower.defense'; Title = 'Kingshot'; Version = '1.11.25' }
)

function Copy-DirectoryContents([string] $Source, [string] $Destination) {
    if (Test-Path -LiteralPath $Destination) {
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse -Force
    }
}

function Copy-OptionalPath([string] $Source, [string] $Destination) {
    if (-not (Test-Path -LiteralPath $Source)) {
        return
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $Destination) -Force | Out-Null
    Copy-Item -LiteralPath $Source -Destination $Destination -Recurse -Force
}

function Get-FeatureItems([string] $MainSource) {
    $text = Get-Content -LiteralPath $MainSource -Raw
    $matches = [regex]::Matches($text, 'OBFUSCATE\("([^"\\]*(?:\\.[^"\\]*)*)"\)')
    $items = [System.Collections.Generic.List[string]]::new()
    foreach ($match in $matches) {
        $value = $match.Groups[1].Value
        if ($value -notmatch '^(?:\d+_)?(?:Collapse|CollapseAdd|Category|Toggle|Button|SeekBar|Input|InputValue|InputText|InputFloat|InputLong|Spinner|RadioButton|CheckBox|RichTextView|TextView)') {
            continue
        }
        if ($value -match '^Category_(Preferences|Settings|Menu|Navigation)$' -or
            $value -match 'Save feature preferences|Expanded panel height|Auto size vertically|Menu animations|Color animations') {
            continue
        }

        $label = $value `
            -replace '^\d+_', '' `
            -replace '^(CollapseAdd_)+', '' `
            -replace '^(Collapse_)+', '' `
            -replace '^Category_', '' `
            -replace '^(Toggle|Button|SeekBar|InputValue|InputText|InputFloat|InputLong|Spinner|RadioButton|CheckBox|RichTextView|TextView)_', '' `
            -replace '_[0-9]+_[0-9]+$', '' `
            -replace '_', ' '
        $label = $label.Trim()
        if ($label -and -not $items.Contains($label)) {
            $items.Add($label)
        }
    }
    return @($items)
}

function Write-JsonFile([string] $Path, [object] $Value, [int] $Depth = 8) {
    $Value | ConvertTo-Json -Depth $Depth | Set-Content -LiteralPath $Path -Encoding UTF8
}

function New-FeatureGroups([string[]] $Features) {
    $maxGroupSize = 35
    $groups = [System.Collections.Generic.List[object]]::new()
    for ($offset = 0; $offset -lt $Features.Count; $offset += $maxGroupSize) {
        $chunk = @($Features | Select-Object -Skip $offset -First $maxGroupSize)
        $suffix = if ($Features.Count -gt $maxGroupSize) {
            " " + ([int]($offset / $maxGroupSize) + 1)
        } else {
            ""
        }
        $groups.Add([ordered]@{
            title = "Included controls$suffix"
            features = $chunk
        })
    }
    return @($groups)
}

function Get-NativeJavaMethods([string] $JavaRoot) {
    if (-not (Test-Path -LiteralPath $JavaRoot -PathType Container)) {
        return @()
    }
    $methods = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal)
    Get-ChildItem -LiteralPath $JavaRoot -Recurse -File -Filter '*.java' | ForEach-Object {
        $source = Get-Content -LiteralPath $_.FullName -Raw
        foreach ($match in [regex]::Matches(
                $source,
                '\bnative\s+[A-Za-z_$][\w$<>\[\].?]*\s+([A-Za-z_$][\w$]*)\s*\(')) {
            [void]$methods.Add($match.Groups[1].Value)
        }
    }
    return @($methods)
}

function Get-RegisteredNativeMethods([string] $CppRoot) {
    if (-not (Test-Path -LiteralPath $CppRoot -PathType Container)) {
        return @()
    }
    $methods = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal)
    Get-ChildItem -LiteralPath $CppRoot -Recurse -File |
        Where-Object { $_.Extension -in @('.cpp', '.cc', '.cxx') } |
        ForEach-Object {
            $source = Get-Content -LiteralPath $_.FullName -Raw
            foreach ($table in [regex]::Matches(
                    $source,
                    '(?s)JNINativeMethod\s+[A-Za-z_$][\w$]*\s*\[\s*\]\s*=\s*\{(.*?)\};')) {
                foreach ($entry in [regex]::Matches(
                        $table.Groups[1].Value,
                        '\{\s*OBFUSCATE\("([A-Za-z_$][\w$]*)"\)\s*,\s*OBFUSCATE\s*\(')) {
                    [void] $methods.Add($entry.Groups[1].Value)
                }
            }
        }
    return @($methods)
}

if (-not (Test-Path -LiteralPath $TemplateRoot)) {
    throw "Missing module template: $TemplateRoot"
}

foreach ($game in $Games) {
    $oldProject = Join-Path $OldMenuRoot $game.Old
    if (-not (Test-Path -LiteralPath $oldProject)) {
        throw "Missing old menu project: $oldProject"
    }

    $destination = Join-Path $ModulesRoot $game.Package
    $templateJavaRoot = Join-Path $TemplateRoot 'java'
    $templateJavaFiles = @{}
    Get-ChildItem -LiteralPath $templateJavaRoot -Recurse -File -Filter '*.java' | ForEach-Object {
        $relative = $_.FullName.Substring($templateJavaRoot.Length + 1)
        $templateJavaFiles[$relative] = $true
    }

    # Module-only helpers often contain hand-migrated fixes that cannot be reconstructed from
    # the shared shell. Preserve them when this generator refreshes an existing local module.
    $preservedJavaHelpers = @{}
    $preservedSharedJavaOverrides = @{}
    $preservedNativeMain = $null
    $existingJavaRoot = Join-Path $destination 'java'
    $existingNativeMain = Join-Path $destination 'cpp\Main.cpp'
    $generatedRuntimeRelative = ($game.Package -replace '\.', '\') + '\ModuleRuntime.java'
    if (Test-Path -LiteralPath $existingJavaRoot) {
        Get-ChildItem -LiteralPath $existingJavaRoot -Recurse -File -Filter '*.java' | ForEach-Object {
            $relative = $_.FullName.Substring($existingJavaRoot.Length + 1)
            if ($templateJavaFiles.ContainsKey($relative)) {
                $templateCounterpart = Join-Path $templateJavaRoot $relative
                $existingHash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
                $templateHash = (Get-FileHash -LiteralPath $templateCounterpart -Algorithm SHA256).Hash
                if ($existingHash -ne $templateHash) {
                    # Some games extend shared filenames such as Menu.java and
                    # OfflineTranslator.java. Preserve those reviewed overrides; replacing them
                    # with the generic shell can leave RegisterNatives pointing at missing Java
                    # methods and make BlackBox relaunch the guest indefinitely.
                    $preservedSharedJavaOverrides[$relative] =
                        Get-Content -LiteralPath $_.FullName -Raw
                }
            } elseif (-not $relative.Equals(
                    $generatedRuntimeRelative,
                    [System.StringComparison]::OrdinalIgnoreCase)) {
                $preservedJavaHelpers[$relative] = Get-Content -LiteralPath $_.FullName -Raw
            }
        }
    }
    if (-not $RefreshNative -and (Test-Path -LiteralPath $existingNativeMain -PathType Leaf)) {
        # Local module Main.cpp files contain compatibility status and game fixes layered on top
        # of the legacy source. A Java/helper refresh must not silently replace that refined native
        # implementation. Pass -RefreshNative only when intentionally re-importing native code.
        $preservedNativeMain = Get-Content -LiteralPath $existingNativeMain -Raw
    }

    if (Test-Path -LiteralPath $destination) {
        Remove-Item -LiteralPath $destination -Recurse -Force
    }
    New-Item -ItemType Directory -Path $destination -Force | Out-Null

    Copy-DirectoryContents (Join-Path $TemplateRoot 'cpp') (Join-Path $destination 'cpp')
    Copy-DirectoryContents (Join-Path $TemplateRoot 'java') (Join-Path $destination 'java')

    $oldJni = Join-Path $oldProject 'app\src\main\jni'
    foreach ($file in @('Main.cpp', 'CMakeLists.txt', 'LuaDiscovery.hpp', 'EmbeddedGameLogic.S.in')) {
        Copy-OptionalPath (Join-Path $oldJni $file) (Join-Path $destination "cpp\$file")
    }
    if ($null -ne $preservedNativeMain) {
        [System.IO.File]::WriteAllText(
            (Join-Path $destination 'cpp\Main.cpp'),
            $preservedNativeMain,
            [System.Text.UTF8Encoding]::new($false))
    }
    foreach ($directory in @('Menu', 'Includes', 'Dobby', 'KittyMemory', 'xDL')) {
        $sourceDirectory = Join-Path $oldJni $directory
        if (Test-Path -LiteralPath $sourceDirectory) {
            Copy-DirectoryContents $sourceDirectory (Join-Path $destination "cpp\$directory")
        }
    }
    Copy-OptionalPath `
        (Join-Path $oldJni 'Embedded\GameLogic.mod.live_toggle.dll') `
        (Join-Path $destination 'cpp\Embedded\GameLogic.mod.live_toggle.dll')

    $oldJavaRoot = Join-Path $oldProject 'app\src\main\java'
    $supportPrefix = 'com\android\support\'
    if (Test-Path -LiteralPath $oldJavaRoot) {
        Get-ChildItem -LiteralPath $oldJavaRoot -Recurse -File -Filter '*.java' | ForEach-Object {
            $sourceRelative = $_.FullName.Substring($oldJavaRoot.Length + 1)
            $relative = if ($sourceRelative.StartsWith(
                    $supportPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                $sourceRelative.Substring($supportPrefix.Length)
            } else {
                $sourceRelative
            }
            if (-not $templateJavaFiles.ContainsKey($relative)) {
                Copy-OptionalPath $_.FullName (Join-Path $destination "java\$relative")
            }
        }
    }

    $packageJavaRoot = Join-Path $destination ('java\' + ($game.Package -replace '\.', '\'))
    New-Item -ItemType Directory -Path $packageJavaRoot -Force | Out-Null
    @"
package $($game.Package);

/** Package-specific loader required by the standalone launcher. */
public final class ModuleRuntime {
    private ModuleRuntime() {
    }

    public static void loadNative(String absolutePath) {
        com.android.support.ModuleRuntime.loadNative(absolutePath);
    }
}
"@ | Set-Content -LiteralPath (Join-Path $packageJavaRoot 'ModuleRuntime.java') -Encoding ASCII

    foreach ($relative in $preservedJavaHelpers.Keys) {
        $helperDestination = Join-Path $destination "java\$relative"
        New-Item -ItemType Directory -Path (Split-Path -Parent $helperDestination) -Force | Out-Null
        [System.IO.File]::WriteAllText(
            $helperDestination,
            $preservedJavaHelpers[$relative],
            [System.Text.UTF8Encoding]::new($false))
    }

    foreach ($relative in $preservedSharedJavaOverrides.Keys) {
        $overrideDestination = Join-Path $destination "java\$relative"
        [System.IO.File]::WriteAllText(
            $overrideDestination,
            $preservedSharedJavaOverrides[$relative],
            [System.Text.UTF8Encoding]::new($false))
    }

    # Shared filenames such as Main.java and Menu.java come from the module-safe template, but old
    # games can add JNI callbacks to those files. Do not silently produce a module whose native
    # RegisterNatives table references a Java method discarded by migration.
    $oldNativeMethods = @(Get-NativeJavaMethods $oldJavaRoot)
    $migratedNativeMethods = @(Get-NativeJavaMethods (Join-Path $destination 'java'))
    $missingNativeMethods = @($oldNativeMethods | Where-Object {
        $_ -notin $migratedNativeMethods
    })
    if ($missingNativeMethods.Count -gt 0) {
        throw "Module $($game.Package) lost native Java method(s) during migration: $($missingNativeMethods -join ', '). Add guarded compatibility declarations to the shared template."
    }

    $registeredNativeMethods = @(Get-RegisteredNativeMethods (Join-Path $destination 'cpp'))
    $missingRegisteredMethods = @($registeredNativeMethods | Where-Object {
        $_ -notin $migratedNativeMethods
    } | Sort-Object)
    if ($missingRegisteredMethods.Count -gt 0) {
        throw "Module $($game.Package) registers native method(s) missing after migration: $($missingRegisteredMethods -join ', '). Restore its module-specific Java override before exporting."
    }

    # An injected DEX cannot add Android components to an already installed game's manifest.
    # Fail migration instead of producing a helper that compiles but can never be launched.
    Get-ChildItem -LiteralPath (Join-Path $destination 'java') -Recurse -File -Filter '*.java' |
        Where-Object {
            $relative = $_.FullName.Substring((Join-Path $destination 'java').Length + 1)
            -not $templateJavaFiles.ContainsKey($relative)
        } | ForEach-Object {
            $source = Get-Content -LiteralPath $_.FullName -Raw
            if ($source -match '\bextends\s+(?:[A-Za-z_$][\w$]*\.)*(?:(?:[A-Za-z_$][\w$]*)?(?:Activity|Service)|BroadcastReceiver|ContentProvider)\b') {
                throw "Module helper $($_.FullName) still depends on an Android manifest component. Convert it to an in-process helper (for example, a headless Fragment) before migration."
            }
        }

    Write-JsonFile (Join-Path $destination 'config.json') ([ordered]@{
        package_name = $game.Package
        title = $game.Title
        supported_versions = @($game.Version)
        supported_abis = @('arm64-v8a')
        nonroot_method = 'injection'
        entry_point = 'com.android.support.Main'
        dex_file = 'classes.dex'
        native_file = 'libmenu_native.so'
    })

    $features = @(Get-FeatureItems (Join-Path $destination 'cpp\Main.cpp'))
    if ($features.Count -eq 0) {
        $features = @('Launcher support menu')
    }
    Write-JsonFile (Join-Path $destination 'features.json') ([ordered]@{
        schema = 1
        groups = @(New-FeatureGroups $features)
    })

    @"
# $($game.Title) launcher module

Migrated from the old standalone menu into the Jester Moods launcher module format.

This local module keeps the old `com.android.support` JNI/menu namespace for compatibility, adds a package-specific `ModuleRuntime` for launcher loading, and excludes the old app updater/installer shell.
"@ | Set-Content -LiteralPath (Join-Path $destination 'README.md') -Encoding UTF8
}

Write-Host "Generated $($Games.Count) local module source directories."
