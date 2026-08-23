import os
import subprocess
import sys
import zipfile

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "build"))
from pack_xapk import build  # noqa: E402
from verify_xapk import inspect  # noqa: E402


@pytest.fixture
def apks(tmp_path):
    base = tmp_path / "base.apk"
    split = tmp_path / "config.xxhdpi.apk"
    base.write_bytes(b"BASE" * 256)
    split.write_bytes(b"SPLIT" * 128)
    return [("base.apk", str(base)), ("config.xxhdpi.apk", str(split))]


def test_plain_style_has_two_apk_entries(tmp_path, apks):
    """判定ルールの要。書庫内の .apk が 2 個であること。"""
    out = str(tmp_path / "c1.xapk")
    build(out, "com.now.moov", 1, apks, style="plain")
    r = inspect(out)
    assert r["apk_count"] == 2


def test_plain_style_includes_manifest_json(tmp_path, apks):
    out = str(tmp_path / "c1.xapk")
    build(out, "com.now.moov", 1, apks, style="plain")
    names = [e["name"] for e in inspect(out)["entries"]]
    assert "manifest.json" in names


def test_manifest_json_declares_all_apks(tmp_path, apks):
    import json

    out = str(tmp_path / "c1.xapk")
    build(out, "com.now.moov", 1, apks, style="plain")
    with zipfile.ZipFile(out) as z:
        m = json.loads(z.read("manifest.json"))
    assert m["package_name"] == "com.now.moov"
    assert m["name"] == "AKScrobble"
    assert len(m["split_apks"]) == 2
    assert m["split_apks"][0]["id"] == "base"


def test_manifest_json_accepts_app_and_version_names(tmp_path, apks):
    import json

    out = str(tmp_path / "custom.xapk")
    build(
        out,
        "com.now.moov",
        1,
        apks,
        app_name="X",
        version_name="9.9",
    )
    with zipfile.ZipFile(out) as z:
        manifest = json.loads(z.read("manifest.json"))
    assert manifest["name"] == "X"
    assert manifest["version_name"] == "9.9"


def test_apkpure_style_is_all_stored(tmp_path, apks):
    """実測した APKPure の XAPK は全エントリ STORED。"""
    out = str(tmp_path / "c3.xapk")
    build(out, "com.now.moov", 1, apks, style="apkpure")
    r = inspect(out)
    assert r["all_stored"] is True


def test_apkpure_style_uses_data_descriptors(tmp_path, apks):
    """汎用フラグ 0x0008 が全エントリに立っていること。"""
    out = str(tmp_path / "c3.xapk")
    build(out, "com.now.moov", 1, apks, style="apkpure")
    assert inspect(out)["all_data_descriptor"] is True


def test_apkpure_style_puts_manifest_last(tmp_path, apks):
    out = str(tmp_path / "c3.xapk")
    build(out, "com.now.moov", 1, apks, style="apkpure")
    names = [e["name"] for e in inspect(out)["entries"]]
    assert names[-1] == "manifest.json"


def test_apkpure_style_is_readable_by_stdlib(tmp_path, apks):
    """自作 ZIP ライタが壊れた書庫を作っていないこと。"""
    out = str(tmp_path / "c3.xapk")
    build(out, "com.now.moov", 1, apks, style="apkpure")
    with zipfile.ZipFile(out) as z:
        assert z.testzip() is None
        with open(apks[0][1], "rb") as base:
            assert z.read("base.apk") == base.read()


def test_unzip_agrees(tmp_path, apks):
    """外部の unzip でも読めること。Python 実装だけの都合になっていないこと。"""
    out = str(tmp_path / "c3.xapk")
    build(out, "com.now.moov", 1, apks, style="apkpure")
    r = subprocess.run(["unzip", "-t", out], capture_output=True, text=True)
    assert r.returncode == 0, r.stdout + r.stderr
