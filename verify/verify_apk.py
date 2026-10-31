"""APK の manifest と署名を検証する。

androguard のログは既定で大量に出るため必ず抑止する。
"""
import hashlib
import sys

from loguru import logger

logger.remove()

from androguard.core.apk import APK  # noqa: E402

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def _manifest_root(apk):
    x = apk.get_android_manifest_xml()
    return x if x.tag == "manifest" else x.find(".//manifest")


def _requires_split(apk, root) -> bool:
    """split を要求するフラグが 1 つでも立っていれば True。

    3 つの経路がある。API 31+ の requiredSplitTypes、application の
    isSplitRequired、そして Play 用の meta-data。どれか 1 つでも立てば
    base 単体インストールは拒否される。
    """
    if root.get(ANDROID_NS + "requiredSplitTypes"):
        return True
    app = root.find("application")
    if app is not None and app.get(ANDROID_NS + "isSplitRequired") == "true":
        return True
    for md in root.iter("meta-data"):
        if md.get(ANDROID_NS + "name") == "com.android.vending.splits.required":
            if md.get(ANDROID_NS + "value") == "true":
                return True
    return False


def inspect(path: str) -> dict:
    apk = APK(path)
    root = _manifest_root(apk)
    return {
        "package": apk.get_package(),
        "version_code": apk.get_androidversion_code(),
        "version_name": apk.get_androidversion_name(),
        "min_sdk": apk.get_min_sdk_version(),
        "target_sdk": apk.get_target_sdk_version(),
        "split": root.get("split"),
        "signed_v1": apk.is_signed_v1(),
        "signed_v2": apk.is_signed_v2(),
        "signed_v3": apk.is_signed_v3(),
        "cert_sha256": [
            hashlib.sha256(c.dump()).hexdigest() for c in apk.get_certificates()
        ],
        "requires_split": _requires_split(apk, root),
        "permissions": sorted(apk.get_permissions()),
    }


def main(argv):
    if len(argv) < 2:
        print("usage: verify_apk.py <apk> [<apk> ...]", file=sys.stderr)
        return 2
    for p in argv[1:]:
        print(f"=== {p}")
        for k, v in inspect(p).items():
            print(f"  {k:14s} {v}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
