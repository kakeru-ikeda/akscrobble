#!/usr/bin/env bash
# config split APK をビルドして署名する。base と同じ鍵を使う。
#   usage: build_split.sh <package> <versionCode> <splitName> <outApk> [padBytes] [versionName]
source "$(dirname "${BASH_SOURCE[0]}")/../tools/env.sh"

PKG="${1:?package required}"
VC="${2:?versionCode required}"
SPLIT="${3:?split name required}"
OUT="${4:?output apk path required}"
PAD="${5:-0}"
VN="${6:-1.0}"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

[ -f "$KEYSTORE" ] || { echo "keystore missing; run build_scrobbler.sh first" >&2; exit 1; }

# split 属性は aapt2 の引数では渡せないのでマニフェストを生成する。
cat > "$TMP/AndroidManifest.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$PKG"
    split="$SPLIT">
    <application android:hasCode="false" />
</manifest>
XML

"$AAPT2" link \
    -o "$TMP/split.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$TMP/AndroidManifest.xml" \
    --min-sdk-version 21 \
    --target-sdk-version 28 \
    --version-code "$VC" \
    --version-name "$VN"

if [ "$PAD" -gt 0 ]; then
    head -c "$PAD" /dev/zero > "$TMP/pad.bin"
    mkdir -p "$TMP/assets"
    mv "$TMP/pad.bin" "$TMP/assets/pad.bin"
    (cd "$TMP" && zip -q -X -0 "$TMP/split.apk" assets/pad.bin)
fi

"$ZIPALIGN" -f 4 "$TMP/split.apk" "$TMP/aligned.apk"
# パスワードを引数に載せると実行中 ps から見えるので環境変数経由で渡す。
AKS_KS_PASS_FOR_SIGNER="$KS_PASS" "$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass env:AKS_KS_PASS_FOR_SIGNER \
    --key-pass env:AKS_KS_PASS_FOR_SIGNER \
    --ks-key-alias "$KS_ALIAS" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled false \
    --out "$OUT" "$TMP/aligned.apk"

echo "built split: $OUT ($(stat -c%s "$OUT") bytes, split=$SPLIT)"
