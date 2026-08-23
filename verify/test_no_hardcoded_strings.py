import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "scrobbler/src/com/akscrobble"
PENDING = ()
STRING_LITERAL = re.compile(r'"(?:\\.|[^"\\])*"')
JAPANESE = re.compile(r"[぀-ヿ一-鿿]")


def test_pending_files_exist():
    missing = [name for name in PENDING if not (SOURCE / name).is_file()]
    assert not missing, "PENDING files do not exist: " + ", ".join(missing)


def test_no_hardcoded_japanese_ui_strings():
    violations = []
    for path in sorted(SOURCE.glob("*.java")):
        if path.name in PENDING:
            continue
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            literals = STRING_LITERAL.findall(line)
            if any(JAPANESE.search(literal) for literal in literals):
                violations.append(f"{path.name}:{line_number}:{line}")
    assert not violations, "Hard-coded Japanese UI strings:\n" + "\n".join(violations)


MANIFEST = ROOT / "scrobbler/AndroidManifest.xml"
ACTIVITY = re.compile(r'<activity\b[^>]*?android:name="com\.akscrobble\.(\w+)"', re.S)
# MainActivity のタイトルはアプリ名 "AKScrobble" であり、意図的に翻訳しない。
TITLE_EXEMPT = {"MainActivity"}


def test_every_activity_localizes_its_title():
    """manifest の android:label は固定文字列なので、各画面は setTitle で言語に合わせる。

    これが無いと画面で最も目立つタイトルバーだけ英語のまま残る（実機で 2 回作り込んだ）。
    """
    names = set(ACTIVITY.findall(MANIFEST.read_text(encoding="utf-8"))) - TITLE_EXEMPT
    assert names, "no activities found in the manifest"
    missing = []
    for name in sorted(names):
        path = SOURCE / f"{name}.java"
        if not path.is_file():
            missing.append(f"{name}: source file not found")
        elif "setTitle(Ui.s(" not in path.read_text(encoding="utf-8"):
            missing.append(f"{name}: no setTitle(Ui.s(...)) call")
    assert not missing, "activities without a localized title:\n" + "\n".join(missing)
