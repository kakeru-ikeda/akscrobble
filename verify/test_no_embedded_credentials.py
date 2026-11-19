from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_embedded_credentials_removed():
    for directory in (ROOT / "scrobbler", ROOT / "build"):
        for path in directory.rglob("*"):
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            assert "BuildSecrets" not in text, path
            assert "LastfmSig" not in text, path

    assert not (ROOT / "build/secrets.env.example").exists()

    for relative in ("build/build_scrobbler.sh", "build/make_release.sh"):
        text = (ROOT / relative).read_text(encoding="utf-8")
        assert "secrets.env" not in text, relative
        assert "AKS_SKIP_SECRETS" not in text, relative

    request = ROOT / "scrobbler/src/com/akscrobble/core/LastfmRequest.java"
    assert "secret" not in request.read_text(encoding="utf-8").lower()
