#!/bin/bash
set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_PKG="${1:-com.bandainamcoent.opbrww}"
TITLE="${2:-ONE PIECE Bounty Rush Mod}"
VERSION="${3:-93010}"
BUILD_CODE="${4:-93010}"

echo "=== Building Module for target: $TARGET_PKG ($TITLE v$VERSION code $BUILD_CODE) ==="

ANDROID_HOME="${ANDROID_HOME:-/Users/merlinegomez/Library/Android/sdk}"
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
NDK_DIR="$ANDROID_HOME/ndk/28.2.13676358"
CMAKE="$ANDROID_HOME/cmake/3.22.1/bin/cmake"
NINJA="$ANDROID_HOME/cmake/3.22.1/bin/ninja"
D8="$ANDROID_HOME/build-tools/35.0.0/d8"

WORK_DIR="$REPO_ROOT/build/module-work"
STAGE_DIR="$REPO_ROOT/build/module-stage/$TARGET_PKG"
rm -rf "$WORK_DIR" "$STAGE_DIR"
mkdir -p "$WORK_DIR/classes" "$WORK_DIR/dex" "$WORK_DIR/cmake" "$STAGE_DIR"

MODULE_SRC="${MODULE_SRC:-$REPO_ROOT/modules/com.example.module}"

# 1. Compile Java Sources
echo "[1/4] Compiling Java classes..."
JAVA_FILES=$(find "$MODULE_SRC/java" -name "*.java")
javac -source 8 -target 8 -classpath "$ANDROID_JAR" -d "$WORK_DIR/classes" $JAVA_FILES
jar cf "$WORK_DIR/classes.jar" -C "$WORK_DIR/classes" .
"$D8" --min-api 26 --output "$WORK_DIR/dex" "$WORK_DIR/classes.jar"
cp "$WORK_DIR/dex/classes.dex" "$STAGE_DIR/classes.dex"
echo "  ✓ classes.dex generated"

# 2. Compile Native C++ (.so)
echo "[2/4] Compiling Native C++ (libmenu_native.so for arm64-v8a)..."
"$CMAKE" -S "$MODULE_SRC/cpp" -B "$WORK_DIR/cmake" \
    -G Ninja \
    -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-21 \
    -DANDROID_NDK="$NDK_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=Release

"$CMAKE" --build "$WORK_DIR/cmake" --config Release

BUILT_SO="$WORK_DIR/cmake/libYourSaviour.so"
if [ ! -f "$BUILT_SO" ]; then
    echo "ERROR: Native library not found at $BUILT_SO"
    exit 1
fi
cp "$BUILT_SO" "$STAGE_DIR/libmenu_native.so"
echo "  ✓ libmenu_native.so generated"

# 3. Create config.json and metadata
echo "[3/4] Generating config.json and features.json..."
cat <<EOF > "$STAGE_DIR/config.json"
{
  "package_name": "$TARGET_PKG",
  "title": "$TITLE",
  "supported_versions": ["$VERSION"],
  "supported_version_codes": [$BUILD_CODE],
  "supported_abis": ["arm64-v8a"],
  "nonroot_method": "injection",
  "entry_point": "com.android.support.Main",
  "dex_file": "classes.dex",
  "native_file": "libmenu_native.so"
}
EOF

cp "$MODULE_SRC/features.json" "$STAGE_DIR/features.json"
echo '{"schema":1}' > "$STAGE_DIR/local-test.json"

# 4. Create module bundle ZIP
echo "[4/4] Packing module bundle ZIP..."
BUNDLE_ZIP="$REPO_ROOT/build/module-$TARGET_PKG.zip"
rm -f "$BUNDLE_ZIP"
(cd "$STAGE_DIR" && zip -r "$BUNDLE_ZIP" config.json features.json classes.dex libmenu_native.so local-test.json)

echo "=== Successfully built module bundle: $BUNDLE_ZIP ==="
ls -lh "$BUNDLE_ZIP"
