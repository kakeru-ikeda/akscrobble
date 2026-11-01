"""Qobuz は uses-permission がない呼び出し元を見つけられずクラッシュする。

PackageValidator は ``IllegalStateException("Caller wasn't found in the system?")``
を投げるため、権限宣言を維持する。詳細は docs/DEVICE-NOTES.md を参照。
"""
from pathlib import Path
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "scrobbler/AndroidManifest.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def test_manifest_declares_at_least_one_uses_permission():
    """権限宣言がないと Qobuz の PackageValidator が例外を投げクラッシュする。"""
    root = ET.parse(MANIFEST).getroot()
    permissions = root.findall("uses-permission")
    assert permissions, (
        'Qobuz の PackageValidator は uses-permission を 1 つも宣言しない呼び出し元に対して '
        'IllegalStateException("Caller wasn\'t found in the system?") を投げ、'
        "Qobuz のプロセスがクラッシュする"
    )


def test_manifest_keeps_internet_permission():
    """Last.fm への通信に必要な INTERNET 権限を維持する。"""
    root = ET.parse(MANIFEST).getroot()
    names = {node.get(ANDROID_NS + "name") for node in root.findall("uses-permission")}
    assert "android.permission.INTERNET" in names


def test_manifest_declares_install_packages_permission():
    root = ET.parse(MANIFEST).getroot()
    names = {node.get(ANDROID_NS + "name") for node in root.findall("uses-permission")}
    assert "android.permission.REQUEST_INSTALL_PACKAGES" in names


def test_manifest_declares_private_install_result_receiver():
    root = ET.parse(MANIFEST).getroot()
    receivers = root.findall("application/receiver")
    assert any(node.get(ANDROID_NS + "name") == "com.akscrobble.update.InstallResultReceiver"
               and node.get(ANDROID_NS + "exported") == "false" for node in receivers)
