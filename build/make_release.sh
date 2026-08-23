#!/usr/bin/env bash
# Build release APK and XAPK artifacts without embedded API credentials.
#   usage: make_release.sh <versionCode> <versionName>
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../tools/env.sh"

VC="${1:?versionCode required}"
VN="${2:?versionName required}"

# リリースは必ず明示指定した鍵で署名する。tools/env.sh の開発用フォールバックは
# パスワードが公開リポジトリに載っているため、配布物の署名に使ってはいけない。
# 署名鍵が変わると既存インストールを上書きできなくなるので、一度決めた鍵を使い続けること。
: "${AKS_KEYSTORE:?release signing requires AKS_KEYSTORE (path to the release keystore)}"
: "${AKS_KS_PASS:?release signing requires AKS_KS_PASS (release keystore password)}"
if [ ! -f "$AKS_KEYSTORE" ]; then
    echo "release keystore not found: $AKS_KEYSTORE" >&2
    echo "create it once with:" >&2
    echo "  keytool -genkeypair -keystore \"\$AKS_KEYSTORE\" -alias akscrobble \\" >&2
    echo "    -storepass \"\$AKS_KS_PASS\" -keypass \"\$AKS_KS_PASS\" \\" >&2
    echo "    -keyalg RSA -keysize 2048 -validity 10000 \\" >&2
    echo "    -dname 'CN=AKScrobble, O=AKScrobble, C=JP'" >&2
    exit 1
fi
OUT="$PROJECT_ROOT/artifacts/release"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

BASE="$OUT/akscrobble-$VN.apk"
SPLIT="$TMP/split-xxhdpi.apk"
XAPK="$OUT/akscrobble-$VN.xapk"

AKS_SKIP_SECRETS=1 "$PROJECT_ROOT/build/build_scrobbler.sh" \
    fm.last.android "$VC" "$BASE" "$VN"
AKS_SKIP_SECRETS=1 "$PROJECT_ROOT/build/build_split.sh" \
    fm.last.android "$VC" config.xxhdpi "$SPLIT" 0 "$VN"

python3 - "$PROJECT_ROOT" "$XAPK" "$VC" "$VN" "$BASE" "$SPLIT" <<'PY'
import sys

project_root, out_path, version_code, version_name, base, split = sys.argv[1:]
sys.path.insert(0, project_root + "/build")
from pack_xapk import build

build(
    out_path,
    "fm.last.android",
    int(version_code),
    [("base.apk", base), ("config.xxhdpi.apk", split)],
    style="plain",
    app_name="AKScrobble",
    version_name=version_name,
)
PY

if [ -f "$PROJECT_ROOT/build/secrets.env" ]; then
    (
        LASTFM_API_KEY=""
        LASTFM_API_SECRET=""
        source "$PROJECT_ROOT/build/secrets.env"
        python3 - "$BASE" "${LASTFM_API_KEY:-}" "${LASTFM_API_SECRET:-}" <<'PY'
import sys

apk_path, api_key, api_secret = sys.argv[1:]
apk = open(apk_path, "rb").read()
for label, value in (("LASTFM_API_KEY", api_key), ("LASTFM_API_SECRET", api_secret)):
    if value and value.encode() in apk:
        raise SystemExit(f"secret leaked into release APK: {label}")
PY
    )
fi

echo "built: $BASE ($(stat -c%s "$BASE") bytes)"
echo "built: $XAPK ($(stat -c%s "$XAPK") bytes)"
