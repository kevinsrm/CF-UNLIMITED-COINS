#!/usr/bin/env bash
# CF LOCK - headless APK build. no Gradle, no Android Studio.
# needs: JDK 11+, kotlin-compiler.jar, android.jar, d8.jar, apksigner.jar, aapt2.
# every path is overridable through the environment.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP="$ROOT"
SRC="$APP/src"
RES="$APP/res"
OUT="${OUT:-$APP/build}"
DIST="${DIST:-$APP/dist}"
APK_NAME="${APK_NAME:-CF-LOCK.apk}"

JAVA="${JAVA:-java}"

# Find the compiler distribution, not just the Kotlin runtime. Explicit
# KOTLINC_JAR / KOTLIN_STDLIB / KOTLIN_HOME overrides take precedence.
# Termux installs its bin symlinks in $PREFIX/bin and the jars in opt/kotlin/lib.
KOTLIN_HOME="${KOTLIN_HOME:-}"
if [[ -z "$KOTLIN_HOME" ]] && command -v kotlinc >/dev/null 2>&1; then
    KOTLINC_BIN="$(readlink -f "$(command -v kotlinc)")"
    KOTLINC_PREFIX="$(dirname "$(dirname "$KOTLINC_BIN")")"
    if [[ -d "$KOTLINC_PREFIX/lib" ]]; then
        KOTLIN_HOME="$KOTLINC_PREFIX"
    fi
fi
if [[ -z "$KOTLIN_HOME" && -n "${PREFIX:-}" && -d "$PREFIX/opt/kotlin/lib" ]]; then
    KOTLIN_HOME="$PREFIX/opt/kotlin"
fi
KOTLINC_JAR="${KOTLINC_JAR:-${KOTLIN_HOME:+$KOTLIN_HOME/lib/kotlin-compiler.jar}}"
KOTLINC_JAR="${KOTLINC_JAR:-$HOME/buildenv/bin/kotlin-compiler.jar}"
KOTLIN_STDLIB="${KOTLIN_STDLIB:-${KOTLIN_HOME:+$KOTLIN_HOME/lib/kotlin-stdlib.jar}}"
KOTLIN_STDLIB="${KOTLIN_STDLIB:-$HOME/buildenv/bin/kotlin-stdlib.jar}"
# Optional: this app does not use coroutines or org.jetbrains.annotations.
COROUTINES="${COROUTINES:-$HOME/buildenv/bin/kotlinx-coroutines-core-jvm.jar}"
ANNOTATIONS="${ANNOTATIONS:-$HOME/buildenv/bin/annotations-13.0.jar}"
ANDROID_JAR="${ANDROID_JAR:-$HOME/buildenv/bin/android.jar}"
D8_JAR="${D8_JAR:-$HOME/buildenv/bin/d8.jar}"
APKSIGNER_JAR="${APKSIGNER_JAR:-$HOME/buildenv/bin/apksigner.jar}"
AAPT2="${AAPT2:-$HOME/buildenv/bin/aapt2}"
KEYSTORE="${KEYSTORE:-$HOME/buildenv/bin/release.jks}"
KS_PASS="${KS_PASS:-cfbuild123}"
KEY_PASS="${KEY_PASS:-cfbuild123}"
KEY_ALIAS="${KEY_ALIAS:-cf}"
MIN_SDK="${MIN_SDK:-24}"
TARGET_SDK="${TARGET_SDK:-36}"

# Fail before deleting previous build products if an input is missing.
require_file() {
    if [[ ! -f "$2" || ! -r "$2" ]]; then
        echo "error: $1 is not a readable file: $2" >&2
        echo "Set $1 to the correct path; see app/README.md." >&2
        exit 1
    fi
}
require_file KOTLINC_JAR "$KOTLINC_JAR"
require_file KOTLIN_STDLIB "$KOTLIN_STDLIB"
require_file ANDROID_JAR "$ANDROID_JAR"
require_file D8_JAR "$D8_JAR"
require_file APKSIGNER_JAR "$APKSIGNER_JAR"
require_file AAPT2 "$AAPT2"
require_file KEYSTORE "$KEYSTORE"
if [[ ! -x "$AAPT2" ]]; then
    echo "error: AAPT2 is not executable: $AAPT2" >&2
    exit 1
fi
if ! command -v "$JAVA" >/dev/null 2>&1; then
    echo "error: JAVA not found: $JAVA (install a JDK or set JAVA)" >&2
    exit 1
fi
for tool in unzip zip; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "error: $tool not found; install it and retry." >&2
        exit 1
    fi
done
if ! unzip -Z1 "$KOTLINC_JAR" org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.class >/dev/null 2>&1; then
    echo "error: KOTLINC_JAR is invalid or does not contain org.jetbrains.kotlin.cli.jvm.K2JVMCompiler: $KOTLINC_JAR" >&2
    echo "On Termux, try: export KOTLINC_JAR=\"\$PREFIX/opt/kotlin/lib/kotlin-compiler.jar\"" >&2
    exit 1
fi

mapfile -d '' -t SOURCES < <(find "$SRC" -type f -name '*.kt' -print0 | sort -z)
if (( ${#SOURCES[@]} == 0 )); then
    echo "error: no Kotlin sources found in $SRC" >&2
    exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/pkg" "$DIST"

echo "[1/6] kotlinc"
COMPILER_CP="$KOTLINC_JAR:$KOTLIN_STDLIB"
# Include the distribution's compiler dependencies (reflect, script runtime,
# etc.), rather than assuming the compiler jar is self-contained.
if [[ -n "$KOTLIN_HOME" && -d "$KOTLIN_HOME/lib" ]]; then
    COMPILER_CP="$COMPILER_CP:$KOTLIN_HOME/lib/*"
fi
for jar in "$COROUTINES" "$ANNOTATIONS"; do
    if [[ -f "$jar" ]]; then
        COMPILER_CP="$COMPILER_CP:$jar"
    fi
done
"$JAVA" -cp "$COMPILER_CP" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -jvm-target 1.8 -nowarn \
    -classpath "$ANDROID_JAR:$KOTLIN_STDLIB" \
    -d "$OUT/classes" \
    "${SOURCES[@]}"

echo "[2/6] d8"
mapfile -d '' -t CLASSES < <(find "$OUT/classes" -type f -name '*.class' -print0)
"$JAVA" -cp "$D8_JAR" com.android.tools.r8.D8 \
    --min-api "$MIN_SDK" --release --lib "$ANDROID_JAR" \
    --classpath "$KOTLIN_STDLIB" \
    --output "$OUT/dex" "${CLASSES[@]}"

echo "[3/6] aapt2 compile"
"$AAPT2" compile --dir "$RES" -o "$OUT/res.zip"

echo "[4/6] aapt2 link"
"$AAPT2" link -o "$OUT/base.apk" \
    --manifest "$APP/AndroidManifest.xml" \
    -I "$ANDROID_JAR" \
    --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
    --auto-add-overlay \
    "$OUT/res.zip"

echo "[5/6] package"
cp "$OUT/base.apk" "$OUT/pkg/unsigned.apk"
(cd "$OUT/pkg" && unzip -q -o unsigned.apk && rm unsigned.apk && cp "$OUT"/dex/*.dex . \
    && zip -q -r -X "$OUT/unsigned.apk" .)

echo "[6/6] sign"
"$JAVA" -jar "$APKSIGNER_JAR" sign \
    --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
    --ks-key-alias "$KEY_ALIAS" \
    --min-sdk-version "$MIN_SDK" \
    --out "$DIST/$APK_NAME" "$OUT/unsigned.apk"

"$JAVA" -jar "$APKSIGNER_JAR" verify --min-sdk-version "$MIN_SDK" "$DIST/$APK_NAME"
echo "built: $DIST/$APK_NAME ($(du -h "$DIST/$APK_NAME" | cut -f1))"
