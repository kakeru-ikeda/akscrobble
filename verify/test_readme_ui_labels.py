"""README が参照する UI ラベルが、実際の文字列表と一致していることを検査する。

手順書でボタン名がずれると、利用者は存在しないボタンを探すことになる。
実際に英語版 README の 3 箇所（API credentials / Last.fm authorization /
Sanitize rules）が実装と食い違っていた。

規約: README 内の **太字** は UI 上のラベルを指す。UI ラベルでない太字は
ALLOWED に列挙する。言語ごとに文字列表の対応する列と突き合わせるので、
翻訳のずれも同時に捕まえられる。
"""
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TABLE = ROOT / "scrobbler/src/com/akscrobble/core/Strings.java"
# このリポジトリでは docs/release/ に、切り出した公開リポジトリではルートに置かれる。
RELEASE_DIRS = (ROOT / "docs/release", ROOT)
BOLD = re.compile(r"\*\*([^*\n]+)\*\*")
# {KEY, "ja", "en", "ko"} の 3 つの文字列リテラルを取り出す
ROW = re.compile(
    r'\{\s*\w+\s*,\s*"((?:\\.|[^"\\])*)"\s*,\s*"((?:\\.|[^"\\])*)"\s*,'
    r'\s*"((?:\\.|[^"\\])*)"\s*\}'
)
# UI ラベルではない太字はここに挙げる。強調と、Last.fm 側のフォーム項目名。
ALLOWED = {
    # 文中の強調
    "No root is required.",
    "root は不要です。",
    "root 권한이 필요하지 않습니다.",
    # last.fm の API アカウント作成フォームの項目名（AKScrobble の UI ではない）
    "Callback URL",
    "Application homepage",
    # 認証情報の渡し方を 2 通り示す小見出しと、その中の強調
    "Recommended: the configuration file.",
    "Alternative: type them in the app.",
    "推奨: 設定ファイル。",
    "代替: アプリ内で入力する。",
    "設定ファイルの方が圧倒的に楽です",
    "ボタンを押す必要はありません",
    "권장: 설정 파일.",
    "대체: 앱에서 직접 입력.",
    "설정 파일이 훨씬 편합니다",
    "버튼을 누를 필요가 없습니다",
}
COLUMN = {"README.md": 1, "README.ja.md": 0, "README.ko.md": 2}


def labels_for(column):
    values = set()
    for row in ROW.findall(TABLE.read_text(encoding="utf-8")):
        value = row[column].replace("\\n", "\n")
        for part in value.split("\n"):
            part = part.strip()
            if part:
                values.add(part)
    return values


def test_readme_bold_spans_are_real_ui_labels():
    problems = []
    for name, column in COLUMN.items():
        path = next((d / name for d in RELEASE_DIRS if (d / name).is_file()), None)
        if path is None:
            continue          # まだ書かれていない翻訳はここでは咎めない
        known = labels_for(column)
        for span in BOLD.findall(path.read_text(encoding="utf-8")):
            span = span.strip()
            if span in ALLOWED or span in known:
                continue
            problems.append(f"{name}: **{span}** is not a UI label in Strings.java")
    assert not problems, (
        "README references labels that do not exist in the string table:\n"
        + "\n".join(problems)
        + "\n(UI ラベルでない強調は ALLOWED に追加すること)"
    )
