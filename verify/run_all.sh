#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
source tools/env.sh

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

echo "== 1/4 java scrobbler unit tests =="
mkdir -p "$T/scrobbler"
mapfile -t SCROBBLER_SRCS < <(find scrobbler/src scrobbler/test -name '*.java')
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -classpath "$ANDROID_JAR" \
     -d "$T/scrobbler" "${SCROBBLER_SRCS[@]}"
java -cp "$T/scrobbler:$ANDROID_JAR" ScrobblerTests

mkdir -p artifacts/verify

echo "== 2/4 APK builds and satisfies constraints =="
./build/build_scrobbler.sh fm.last.android 1 artifacts/verify/base.apk >/dev/null
python3 - <<'PY'
import sys; sys.path.insert(0, "verify")
from verify_apk import inspect
r = inspect("artifacts/verify/base.apk")
assert r["package"] == "fm.last.android", r["package"]
assert r["min_sdk"] == "21", r["min_sdk"]
assert r["target_sdk"] == "28", r["target_sdk"]
assert r["signed_v1"] and r["signed_v2"] and not r["signed_v3"], r
assert not r["requires_split"], "base must not require splits"
print("APK constraints OK")
PY

echo "== 3/4 XAPK packs and satisfies constraints =="
# 検証ゲートはリリース鍵を必要としない。make_release.sh はリリース署名鍵の
# 明示指定を要求するので、ここでは同じ経路（build_scrobbler + build_split +
# pack_xapk のパラメータ）を開発鍵で組み立てて検査する。
AKS_SKIP_SECRETS=1 ./build/build_scrobbler.sh \
    fm.last.android 1 artifacts/verify/base.apk 0.0.0-verify >/dev/null
AKS_SKIP_SECRETS=1 ./build/build_split.sh \
    fm.last.android 1 config.xxhdpi artifacts/verify/split-xxhdpi.apk 0 0.0.0-verify >/dev/null
python3 - <<'PACK'
import sys
sys.path.insert(0, "build")
from pack_xapk import build
build("artifacts/verify/akscrobble-0.0.0-verify.xapk", "fm.last.android", 1,
      [("base.apk", "artifacts/verify/base.apk"),
       ("config.xxhdpi.apk", "artifacts/verify/split-xxhdpi.apk")],
      style="plain", app_name="AKScrobble", version_name="0.0.0-verify")
PACK
python3 - <<'PY'
import json
import sys
sys.path.insert(0, "verify")
from verify_apk import inspect as inspect_apk
from verify_xapk import inspect as inspect_xapk

xapk_path = "artifacts/verify/akscrobble-0.0.0-verify.xapk"
xapk = inspect_xapk(xapk_path)
names = [entry["name"] for entry in xapk["entries"]]
assert xapk["apk_count"] == 2, xapk["apk_count"]
assert "manifest.json" in names, names
with __import__("zipfile").ZipFile(xapk_path) as archive:
    manifest = json.loads(archive.read("manifest.json"))
assert manifest["package_name"] == "fm.last.android", manifest
assert manifest["name"] == "AKScrobble", manifest
assert manifest["version_name"] == "0.0.0-verify", manifest

base = inspect_apk("artifacts/verify/base.apk")
assert base["version_name"] == "0.0.0-verify", base["version_name"]
print("XAPK constraints OK")
PY

# python の検証器テストは最後に回す。test_verify_apk.py が上でビルドした
# artifacts/verify/ の APK を fixture として使うため、先に走らせるとスキップされる。
echo "== 4/4 python verifier tests =="
python3 -m pytest verify -q

echo "ALL VERIFY PASS"
