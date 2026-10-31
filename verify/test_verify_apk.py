import os
import pytest
from lxml import etree
from verify_apk import ANDROID_NS, _requires_split, inspect

# 検証ゲートが自前でビルドする成果物を fixture として使う。
# もともとは work/ に置いた第三者 APK の実測値を見ていたが、work/ は
# 追跡対象外なので、クリーンなツリーでは 8 件すべてが黙ってスキップされて
# いた（実測で確認）。自前の成果物なら誰の環境でも再現できる。
ARTIFACTS = os.path.join(os.path.dirname(__file__), "..", "artifacts", "verify")
BASE = os.path.join(ARTIFACTS, "base.apk")
SPLIT = os.path.join(ARTIFACTS, "split-xxhdpi.apk")


@pytest.fixture
def built_apks():
    """run_all.sh が先にビルドした APK。単体で pytest を回したときだけスキップする。"""
    for path in (BASE, SPLIT):
        if not os.path.exists(path):
            pytest.skip(f"run ./verify/run_all.sh first; missing {path}")
    return BASE, SPLIT


def test_inspect_reads_base_apk_values(built_apks):
    """自前でビルドした base APK の値を読めること。"""
    base, _ = built_apks
    r = inspect(base)
    assert r["package"] == "fm.last.android"
    assert r["min_sdk"] == "21"
    assert r["target_sdk"] == "28"
    assert r["signed_v1"] is True
    assert r["signed_v2"] is True
    assert r["signed_v3"] is False
    assert r["split"] is None
    assert r["requires_split"] is False


def test_built_base_apk_declares_uses_permissions(built_apks):
    """Qobuz は権限宣言のない呼び出し元を PackageValidator で拒否しクラッシュする。"""
    base, _ = built_apks
    permissions = inspect(base)["permissions"]
    assert permissions
    assert "android.permission.INTERNET" in permissions


def test_inspect_detects_split_name(built_apks):
    """config split の split 属性を読めること。"""
    _, split = built_apks
    r = inspect(split)
    assert r["split"] == "config.xxhdpi"
    assert r["package"] == "fm.last.android"


def test_inspect_reports_certificate_fingerprints(built_apks):
    """base と split の証明書が一致していることを検出できること。"""
    base, split = built_apks
    a = inspect(base)
    b = inspect(split)
    assert len(a["cert_sha256"]) == 1
    assert a["cert_sha256"] == b["cert_sha256"]


def _manifest(inner: str = "") -> etree._Element:
    """android 名前空間を宣言した最小限の manifest 要素を組み立てる。"""
    xml = f'<manifest xmlns:android="{ANDROID_NS[1:-1]}">{inner}</manifest>'
    return etree.fromstring(xml.encode())


def test_requires_split_true_for_required_split_types_alone():
    """経路1単独: manifest の requiredSplitTypes だけで True になること。"""
    root = _manifest()
    root.set(ANDROID_NS + "requiredSplitTypes", "base__abi,base__density")
    assert _requires_split(None, root) is True


def test_requires_split_true_for_is_split_required_alone():
    """経路2単独: application の isSplitRequired だけで True になること。"""
    root = _manifest('<application/>')
    root.find("application").set(ANDROID_NS + "isSplitRequired", "true")
    assert _requires_split(None, root) is True


def test_requires_split_true_for_vending_meta_data_alone():
    """経路3単独: Play 用 meta-data だけで True になること。"""
    root = _manifest('<application><meta-data/></application>')
    md = root.find("application/meta-data")
    md.set(ANDROID_NS + "name", "com.android.vending.splits.required")
    md.set(ANDROID_NS + "value", "true")
    assert _requires_split(None, root) is True


def test_requires_split_false_when_none_of_the_three_present():
    """3経路とも存在しなければ False になること。"""
    root = _manifest('<application/>')
    assert _requires_split(None, root) is False
