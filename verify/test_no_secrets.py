import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SECRET = re.compile(r"(?i)(key|secret|token|sk)\s*=\s*[\"']?[0-9a-f]{32}")


def test_no_committed_secrets():
    violations = []
    for directory in (ROOT / "scrobbler", ROOT / "build"):
        for path in sorted(directory.rglob("*")):
            if not path.is_file() or path.name == "secrets.env":
                continue
            try:
                lines = path.read_text(encoding="utf-8").splitlines()
            except (UnicodeDecodeError, OSError):
                continue
            for number, line in enumerate(lines, 1):
                if SECRET.search(line):
                    violations.append(f"{path.relative_to(ROOT)}:{number}")
    assert not violations, "possible embedded secrets:\n" + "\n".join(violations)
