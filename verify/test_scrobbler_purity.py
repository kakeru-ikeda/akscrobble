from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "scrobbler/src/com/akscrobble/core"
TESTS = ROOT / "scrobbler/test"


def java_files(directory):
    return sorted(directory.rglob("*.java"))


def locations_with(path, predicate):
    return [
        f"{path.relative_to(ROOT)}:{number}: {line.strip()}"
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1)
        if predicate(line)
    ]


def test_core_has_no_android_references():
    files = java_files(CORE)
    assert files, f"no Java files found under {CORE.relative_to(ROOT)}"
    violations = []
    for path in files:
        violations.extend(locations_with(path, lambda line: "android." in line))
    assert not violations, "core Android references:\n" + "\n".join(violations)


def test_tests_only_import_core_classes():
    violations = []
    for path in java_files(TESTS):
        violations.extend(
            locations_with(
                path,
                lambda line: line.lstrip().startswith("import com.akscrobble.")
                and not line.lstrip().startswith("import com.akscrobble.core."),
            )
        )
    assert not violations, "non-core Scrobbler test imports:\n" + "\n".join(violations)
