import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_release_workflow_checks_certificate_and_cleans_up_key():
    workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
    assert "release-cert.sha256" in workflow
    assert "--print-certs" in workflow
    lines = workflow.splitlines()
    cleanup_blocks = []
    for index, line in enumerate(lines):
        if "if: always()" in line:
            end = next(
                (i for i in range(index + 1, len(lines))
                 if re.match(r"\s+- (?:name|uses):", lines[i])),
                len(lines),
            )
            cleanup_blocks.append("\n".join(lines[index:end]))
    assert len(cleanup_blocks) == 1
    assert "rm -rf" in cleanup_blocks[0]
    assert "secrets: inherit" not in workflow
    assert "set -x" not in workflow
    assert "nyanstall-publish.yml@v1" in workflow
    assert "nyanstall-key@v1" in workflow


def test_release_certificate_pin_is_one_lowercase_sha256_line():
    pin = (ROOT / "build/release-cert.sha256").read_bytes()
    assert re.fullmatch(rb"[0-9a-f]{64}\n", pin)
