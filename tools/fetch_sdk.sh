#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CMDLINE_TOOLS_URL="${CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip}"
SDK_ROOT="$PROJECT_ROOT/tools/android-sdk"
ARCHIVE="$PROJECT_ROOT/tools/cmdline-tools.zip"
SDK_TMP="$PROJECT_ROOT/tools/sdk-tmp"

if [ -x "$SDK_ROOT/build-tools/34.0.0/aapt2" ] && \
   [ -f "$SDK_ROOT/platforms/android-28/android.jar" ]; then
    echo "Android SDK already installed"
    exit 0
fi

for command_name in curl unzip java; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "required command not found: $command_name" >&2
        exit 1
    fi
done

rm -rf "$SDK_TMP"
mkdir -p "$SDK_TMP"
curl -fsSL "$CMDLINE_TOOLS_URL" -o "$ARCHIVE"
unzip -q "$ARCHIVE" -d "$SDK_TMP"

yes | "$SDK_TMP/cmdline-tools/bin/sdkmanager" \
    --sdk_root="$SDK_ROOT" \
    "build-tools;34.0.0" \
    "platforms;android-28"

rm -rf "$SDK_TMP"
rm -f "$ARCHIVE"

# shellcheck source=env.sh
source "$PROJECT_ROOT/tools/env.sh"
echo "Android SDK installed successfully"
