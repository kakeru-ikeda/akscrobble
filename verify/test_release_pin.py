import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_release_pin_matches_build_certificate():
    source = (ROOT / "scrobbler/src/com/akscrobble/update/ReleasePin.java").read_text()
    match = re.search(r'SIGNER_SHA256\s*=\s*"([0-9a-f]+)"', source)
    assert match, "ReleasePin.SIGNER_SHA256 must be a lowercase hexadecimal string"
    pin = match.group(1)
    assert re.fullmatch(r"[0-9a-f]{64}", pin)
    assert pin == (ROOT / "build/release-cert.sha256").read_text().strip()
