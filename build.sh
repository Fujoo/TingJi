#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ROOT=..
AAPT2="$ROOT/build/toolchain/bt34/aapt2.exe"
ZIPALIGN="$ROOT/build/toolchain/bt34/zipalign.exe"
APKSIGNER="$ROOT/build/toolchain/bt34/apksigner.bat"
ANDROID_JAR="$ROOT/build/toolchain/android-28/android.jar"
R8JAR="$ROOT/build/toolchain/r8.jar"
KS="$ROOT/build/debug.keystore"
OUT=build
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0.0}"
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" out
"$AAPT2" compile --dir res -o "$OUT/res.zip"
"$AAPT2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml -R "$OUT/res.zip" --java "$OUT/gen" \
  --auto-add-overlay --min-sdk-version 23 --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"
find java "$OUT/gen" -name '*.java' | sort > "$OUT/sources.txt"
javac --release 8 -encoding UTF-8 -nowarn -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt"
find "$OUT/classes" -name '*.class' | sort > "$OUT/classes.txt"
java -cp "$R8JAR" com.android.tools.r8.D8 --release --min-api 23 \
  --lib "$ANDROID_JAR" --output "$OUT/dex" @"$OUT/classes.txt"
( cd "$OUT/dex" && jar -0uf ../base.apk classes.dex )
"$ZIPALIGN" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/TingJi-v$VERSION_NAME.apk" "$OUT/aligned.apk"
"$APKSIGNER" verify --print-certs "$OUT/TingJi-v$VERSION_NAME.apk" | head -4
cp "$OUT/TingJi-v$VERSION_NAME.apk" out/
ls -lh "out/TingJi-v$VERSION_NAME.apk"
