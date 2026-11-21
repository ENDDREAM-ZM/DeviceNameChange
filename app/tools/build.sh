#!/bin/sh
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK=/opt/dshgo/jdk-17.0.21+9
SDK=/opt/dshgo/sdk
PLATFORM=$SDK/platforms/android-36/android.jar
BT=$SDK/build-tools/36.0.0
AAPT2=/opt/dshgo/bin/aapt2
QEMU=/opt/dshgo/qemu-x86_64-static
QROOT=/opt/dshgo/x86root
LIBAPI=${LIBAPI:-$ROOT/tools/libxposed-api-102.jar}
LIBSVC=${LIBSVC:-$ROOT/tools/libxposed-service-102.jar}
OUT=$ROOT/dist
BUILD=$ROOT/build

rm -rf "$BUILD" "$OUT"
mkdir -p "$BUILD/compiled" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$OUT"

"$AAPT2" compile --dir "$ROOT/res" -o "$BUILD/res.zip"

"$AAPT2" link \
    -o "$BUILD/base.apk" \
    -I "$PLATFORM" \
    --manifest "$ROOT/AndroidManifest.xml" \
    --java "$BUILD/gen" \
    --min-sdk-version 29 \
    --target-sdk-version 36 \
    --no-version-vectors \
    "$BUILD/res.zip"

find "$ROOT/src" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
"$JDK/bin/javac" -encoding UTF-8 -source 8 -target 8 -nowarn \
    -bootclasspath "$PLATFORM:$BT/core-lambda-stubs.jar" \
    -classpath "$LIBAPI:$LIBSVC" \
    -d "$BUILD/classes" @"$BUILD/sources.txt"

"$JDK/bin/jar" cf "$BUILD/classes.jar" -C "$BUILD/classes" .

"$BT/d8" --release --min-api 29 --lib "$PLATFORM" --output "$BUILD/dex" \
    "$BUILD/classes.jar" "$LIBSVC"

python3 "$ROOT/tools/package.py" "$BUILD/base.apk" "$BUILD/dex/classes.dex" "$ROOT/resources/META-INF/xposed" "$BUILD/aligned_in.apk"

"$QEMU" -L "$QROOT" "$BT/zipalign" -f -p 4 "$BUILD/aligned_in.apk" "$BUILD/aligned.apk"

sh "$BT/apksigner" sign \
    --ks "$ROOT/keystore/dnc.jks" \
    --ks-key-alias enddream \
    --ks-pass pass:enddream2024 \
    --key-pass pass:enddream2024 \
    --min-sdk-version 29 \
    --max-sdk-version 36 \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$OUT/DeviceNameChange-1.0.1.apk" \
    "$BUILD/aligned.apk"

sh "$BT/apksigner" verify --print-certs "$OUT/DeviceNameChange-1.0.1.apk"
echo "built: $OUT/DeviceNameChange-1.0.1.apk"
