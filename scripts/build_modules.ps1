param(
    [ValidateSet("debug", "release")]
    [string] $Mode = "debug",

    [string] $Module = "",

    [switch] $SkipApp
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$modules = Join-Path $root "modules"
$output = Join-Path $root "module-output"
$androidSdk = $env:ANDROID_HOME
if (-not $androidSdk) {
    $androidSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
$androidJar = Join-Path $androidSdk "platforms\android-35\android.jar"
$androidNdk = $env:ANDROID_NDK_HOME
if (-not $androidNdk) {
    $ndkRoot = Join-Path $androidSdk "ndk"
    $androidNdk = Get-ChildItem -LiteralPath $ndkRoot -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
$toolchain = Join-Path $androidNdk "toolchains\llvm\prebuilt\windows-x86_64\bin"
$clang = Join-Path $toolchain "aarch64-linux-android21-clang++.cmd"
if (-not (Test-Path -LiteralPath $clang)) {
    $clang = Join-Path $toolchain "aarch64-linux-android21-clang++.exe"
}
$strip = Join-Path $toolchain "llvm-strip.exe"
$cmake = $null
$ninja = $null
$cmakeRoot = Join-Path $androidSdk "cmake"
if (Test-Path -LiteralPath $cmakeRoot) {
    $cmakeDir = Get-ChildItem -LiteralPath $cmakeRoot -Directory |
        Sort-Object Name -Descending |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "bin\cmake.exe") } |
        Select-Object -First 1
    if ($cmakeDir) {
        $cmake = Join-Path $cmakeDir.FullName "bin\cmake.exe"
        $ninjaCandidate = Join-Path $cmakeDir.FullName "bin\ninja.exe"
        if (Test-Path -LiteralPath $ninjaCandidate) {
            $ninja = $ninjaCandidate
        }
    }
}
if (-not $cmake) {
    $cmake = "cmake"
}

function Convert-ToRspPath([string] $Path) {
    return '"' + ($Path -replace '\\', '/') + '"'
}

function Convert-ToRspArg([string] $Arg) {
    return '"' + ($Arg -replace '\\', '/') + '"'
}

function Compress-GzipFile([string] $Source, [string] $Destination) {
    $sourceStream = [System.IO.File]::OpenRead($Source)
    try {
        $destinationStream = [System.IO.File]::Create($Destination)
        try {
            $gzipStream = New-Object System.IO.Compression.GZipStream(
                $destinationStream,
                [System.IO.Compression.CompressionLevel]::Optimal,
                $true)
            try {
                $sourceStream.CopyTo($gzipStream, 1024 * 1024)
            } finally {
                $gzipStream.Dispose()
            }
        } finally {
            $destinationStream.Dispose()
        }
    } finally {
        $sourceStream.Dispose()
    }
}

function Convert-ToModuleLookupKey([string] $Value) {
    if (-not $Value) {
        return ""
    }

    return ($Value -replace '[^A-Za-z0-9]', '').ToLowerInvariant()
}

function Assert-PcEmulatorCompatibility(
    [string] $Name,
    [string] $CppDirectory,
    [string] $NativeMainText
) {
    # Otherworld Legends is the device-confirmed custom implementation and the
    # source of this contract. Every template-style module must use the same
    # observer, and every RVA helper must resolve through that observer's ELF
    # load bias without caching a failed early lookup.
    if ($Name -eq 'com.chillyroom.zhmr.gp') {
        foreach ($requiredToken in @(
                'IsNativeBridgeRuntime',
                'ElfScanner::findElf',
                'kEmulatedTargetSettleMilliseconds',
                'IsExecutableLibraryAddress')) {
            if ($NativeMainText -notmatch [regex]::Escape($requiredToken)) {
                throw "Module ${Name} is missing confirmed PC-emulator token: ${requiredToken}."
            }
        }
        return
    }

    $observer = Join-Path $CppDirectory 'Includes\EarlyLoadObserver.hpp'
    $referenceObserver = Join-Path $modules 'com.example.module\cpp\Includes\EarlyLoadObserver.hpp'
    if (-not (Test-Path -LiteralPath $observer -PathType Leaf)) {
        throw "Module ${Name} must include the shared PC-emulator EarlyLoadObserver.hpp."
    }
    $observerText = Get-Content -LiteralPath $observer -Raw
    if ($observerText -cne (Get-Content -LiteralPath $referenceObserver -Raw)) {
        throw "Module ${Name} PC-emulator observer differs from com.example.module. Refresh it before building."
    }
    if ([regex]::Matches(
            $observerText,
            'if\s*\(\s*!IsNativeBridgeRuntime\s*\(\s*\)\s*\)\s*return').Count -lt 2) {
        throw "Module ${Name} must keep ELF scanner fallback restricted to native-bridge runtimes."
    }

    $utils = Join-Path $CppDirectory 'Includes\Utils.cpp'
    if (-not (Test-Path -LiteralPath $utils -PathType Leaf)) {
        throw "Module ${Name} must include cpp\Includes\Utils.cpp."
    }
    $utilsText = Get-Content -LiteralPath $utils -Raw
    if ($utilsText -notmatch 'EarlyLoadObserver::ResolveLibraryBase\s*\(' -or
            $utilsText -match 'lib_links\s*\[\s*libraryName\s*\]\s*=\s*getLibraryAddress\s*\(') {
        throw "Module ${Name} must use ELF load-bias addressing and must not cache a zero library base."
    }

    $cmake = Join-Path $CppDirectory 'CMakeLists.txt'
    $cmakeText = Get-Content -LiteralPath $cmake -Raw
    if ($cmakeText -notmatch 'KittyMemory/KittyScanner\.cpp') {
        throw "Module ${Name} must compile KittyScanner.cpp for translated/APK ELF resolution."
    }

    if ($NativeMainText -match 'TryInstallLuaHooks\s*\(') {
        foreach ($requiredToken in @(
                'IsNativeBridgeRuntime',
                'kNativeBridgeSettleMilliseconds',
                'IsExecutableLibraryAddress')) {
            if ($NativeMainText -notmatch [regex]::Escape($requiredToken)) {
                throw "Custom loader ${Name} is missing PC-emulator hook token: ${requiredToken}."
            }
        }
    }
}

function Resolve-ModuleDirectory([string] $RequestedModule) {
    $requestedModulePath = Join-Path $modules $RequestedModule
    if (Test-Path -LiteralPath $requestedModulePath -PathType Container) {
        return Get-Item -LiteralPath $requestedModulePath
    }

    $lookupKey = Convert-ToModuleLookupKey $RequestedModule
    $matches = @(
        Get-ChildItem -LiteralPath $modules -Directory | Where-Object {
            $moduleDirectory = $_
            $configPath = Join-Path $moduleDirectory.FullName "config.json"
            $candidateKeys = @(
                (Convert-ToModuleLookupKey $moduleDirectory.Name),
                (Convert-ToModuleLookupKey (($moduleDirectory.Name -split '\\.')[-1]))
            )

            if (Test-Path -LiteralPath $configPath -PathType Leaf) {
                try {
                    $candidateConfig = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
                    $candidateKeys += Convert-ToModuleLookupKey ([string] $candidateConfig.package_name)
                    $candidateKeys += Convert-ToModuleLookupKey (([string] $candidateConfig.package_name -split '\\.')[-1])
                    $candidateKeys += Convert-ToModuleLookupKey ([string] $candidateConfig.title)
                } catch {
                    # Invalid module JSON is reported by the normal build validation after selection.
                }
            }

            $candidateKeys -contains $lookupKey
        }
    )

    if ($matches.Count -eq 1) {
        Write-Host "Resolved module '$RequestedModule' to '$($matches[0].Name)'."
        return $matches[0]
    }
    if ($matches.Count -gt 1) {
        $matchNames = ($matches | Select-Object -ExpandProperty Name) -join ", "
        throw "Module name is ambiguous: $RequestedModule (matches: $matchNames). Use the full module directory or package name."
    }

    throw "Module not found: $RequestedModule (no directory, package, package suffix, or title matched under $modules)"
}

if (-not (Test-Path -LiteralPath $modules)) {
    throw "Missing modules directory: $modules"
}
if (-not (Test-Path -LiteralPath $androidJar)) {
    throw "Missing Android platform jar: $androidJar"
}
if (-not (Test-Path -LiteralPath $clang)) {
    throw "Missing Android NDK clang: $clang"
}

if ($Module) {
    $requestedModules = @(
        $Module -split ',' |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ } |
            Select-Object -Unique
    )
    if ($requestedModules.Count -eq 0) {
        throw 'No module was selected.'
    }
    $moduleDirectories = @($requestedModules | ForEach-Object { Resolve-ModuleDirectory $_ })
} else {
    $moduleDirectories = @(Get-ChildItem -LiteralPath $modules -Directory)
}

if ($Module) {
    if (-not (Test-Path -LiteralPath $output -PathType Container)) {
        New-Item -ItemType Directory -Path $output | Out-Null
    }
    foreach ($moduleDirectory in $moduleDirectories) {
        $selectedOutput = Join-Path $output $moduleDirectory.Name
        if (Test-Path -LiteralPath $selectedOutput) {
            Remove-Item -LiteralPath $selectedOutput -Recurse -Force
        }
    }
} else {
    if (Test-Path -LiteralPath $output) {
        Remove-Item -LiteralPath $output -Recurse -Force
    }
    New-Item -ItemType Directory -Path $output | Out-Null
}

foreach ($moduleDir in $moduleDirectories) {
    if ($null -eq $moduleDir -or -not $moduleDir.PSIsContainer) {
        throw "Invalid module directory entry while building modules."
    }

    $name = $moduleDir.Name
    $java = Join-Path $moduleDir.FullName "java"
    $cpp = Join-Path $moduleDir.FullName "cpp"
    $assets = Join-Path $moduleDir.FullName "assets"
    $config = Join-Path $moduleDir.FullName "config.json"
    $features = Join-Path $moduleDir.FullName "features.json"
    $work = Join-Path $env:TEMP "moodtools-module-$name"
    $stage = Join-Path $output $name

    if (-not ((Test-Path -LiteralPath $config) -and (Test-Path -LiteralPath $features) -and (Test-Path -LiteralPath $java) -and (Test-Path -LiteralPath $cpp))) {
        Write-Host "Skipping ${name}: config, features, java, or cpp directory missing"
        continue
    }

    try {
        $moduleConfig = Get-Content -LiteralPath $config -Raw | ConvertFrom-Json
    } catch {
        throw "Invalid JSON in ${config}: $($_.Exception.Message)"
    }
    if ($moduleConfig.nonroot_method -notin @('injection', 'direct_patch')) {
        throw "Module ${name} must declare nonroot_method as 'injection' or 'direct_patch'."
    }
    if ($moduleConfig.nonroot_method -eq 'direct_patch') {
        $launchGuard = Join-Path $java "DirectLaunchGuard.java"
        $componentFactory = Join-Path $java "ModComponentFactory.java"
        if (-not ((Test-Path -LiteralPath $launchGuard -PathType Leaf) -and
                (Test-Path -LiteralPath $componentFactory -PathType Leaf))) {
            throw "Direct-patch module ${name} must include DirectLaunchGuard.java and ModComponentFactory.java."
        }
    }

    $menuSource = Join-Path $java "Menu.java"
    if (-not (Test-Path -LiteralPath $menuSource -PathType Leaf)) {
        throw "Module ${name} must include the shared Menu.java shell."
    }
    $menuText = Get-Content -LiteralPath $menuSource -Raw
    if ($menuText -notmatch 'SYSTEM STATUS' -or
            $menuText -notmatch 'isCompatibilityStatusText') {
        throw "Module ${name} uses a legacy Menu.java without the SYSTEM STATUS compatibility card. Refresh it from com.example.module before building."
    }

    $nativeMain = Join-Path $cpp "Main.cpp"
    if (-not (Test-Path -LiteralPath $nativeMain -PathType Leaf)) {
        throw "Module ${name} must include cpp\Main.cpp."
    }
    $nativeMainText = Get-Content -LiteralPath $nativeMain -Raw
    Assert-PcEmulatorCompatibility $name $cpp $nativeMainText
    $compatibilityDescriptorReferences = [regex]::Matches(
        $nativeMainText,
        '(?i)compatibilityFeatureDescriptor\s*\(').Count
    if ($compatibilityDescriptorReferences -lt 2) {
        throw "Module ${name} must define its compatibility descriptor and include it as the first GetFeatureList row."
    }

    # RegisterNatives aborts the guest process when even one registered method is absent from
    # the injected DEX. Audit the complete Java/native contract before compiling so a migrated
    # module cannot turn that abort into a BlackBox relaunch loop.
    $javaNativeMethods = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal)
    Get-ChildItem -LiteralPath $java -Recurse -File -Filter '*.java' | ForEach-Object {
        $javaSourceText = Get-Content -LiteralPath $_.FullName -Raw
        [regex]::Matches(
            $javaSourceText,
            '\bnative\s+[A-Za-z_$][\w$<>\[\].?]*\s+([A-Za-z_$][\w$]*)\s*\(') |
            ForEach-Object { [void] $javaNativeMethods.Add($_.Groups[1].Value) }
    }

    $registeredNativeMethods = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal)
    Get-ChildItem -LiteralPath $cpp -Recurse -File |
        Where-Object { $_.Extension -in @('.cpp', '.cc', '.cxx') } |
        ForEach-Object {
            $cppSourceText = Get-Content -LiteralPath $_.FullName -Raw
            foreach ($table in [regex]::Matches(
                    $cppSourceText,
                    '(?s)JNINativeMethod\s+[A-Za-z_$][\w$]*\s*\[\s*\]\s*=\s*\{(.*?)\};')) {
                foreach ($entry in [regex]::Matches(
                        $table.Groups[1].Value,
                        '\{\s*OBFUSCATE\("([A-Za-z_$][\w$]*)"\)\s*,\s*OBFUSCATE\s*\(')) {
                    [void] $registeredNativeMethods.Add($entry.Groups[1].Value)
                }
            }
        }

    $missingJavaNatives = @($registeredNativeMethods | Where-Object {
        -not $javaNativeMethods.Contains($_)
    } | Sort-Object)
    if ($missingJavaNatives.Count -gt 0) {
        throw "Module ${name} registers native method(s) missing from its Java DEX: $($missingJavaNatives -join ', '). Restore the module-specific Java helper before building."
    }

    if (Test-Path -LiteralPath $work) {
        Remove-Item -LiteralPath $work -Recurse -Force
    }
    New-Item -ItemType Directory -Path (Join-Path $work "classes"), (Join-Path $work "dex"), (Join-Path $work "native"), $stage | Out-Null
    Copy-Item -LiteralPath $config -Destination (Join-Path $stage "config.json") -Force
    Copy-Item -LiteralPath $features -Destination (Join-Path $stage "features.json") -Force
    if ($Mode -eq "debug") {
        Set-Content -LiteralPath (Join-Path $stage "local-test.json") -Encoding UTF8 -Value '{"schema":1}'
    }
    if (Test-Path -LiteralPath $assets) {
        $assetFiles = @(Get-ChildItem -LiteralPath $assets -Force -ErrorAction SilentlyContinue)
        if ($assetFiles.Count -gt 0) {
            New-Item -ItemType Directory -Path (Join-Path $stage "assets") -Force | Out-Null
            foreach ($asset in $assetFiles) {
                Copy-Item -LiteralPath $asset.FullName -Destination (Join-Path $stage "assets") -Recurse -Force
            }
        }
    }

    $javaSources = Get-ChildItem -LiteralPath $java -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
    if (-not $javaSources) {
        throw "No Java sources found for $name"
    }

    # DEX injection cannot contribute new Android manifest components to the installed game.
    # Shared legacy shell components are handled by the launcher/direct-patch paths, but a
    # module-only helper must be an in-process class (for example a headless Fragment).
    $templateJava = Join-Path $modules "com.example.module\java"
    foreach ($javaSource in $javaSources) {
        $relativeJava = $javaSource.Substring($java.Length + 1)
        $templateCounterpart = Join-Path $templateJava $relativeJava
        if (-not (Test-Path -LiteralPath $templateCounterpart -PathType Leaf)) {
            $helperSource = Get-Content -LiteralPath $javaSource -Raw
            if ($helperSource -match '\bextends\s+(?:[A-Za-z_$][\w$]*\.)*(?:(?:[A-Za-z_$][\w$]*)?(?:Activity|Service)|BroadcastReceiver|ContentProvider)\b') {
                throw "Module-only Java helper ${javaSource} depends on an Android manifest component. Use an in-process helper such as a headless Fragment."
            }
        }
    }

    & javac -source 8 -target 8 -classpath $androidJar -d (Join-Path $work "classes") @javaSources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $classesJar = Join-Path $work "classes.jar"
    & jar cf $classesJar -C (Join-Path $work "classes") "."
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & d8 --min-api 26 --output (Join-Path $work "dex") $classesJar
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Copy-Item -LiteralPath (Join-Path $work "dex\classes.dex") -Destination (Join-Path $stage "classes.dex") -Force

    $nativeOutput = Join-Path $work "native\libmenu_native.so"
    $cmakeLists = Join-Path $cpp "CMakeLists.txt"
    if (Test-Path -LiteralPath $cmakeLists) {
        $cmakeBuild = Join-Path $work "cmake"
        $toolchainFile = Join-Path $androidNdk "build\cmake\android.toolchain.cmake"
        $cmakeArgs = @(
            "-S", $cpp,
            "-B", $cmakeBuild,
            "-G", "Ninja",
            "-DANDROID_ABI=arm64-v8a",
            "-DANDROID_PLATFORM=android-21",
            "-DANDROID_NDK=$androidNdk",
            "-DCMAKE_TOOLCHAIN_FILE=$toolchainFile",
            "-DCMAKE_MAKE_PROGRAM=$ninja",
            "-DCMAKE_BUILD_TYPE=Release"
        )
        if ($name -eq 'com.chillyroom.zhmr.gp') {
            $gameLogicSource = Join-Path $cpp 'Embedded\GameLogic.mod.live_toggle.dll'
            $gameLogicGzip = Join-Path $work 'native\GameLogic.mod.live_toggle.dll.gz'
            if (-not (Test-Path -LiteralPath $gameLogicSource -PathType Leaf)) {
                throw "Embedded GameLogic DLL missing: ${gameLogicSource}"
            }
            Compress-GzipFile $gameLogicSource $gameLogicGzip
            $gameLogicSize = (Get-Item -LiteralPath $gameLogicSource).Length
            $cmakeArgs += "-DEMBEDDED_GAMELOGIC_GZIP=$gameLogicGzip"
            $cmakeArgs += "-DEMBEDDED_GAMELOGIC_UNCOMPRESSED_SIZE=$gameLogicSize"
        }
        & $cmake @cmakeArgs
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & $cmake --build $cmakeBuild --config Release
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        $builtNative = Get-ChildItem -LiteralPath $cmakeBuild -Recurse -Filter "libYourSaviour.so" |
            Select-Object -First 1 -ExpandProperty FullName
        if (-not $builtNative) {
            throw "CMake did not produce libYourSaviour.so for $name"
        }
        Copy-Item -LiteralPath $builtNative -Destination $nativeOutput -Force
    } else {
        $nativeSources = Get-ChildItem -LiteralPath $cpp -Recurse -File |
            Where-Object { $_.Extension -eq ".cpp" -or $_.Extension -eq ".c" } |
            ForEach-Object { $_.FullName }
        if (-not $nativeSources) {
            throw "No native sources found for $name"
        }
        $nativeArgs = @(
            "-shared",
            "-fPIC",
            "-O2",
            "-std=c++17",
            (Convert-ToRspArg "-I$cpp"),
            (Convert-ToRspArg "-I$(Join-Path $cpp "Includes")"),
            (Convert-ToRspArg "-I$(Join-Path $cpp "Dobby")")
        )
        $nativeArgs += ($nativeSources | ForEach-Object { Convert-ToRspPath $_ })
        $dobby = Join-Path $cpp "Dobby\arm64-v8a\libdobby.a"
        $keystone = Join-Path $cpp "KittyMemory\Deps\Keystone\libs-android\arm64-v8a\libkeystone.a"
        if (Test-Path -LiteralPath $dobby) { $nativeArgs += (Convert-ToRspPath $dobby) }
        if (Test-Path -LiteralPath $keystone) { $nativeArgs += (Convert-ToRspPath $keystone) }
        $nativeArgs += @("-llog", "-landroid", "-lEGL", "-lGLESv2", "-o", (Convert-ToRspPath $nativeOutput))

        $nativeRsp = Join-Path $work "native.rsp"
        Set-Content -LiteralPath $nativeRsp -Encoding ASCII -Value $nativeArgs
        & $clang "@$nativeRsp"
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    if ($Mode -eq "release" -and (Test-Path -LiteralPath $strip)) {
        & $strip --strip-unneeded $nativeOutput
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    Copy-Item -LiteralPath $nativeOutput -Destination (Join-Path $stage "libmenu_native.so") -Force

    $zipPath = Join-Path $output "$name.zip"
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    Push-Location $stage
    try {
        & jar cf $zipPath *
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
}

if ($SkipApp) {
    Write-Host "Module build complete. App build/install skipped."
    exit 0
}

if ($Mode -eq "release") {
    & (Join-Path $root "gradlew.bat") :app:assembleRootRelease :app:assembleNonrootRelease --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "Release assets generated under app\build\outputs\apk"
} else {
    & (Join-Path $root "gradlew.bat") :app:assembleNonrootDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $launcherApk = Join-Path $root "app\build\outputs\apk\nonroot\debug\app-nonroot-debug.apk"
    & adb install -r -d $launcherApk
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & adb shell run-as com.moodtools.hub.nonroot mkdir -p files/menus
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    foreach ($moduleStage in Get-ChildItem -LiteralPath $output -Directory) {
        $remoteModule = "files/menus/$($moduleStage.Name)"
        & adb shell run-as com.moodtools.hub.nonroot mkdir -p $remoteModule
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

        foreach ($file in Get-ChildItem -LiteralPath $moduleStage.FullName -Recurse -File) {
            $relativePath = $file.FullName.Substring($moduleStage.FullName.Length).TrimStart('\') -replace '\\', '/'
            $remoteDestination = "$remoteModule/$relativePath"
            $remoteParent = $remoteDestination.Substring(0, $remoteDestination.LastIndexOf('/'))
            $temporaryName = "$($moduleStage.Name)-$($relativePath -replace '[^A-Za-z0-9_.-]', '_')"
            $remoteTemporary = "/data/local/tmp/$temporaryName"

            & adb push $file.FullName $remoteTemporary | Out-Null
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            & adb shell run-as com.moodtools.hub.nonroot mkdir -p $remoteParent
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            & adb shell run-as com.moodtools.hub.nonroot cp $remoteTemporary $remoteDestination
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            & adb shell rm -f $remoteTemporary
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
    }
    Write-Host "Debug build installed and module archives staged."
}
