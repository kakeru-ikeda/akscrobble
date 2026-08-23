#!/usr/bin/env bash
# 全ビルドスクリプトが source する唯一のパス定義。
# 各スクリプトに SDK パスをハードコードしないこと。
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PROJECT_ROOT

export ANDROID_SDK_ROOT="$PROJECT_ROOT/tools/android-sdk"
export BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/34.0.0"
export ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-28/android.jar"

export AAPT2="$BUILD_TOOLS/aapt2"
export D8="$BUILD_TOOLS/d8"
export APKSIGNER="$BUILD_TOOLS/apksigner"
export ZIPALIGN="$BUILD_TOOLS/zipalign"

# 開発鍵は自動生成される使い捨て。リリース鍵は AKS_KEYSTORE と AKS_KS_PASS で
# 明示的に指定する（make_release.sh が未指定を拒否する）。開発用の既定
# パスワードはこのファイルに書いてあり公開されるので、リリース署名に
# 使ってはいけない。
export KEYSTORE="${AKS_KEYSTORE:-$PROJECT_ROOT/build/akscrobble-dev.keystore}"
export KS_PASS="${AKS_KS_PASS:-akscrobble-dev}"
export KS_ALIAS="${AKS_KS_ALIAS:-akscrobble}"

for _t in "$AAPT2" "$D8" "$APKSIGNER" "$ZIPALIGN" "$ANDROID_JAR"; do
    [ -e "$_t" ] || { echo "missing tool: $_t" >&2; exit 1; }
done
