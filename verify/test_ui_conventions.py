"""Guard the shared UI helper and new-screen manifest conventions."""
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ACTIVITIES = sorted((ROOT / "scrobbler/src/com/akscrobble").glob("*Activity.java"))
MANIFEST = ROOT / "scrobbler/AndroidManifest.xml"


def test_activities_use_shared_ui_helpers():
    pattern = re.compile(r"private\s+\w+\s+(addButton|button|text|input|editText)\s*\(")
    problems = [
        f"{path.relative_to(ROOT)} defines a local UI helper"
        for path in ACTIVITIES
        if pattern.search(path.read_text(encoding="utf-8"))
    ]
    assert not problems, "\n".join(problems)


def test_activities_do_not_hardcode_colors():
    pattern = re.compile(r"Color\.(?:BLACK|WHITE)|setBackgroundColor\(Color\.")
    problems = [
        f"{path.relative_to(ROOT)} hard-codes a color"
        for path in ACTIVITIES
        if pattern.search(path.read_text(encoding="utf-8"))
    ]
    assert not problems, "\n".join(problems)


def test_new_screens_registered():
    manifest = MANIFEST.read_text(encoding="utf-8")
    for activity in ("SetupActivity", "SettingsActivity"):
        assert re.search(
            rf'<activity\b(?=[^>]*android:name="[^"]*{activity}")'
            rf'(?=[^>]*android:exported="false")[^>]*>',
            manifest,
        ), f"{activity} must be registered with android:exported=\"false\""
    assert re.search(r'<application\b(?=[^>]*android:theme="@style/AKS\.Theme")[^>]*>', manifest), (
        '<application> must use android:theme="@style/AKS.Theme"'
    )
