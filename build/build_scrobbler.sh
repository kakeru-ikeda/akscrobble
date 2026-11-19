#!/usr/bin/env bash
# AKScrobble APK をビルドして署名する。
#   usage: build_scrobbler.sh <package> <versionCode> <outApk> [versionName]
source "$(dirname "${BASH_SOURCE[0]}")/../tools/env.sh"

PKG="${1:?package required}"
VC="${2:?versionCode required}"
OUT="${3:?output apk path required}"
VN="${4:-1.0}"

APP="$PROJECT_ROOT/scrobbler"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -alias "$KS_ALIAS" \
        -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=AKScrobble, O=AKScrobble, C=JP" >/dev/null 2>&1
    echo "created keystore: $KEYSTORE"
fi

mkdir -p "$TMP/res-compiled"
"$AAPT2" compile --dir "$APP/res" -o "$TMP/res-compiled"

"$AAPT2" link \
    -o "$TMP/base.apk" \
    -I "$ANDROID_JAR" \
    "$TMP"/res-compiled/*.flat \
    --manifest "$APP/AndroidManifest.xml" \
    --rename-manifest-package "$PKG" \
    --min-sdk-version 21 \
    --target-sdk-version 28 \
    --version-code "$VC" \
    --version-name "$VN"

mkdir -p "$TMP/classes"
mapfile -t SRCS < <(find "$APP/src" -name '*.java')
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -source 8 -target 8 -parameters \
    -bootclasspath "$ANDROID_JAR" \
    -classpath "$ANDROID_JAR" \
    -d "$TMP/classes" \
    "${SRCS[@]}"

mapfile -t CLASSES < <(find "$TMP/classes" -name '*.class')
"$D8" --lib "$ANDROID_JAR" --min-api 21 --output "$TMP" "${CLASSES[@]}"
(cd "$TMP" && zip -q -X "$TMP/base.apk" classes.dex)

"$ZIPALIGN" -f 4 "$TMP/base.apk" "$TMP/aligned.apk"
# パスワードを引数に載せると実行中 ps から見えるので環境変数経由で渡す。
AKS_KS_PASS_FOR_SIGNER="$KS_PASS" "$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass env:AKS_KS_PASS_FOR_SIGNER \
    --key-pass env:AKS_KS_PASS_FOR_SIGNER \
    --ks-key-alias "$KS_ALIAS" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled false \
    --out "$OUT" "$TMP/aligned.apk"

"$APKSIGNER" verify --verbose --print-certs "$OUT"
echo "built: $OUT ($(stat -c%s "$OUT") bytes)"
