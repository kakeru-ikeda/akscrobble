import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def run(*args):
    return subprocess.run(
        ["bash", "build/ci_version_code.sh", *args],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )


def test_calculates_version_code_in_decimal():
    for version, expected in {
        "1.0.2": "1000002",
        "1.0.3": "1000003",
        "1.1.0": "1001000",
        "2099.999.999": "2099999999",
        "1.0.09": "1000009",
        "0001.000.0009": "1000009",
    }.items():
        result = run(version)
        assert result.returncode == 0, result.stderr
        assert result.stdout == expected + "\n"


def test_rejects_invalid_versions_and_missing_argument():
    for version in (
        "0.1.0", "2100.0.0", "1.1000.0", "1.0.1000", "v1.0.2",
        "1.0", "1.0.2-rc1", "", "999999999999999999999999.0.0",
    ):
        result = run(version)
        assert result.returncode == 2, (version, result.stdout, result.stderr)
        assert result.stderr.strip()
    result = run()
    assert result.returncode == 2
    assert result.stderr.strip()
