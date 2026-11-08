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
KOTLINC_JAR="${KOTLINC_JAR:-$HOME/buildenv/bin/kotlin-compiler.jar}"
KOTLIN_STDLIB="${KOTLIN_STDLIB:-$HOME/buildenv/bin/kotlin-stdlib.jar}"
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

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/pkg" "$DIST"

echo "[1/6] kotlinc"
"$JAVA" -cp "$KOTLINC_JAR:$KOTLIN_STDLIB:$COROUTINES:$ANNOTATIONS" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -jvm-target 1.8 -nowarn \
    -classpath "$ANDROID_JAR:$KOTLIN_STDLIB" \
    -d "$OUT/classes" \
    $(find "$SRC" -name '*.kt' | sort)

echo "[2/6] d8"
CLASSES=$(find "$OUT/classes" -name '*.class')
"$JAVA" -cp "$D8_JAR" com.android.tools.r8.D8 \
    --min-api "$MIN_SDK" --release --lib "$ANDROID_JAR" \
    --classpath "$KOTLIN_STDLIB" \
    --output "$OUT/dex" $CLASSES

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
