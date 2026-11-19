"""Guard the external-browser Last.fm authorization flow."""
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "scrobbler/src/com/akscrobble/AuthActivity.java"
STRINGS = ROOT / "scrobbler/src/com/akscrobble/core/Strings.java"
README_FILES = ("README.md", "README.ja.md", "README.ko.md")


def test_auth_activity_uses_copy_and_manual_session_flow():
    source = ACTIVITY.read_text(encoding="utf-8")
    assert "WebView" not in source
    assert "WebSettings" not in source
    assert "CheckBox" not in source
    assert source.count("body.addView(Ui.button(") == 2
    assert "Strings.AUTH_COPY_APPROVAL_URL" in source
    assert "Strings.AUTH_GET_SESSION" in source
    assert "!blank(token) && !blank(approvalUrl)" in source
    assert "if (tokenRequestInProgress) return;" in source
    assert "requestSession(true)" not in source


def test_auth_strings_replace_webview_copy_with_localized_labels():
    source = STRINGS.read_text(encoding="utf-8")
    for obsolete in ("AUTH_OPEN_APPROVAL", "AUTH_SHOW_PC_URL", "AUTH_USE_NEW_UA",
                     "AUTH_WEBVIEW_UNAVAILABLE", "AUTH_OPEN_ON_PC"):
        assert obsolete not in source
    assert re.search(
        r'\{AUTH_COPY_APPROVAL_URL,\s*"認証 URL をコピー",\s*'
        r'"Copy authorization URL",\s*"인증 URL 복사"\}', source
    )
    assert "AUTH_AUTHORIZATION_INSTRUCTIONS" in source


def test_readmes_document_external_browser_steps_without_webview_terms():
    for name in README_FILES:
        content = (ROOT / name).read_text(encoding="utf-8").lower()
        assert "webview" not in content
        assert "browser ua" not in content
        assert "auth" in content or "認証" in content or "인증" in content
