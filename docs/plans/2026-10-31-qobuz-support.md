# Qobuz (com.qobuz.music) 対応

## Open questions

1. **bind で Qobuz プロセスが起動する副作用は許容するか。** `MediaBrowser.connect()` は
   `BIND_AUTO_CREATE` で bind するため、AKScrobble のサービス起動時 (端末起動直後を含む) に
   Qobuz が停止していても Qobuz のプロセスが立ち上がる見込みが高い。手動 E2E の E1 で
   確認する。許容できない場合は「Qobuz が起動済みのときだけ接続する」制御が別途必要だが、
   この計画の範囲外 (別計画にする)。
2. **Task 3 (PlayTracker の不具合修正) をこの計画に含めるか。** 調査中に、PLAYING 以外の
   状態 (BUFFERING=6、PAUSED=2) から `PLAYING, position<3000` に戻ったときにリピート検出が
   働くと、再開した曲の再生時間が積算されない既存の不具合を見つけた (Design notes 参照)。
   Qobuz では曲間に BUFFERING が挟まるので踏む可能性が高いが、AK Music / Apple Music でも
   一時停止→先頭へシーク→再生で起きる。最小変更を優先するなら Task 3 を外してよい
   (その場合も Task 1, 2, 4 は成立する)。
3. **同じ署名鍵で上書きインストールできるか。** 実機に入っているビルドのリリース鍵
   (`AKS_KEYSTORE` / `AKS_KS_PASS`) が手元にあるか確認すること。ない場合はアンインストールが
   必要になり、doze ホワイトリスト、ストレージ権限、セッションキーが失われる
   (docs/DEVICE-NOTES.md「Uninstall side effects」)。アンインストールするかどうかは
   ユーザーが判断する。
4. バージョン番号の更新とリリース (`build/make_release.sh`) はこの計画に含めない想定でよいか。

## Goal

Qobuz 10.1.2.0 (`com.qobuz.music`) の再生を、Apple Music / AK Music と同じ経路
(PlayerLink: MediaBrowser → session token → MediaController → PlayTracker) で
Last.fm へ updateNowPlaying / scrobble できるようにする。あわせて、Qobuz の
呼び出し元検証によるクラッシュを防ぐ前提条件 (マニフェストに `uses-permission` が
1 つ以上あること) を検証ゲートで固定し、ドキュメントを「検証済み」に更新する。

## Non-goals

- Spotify 対応 (未検証のまま)。
- ISRC (`com.qobuz.mobile.TRACK_ISRC`) などの Qobuz 独自メタデータキーの利用。
- Qobuz 用のアルバム補完。Qobuz の metadata は ALBUM を含むので、`AlbumResolver` /
  `ScrobbleService.enrichAkAlbum` (AK Music 専用) は変更しない。ALBUM_ARTIST キーは
  存在しないので albumArtist は null のまま送信する (Last.fm 上は問題ない)。
- Qobuz が停止中に bind しない制御 (Open question 1)。
- PlayerLink の変更。Qobuz の MEDIA_ID は UUID なので `Long.parseLong` が失敗し
  mediaId=-1 になるが、これは想定どおりの動作。
- バージョン番号の更新、リリース作業。

## Design notes

- **定数の書き方 (検証ゲートの規約)。** `verify/test_scrobbler_purity.py` の
  `test_core_has_no_android_references` は、`scrobbler/src/com/akscrobble/core/` 配下の
  全行 (コメントを含む) について部分文字列 `android.` の有無を検査する。`PKG_APPLE` が
  `"com.apple." + "android" + ".music"` と分割されているのはこのため。Qobuz の
  パッケージ名 `com.qobuz.music` は該当しないのでそのまま書ける。サービス名
  `com.qobuz.android.mobile.component.mediaplayer.MusicService` は `qobuz.android.mobile`
  を含むので、次のように分割する。
  ```java
  public static final String PKG_QOBUZ = "com.qobuz.music";
  public static final String SVC_QOBUZ =
          "com.qobuz." + "android" + ".mobile.component.mediaplayer.MusicService";
  ```
  サービスのパッケージ (`com.qobuz.android...`) はアプリのパッケージ (`com.qobuz.music`) と
  異なるので、`PKG_QOBUZ + "..."` とは書かない。Const.java に追加するコメントにも
  `android` の直後に `.` を続けて書いてはいけない。`verify/test_no_hardcoded_strings.py`
  が検査するのは `scrobbler/src/com/akscrobble/*.java` 内の日本語文字列リテラルだけなので、
  ASCII の定数は影響を受けない。テストコード (`scrobbler/test/`) は purity 検査の対象外だが、
  `com.akscrobble.core.` 以外の import は禁止されている。
- **statusKey。** `ScrobbleService.statusKey()` は StatusBus のキー接尾辞を返し、
  未知パッケージではパッケージ名をそのまま返す。表示の一貫性のため `"qobuz"` を追加する。
  StatusBus のキーを個別に参照する UI は存在しない (`MainActivity` は
  `StatusBus.snapshotText()` を表示するだけ) ので、ほかに変更は不要。
- **TARGETS の順序。** 既存の 2 件の後ろへ Qobuz を追加する。ScrobbleService.onCreate は
  TARGETS ごとに PlayTracker と PlayerLink を作るため、これだけで接続と追跡が有効になる。
- **uses-permission の回帰防止。** Qobuz の PackageValidator は、`uses-permission` を
  1 つも宣言していない呼び出し元に対して onGetLibraryRoot で
  `IllegalStateException("Caller wasn't found in the system?")` を投げ、**Qobuz の
  プロセスがクラッシュする**。PlayerLink は失敗時に再試行するので、権限を消すと
  Qobuz がクラッシュを繰り返す。そこで次の 2 段で固定する: (a) ソースの
  `scrobbler/AndroidManifest.xml` を静的に検査する pytest、(b) 検証ゲートがビルドした
  `artifacts/verify/base.apk` の権限を androguard で検査する pytest。(b) だけだと、
  単体で pytest を実行したときにスキップされるため (a) も置く。
- **PlayTracker の不具合 (Task 3)。** `onPlaybackState` のリピート検出分岐は
  `finish(); start(repeated, ...)` を呼ぶが、`start()` は
  `playingSinceElapsed = lastState == STATE_PLAYING ? elapsedMs : -1L` で再生中かどうかを
  判断し、`lastState` はその後で更新される。直前の状態が PLAYING 以外 (Qobuz の
  BUFFERING=6 や PAUSED=2) だと、再開後の曲は「再生中なのに積算されない」状態になり、
  2 回目の再生が scrobble されない。修正方針は、リピート分岐で `start()` を呼ぶ前に
  `lastState = state;` を代入すること (`state` はこの分岐では必ず STATE_PLAYING)。
  却下した案: Const に STATE_BUFFERING を追加して BUFFERING を再生中とみなす案。
  バッファリング中の時間まで積算してしまい、スキップ操作の多い利用者で閾値に早く
  到達するため却下した。
- **E2E は手動。** ワーカーはネットワークも実機もないサンドボックスで動くため、実機での
  確認は「Manual device verification」節としてオーケストレーター/ユーザーが実施する。
  Task 4 (ドキュメントで「検証済み」と書く作業) は、この手動 E2E に合格してから委譲する。
- **README の太字。** `verify/test_readme_ui_labels.py` は README 内の `**太字**` が
  Strings.java の UI ラベルと一致することを要求する。ドキュメントの追記では太字を使わない。

### Task 1: Guard against removing every uses-permission from the manifest

- Files to touch:
  - `verify/test_manifest_permissions.py` (新規)
  - `verify/verify_apk.py`
  - `verify/test_verify_apk.py`
- Files NOT to touch: `scrobbler/**`, `build/**`, `verify/run_all.sh`, `README*.md`, `docs/**`
- New dependencies: none (androguard, lxml, pytest は導入済み)
- Steps:
  1. `verify/test_manifest_permissions.py` を新規作成する。`ROOT = Path(__file__).resolve().parents[1]`、
     `MANIFEST = ROOT / "scrobbler/AndroidManifest.xml"` とし、標準ライブラリの
     `xml.etree.ElementTree` で解析する。名前空間は
     `{http://schemas.android.com/apk/res/android}`。
     - `test_manifest_declares_at_least_one_uses_permission()`: ルート直下の
       `uses-permission` 要素が 1 つ以上あることを assert する。失敗メッセージには
       「Qobuz の PackageValidator は uses-permission を 1 つも宣言しない呼び出し元に対して
       IllegalStateException("Caller wasn't found in the system?") を投げ、Qobuz の
       プロセスがクラッシュする」旨を含める。
     - `test_manifest_keeps_internet_permission()`: `android:name` の集合に
       `android.permission.INTERNET` が含まれることを assert する。
     - モジュール docstring に、上記の Qobuz の罠と docs/DEVICE-NOTES.md への参照を
       日本語で書く (既存の verify/*.py と同じ文体)。
  2. `verify/verify_apk.py` の `inspect()` が返す dict に
     `"permissions": sorted(apk.get_permissions())` を追加する (`requires_split` の後ろ)。
     ほかのキーは変更しない。
  3. `verify/test_verify_apk.py` に
     `test_built_base_apk_declares_uses_permissions(built_apks)` を追加する。既存の
     `built_apks` fixture を使い、`inspect(base)["permissions"]` が空でないこと、および
     `"android.permission.INTERNET"` を含むことを assert する。docstring に Qobuz の
     クラッシュ理由を 1 行で書く。
- Acceptance:
  - `scrobbler/AndroidManifest.xml` からすべての `<uses-permission>` を一時的に消すと
    `python3 -m pytest verify/test_manifest_permissions.py -q` が失敗する。元に戻すと成功する
    (ワーカーは一時変更を必ず元に戻すこと。最終的な差分に manifest の変更を含めない)。
  - `./verify/run_all.sh` が `ALL VERIFY PASS` で終わり、新しい 3 件のテストが
    スキップされずに成功する。
- Verify: `python3 -m pytest verify/test_manifest_permissions.py -q && ./verify/run_all.sh`

### Task 2: Add Qobuz as a player target

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/Const.java`
  - `scrobbler/src/com/akscrobble/ScrobbleService.java`
  - `scrobbler/test/ScrobblerTests.java`
- Files NOT to touch: `scrobbler/src/com/akscrobble/PlayerLink.java`,
  `scrobbler/src/com/akscrobble/AlbumResolver.java`, `scrobbler/src/com/akscrobble/core/PlayTracker.java`,
  `scrobbler/AndroidManifest.xml`, `verify/**`, `README*.md`, `docs/**`
- New dependencies: none
- Steps:
  1. `Const.java`: `SVC_AKMUSIC` の直後に次の 2 定数を追加する (文字列は 1 文字も
     変えないこと)。
     ```java
     public static final String PKG_QOBUZ = "com.qobuz.music";
     public static final String SVC_QOBUZ =
             "com.qobuz." + "android" + ".mobile.component.mediaplayer.MusicService";
     ```
     分割の理由をコメントで添える場合は、`android` の直後に `.` を続けて書かないこと
     (例: 「core では android とドットの連続を禁止しているため分割する
     (verify/test_scrobbler_purity.py)」)。
  2. `Const.TARGETS` の末尾に `{PKG_QOBUZ, SVC_QOBUZ}` を追加する (順序は
     Apple, AK Music, Qobuz)。
  3. `ScrobbleService.statusKey(String pkg)` に
     `if (Const.PKG_QOBUZ.equals(pkg)) return "qobuz";` を AK Music の行の直後に追加する。
     ScrobbleService のほかの箇所は変更しない。
  4. `ScrobblerTests.java`:
     - `"two player targets"` の eq を `"three player targets"` と期待値 3 に変更する。
     - TARGETS を走査して、`target[0]` が `"com.qobuz.music"` で、かつ `target[1]` が
       `"com.qobuz.android.mobile.component.mediaplayer.MusicService"` である要素が存在する
       ことを `TestKit.check("Qobuz target present", ...)` で検査する (テスト側はリテラルを
       そのまま書いてよい。purity 検査の対象は core だけ)。
     - `TestKit.eq("Qobuz package constant", "com.qobuz.music", Const.PKG_QOBUZ)` を追加する。
     - Amazon Music の既存チェックはそのまま残す。
- Acceptance:
  - `grep -n 'android\.' scrobbler/src/com/akscrobble/core/Const.java` が何も出力しない。
  - Java のユニットテストで "three player targets"、"Qobuz target present"、
    "Qobuz package constant" が成功し、既存テストも全件成功する。
  - `./verify/run_all.sh` が `ALL VERIFY PASS` で終わる。
- Verify: `! grep -n 'android\.' scrobbler/src/com/akscrobble/core/Const.java && ./verify/run_all.sh`

### Task 3: Resume accumulation when a repeat is detected after a non-playing state

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/PlayTracker.java`
  - `scrobbler/test/PlayTrackerTests.java`
- Files NOT to touch: `scrobbler/src/com/akscrobble/core/Const.java`, `scrobbler/src/com/akscrobble/*.java`,
  `verify/**`, `README*.md`, `docs/**`
- New dependencies: none
- Steps:
  1. `PlayTrackerTests.java` に `private static final int STATE_BUFFERING = 6;`
     (PlaybackState.STATE_BUFFERING と同じ値。core に Android 依存を持ち込まないため
     テスト内で定義する) を追加する。
  2. `qobuzSequences()` を追加し、`run()` の `shortTrack();` の後ろで呼ぶ。トラックは
     既存の `track()` ヘルパーではなく、Qobuz の実測に合わせて
     `new TrackInfo("com.qobuz.music", title, "Artist", "Album", null, 228000L, -1L, 0)`
     (albumArtist=null、mediaId=-1、duration=228000 なので閾値 114000) で作る。
     時刻は wall と elapsed に同じ値を渡す。以下の 4 シナリオをそれぞれ新しい
     `Sink` / `PlayTracker` で書く。
     - A「metadata が先、BUFFERING を経由して曲送り」: PLAYING pos 0 @0 → metadata t1 @0 →
       tick @114000 → metadata t2 @228000 → 同一内容の metadata t2 @228100 →
       BUFFERING pos 0 @228200 → PLAYING pos 50 @229000。
       assert: scrobble 1 件、`current().title` が t2、`accumulatedMs(239000)` == 10200
       (バッファリング中の 800ms は積算しない)。さらに tick @342800 で scrobble 2 件。
     - B「状態が先、position が 0 に戻ってから metadata」: PLAYING pos 0 @0 → metadata t1 @0 →
       tick @114000 → BUFFERING pos 0 @228000 → PLAYING pos 0 @228500 →
       metadata t2 @228600 → tick @342600 → tick @400000。
       assert: scrobble は 2 件だけ (t1 と t2 が 1 回ずつ。t1 の二重 scrobble なし)、
       `current().title` が t2。
     - C「BUFFERING を経由したリピート再生」: PLAYING pos 0 @0 → metadata t1 @0 →
       tick @114000 → BUFFERING pos 0 @228000 → PLAYING pos 0 @228500 →
       tick @342500。assert: scrobble 2 件 (同じ曲の 2 回目も scrobble される)、
       `accumulatedMs(342500)` == 114000。
     - D「一時停止中に先頭へ戻して再開」: PLAYING pos 0 @0 → metadata t1 @0 → tick @114000 →
       PAUSED pos 120000 @120000 → PLAYING pos 0 @130000 → tick @244000。
       assert: scrobble 2 件。
     ラベルは `"qobuz gapless keeps one scrobble per track"` のように、何を守るかが分かる
     英語にする。
  3. この時点でテストを実行し、C と D が失敗することを確認する (修正前の不具合の再現)。
  4. `PlayTracker.onPlaybackState` のリピート検出分岐を次のように変更する。
     ```java
     TrackInfo repeated = current;
     finish(elapsedMs);
     lastState = state;
     start(repeated, wallMs, elapsedMs);
     ```
     `lastState = state;` の直前に「直前が BUFFERING/PAUSED でも再生中として再開する。
     start() は lastState で再生中かどうかを判断するため」という趣旨の日本語コメントを
     1〜2 行添える。メソッド末尾の `lastState = state;` とほかの分岐は変更しない。
- Acceptance:
  - 修正後、A〜D のすべての assert と既存の PlayTracker テスト (特に repeatDetection の
    "low position below threshold is not a repeat") が成功する。
  - `grep -n 'android\.' scrobbler/src/com/akscrobble/core/PlayTracker.java` が何も出力しない。
  - `./verify/run_all.sh` が `ALL VERIFY PASS` で終わる。
- Verify: `./verify/run_all.sh`

## Manual device verification (orchestrator / user; not a runner task)

Task 1〜3 のコミット後、Task 4 を委譲する前に実施する。対象機は SP3000 (Android 10)、
Qobuz 10.1.2.0。インストール済みパッケージ名は `fm.last.android`。ログは
`adb logcat -s AKSCROB` で見る。状態は AKScrobble のメイン画面の STATUS
(`link_qobuz` / `playback_qobuz` / `track_raw_qobuz` / `progress_qobuz`) で確認する。

0. 署名鍵の一致を確認する (アンインストールを避けるため最初に行う)。
   - `adb shell pm path fm.last.android` で得たパスを
     `adb pull <path> <scratch>/installed.apk` で取得し、
     `python3 verify/verify_apk.py <scratch>/installed.apk` の `cert_sha256` と
     `version_code` を記録する。
   - インストールに使う APK を、実機と同じ鍵で versionCode を記録値より大きくしてビルドする
     (`AKS_KEYSTORE=... AKS_KS_PASS=... ./build/build_scrobbler.sh fm.last.android <vc> <out.apk> <vn>`)。
     `python3 verify/verify_apk.py <out.apk>` の `cert_sha256` が一致することを確認する。
   - 一致したら上書きインストールする (`adb install -r <out.apk>`。使えない場合は
     docs/DEVICE-NOTES.md「SP3000 シリーズ」のとおりファイルエクスプローラーから `.apk`
     を入れる)。一致しない場合は中止してユーザーに判断を仰ぐ (Open question 3)。
   - インストール後、`adb shell dumpsys deviceidle whitelist` に `fm.last.android` が
     残っていること、および Last.fm の認証状態が維持されていることを確認する。
1. E1 起動の副作用: `adb shell am force-stop com.qobuz.music` の後に AKScrobble の
   サービスを停止→開始する。`adb shell pidof com.qobuz.music` で Qobuz のプロセスが
   起動したかを記録し (Open question 1 の判断材料)、`link_qobuz` が `connected` になることを
   確認する。
2. E2 クラッシュなし: 全項目の終了時に `adb logcat -b crash -d | grep -i qobuz` が空であること。
3. E3 updateNowPlaying: Qobuz で再生を開始すると Last.fm のプロフィールに Now playing が
   出て、`track_raw_qobuz` に title/artist/album が入っていること (albumArtist=null)。
4. E4 閾値: 約 4 分以下の曲が duration/2 経過後に scrobble され、8 分を超える曲は 240 秒で
   scrobble されること。手に入れば 30 秒未満の曲が scrobble されないこと。
5. E5 一時停止/再開: 一時停止中に `progress_qobuz` の積算値が増えず、再開後に増えること。
6. E6 曲送りとギャップレス: アルバムを 3 曲以上自動遷移させ、手動スキップも 1 回行う。
   各曲がちょうど 1 回だけ scrobble され、二重送信がないこと。
7. E7 リピート: 1 曲リピートで 2 周再生すると 2 件 scrobble されること (Task 3 を
   採用した場合)。
8. E8 再接続: Qobuz を最近使ったアプリからスワイプで終了する。`link_qobuz` が
   suspended/failed になった後、Qobuz を再起動して再生すると、AKScrobble を再起動せずに
   `connected` に戻り scrobble が再開すること。
9. E9 回帰: AK Music と Apple Music で各 1 曲、従来どおり scrobble されること。

合格したら結果 (E1 のプロセス起動の有無、scrobble の accepted 件数) を記録しておき、
Task 4 の後でオーケストレーターが docs/DEVICE-NOTES.md の「E2E results」へ数行を直接追記する
(2 ファイル以下、30 行以下の直接編集の範囲内)。不合格の項目があれば Task 4 に進まず、
原因の調査を別途計画する。

### Task 4: Document Qobuz as a verified player

**ユーザー指示 (2026-10-31): Manual device verification の合格を待たずに、このタスクを今すぐ実施する。ブロックせず実行すること。** 文面は「最初から対応していた」書き方にし、「追加」「新たに」「2026/10/31」などの日付や変更履歴の表現は使わない。Qobuz は AK Music / Apple Music と同列の対応プレーヤーとして書く。

- Files to touch: `README.md`, `README.ja.md`, `README.ko.md`, `docs/DEVICE-NOTES.md`
- Files NOT to touch: `scrobbler/**`, `verify/**`, `build/**`, `docs/plans/**`
- New dependencies: none
- Steps:
  1. 3 つの README の「Supported players / 対応プレーヤー / 지원 플레이어」の箇条書きに、
     Apple Music の次の行として `- Qobuz (`com.qobuz.music`)` を追加する。
  2. 同じ節の直後の段落で、「Spotify と Qobuz は未検証・未対応」の文を「Spotify は未検証・
     未対応」に書き換える (英: "Spotify is untested and currently unsupported. If you need
     support for it, or for another player, ..."; 日: 「Spotify は未検証で、現在は対応して
     いません。Spotify、または別のプレーヤーへの対応が必要な場合は…」; 韓: 「Spotify는
     테스트되지 않았으며 현재 지원하지 않습니다. Spotify나 다른 플레이어 지원이 필요하면…」)。
     issue へのリンクと文の残りは変えない。
  3. 制限事項の箇条書き「Only AK Music and Apple Music are currently supported.」
     (README.md)、「現在対応しているのは AK Music と Apple Music だけです。」(README.ja.md)、
     「현재 AK Music과 Apple Music만 지원합니다.」(README.ko.md) を、それぞれ AK Music、
     Apple Music、Qobuz の 3 つを挙げる文に書き換える。
  4. 3 つの README に、Qobuz の注意を 1 段落追加する (Supported players 節の末尾)。内容:
     Qobuz は ALBUM_ARTIST を公開しないので albumArtist なしで送信されること。
     README の太字 (`**...**`) は使わない (verify/test_readme_ui_labels.py が UI ラベル以外の
     太字を拒否する)。
  5. `docs/DEVICE-NOTES.md`「Verified players」: 「次の 2 プレーヤーだけです」を
     「次の 3 プレーヤーだけです」にし、
     `- Qobuz: `com.qobuz.music`（サービスは `com.qobuz.android.mobile.component.mediaplayer.MusicService`、接続時の root は `QOBUZ_EMPTY_MEDIA_ROOT`）`
     を追加する。「Spotify と Qobuz は未検証・未対応です」を「Spotify は未検証・未対応です」に
     変える。
  6. `docs/DEVICE-NOTES.md`「Player quirks」の末尾に、Qobuz の段落を日本語で追加する。
     - root は未知の呼び出し元向けの空ルート `QOBUZ_EMPTY_MEDIA_ROOT` (Apple Music の
       `__EMPTY_ROOT__` と同種) だが、セッショントークンは取得でき、
       onMetadataChanged / onPlaybackStateChanged は届く。
     - 呼び出し元検証の罠: 呼び出し元が `uses-permission` を 1 つも宣言していないと、
       Qobuz が onGetLibraryRoot で
       `IllegalStateException("Caller wasn't found in the system?")` を投げ、Qobuz の
       プロセスがクラッシュする。AKScrobble はマニフェストで権限を宣言しているので
       該当しない。この前提は verify/test_manifest_permissions.py と
       verify/test_verify_apk.py で固定している。
     - metadata: TITLE / ARTIST / ALBUM / DURATION(ms) があり、ALBUM_ARTIST はない。
       MEDIA_ID は UUID 文字列なので AKScrobble 側の mediaId は -1 になる (アルバム補完は
       AK Music 専用なので影響なし)。独自キー `com.qobuz.mobile.TRACK_ISRC` などもある。
     - PlaybackState: 再生 3、一時停止 2。曲送りでは BUFFERING(6) を経由して 3 に戻り、
       position は 0 付近に戻る。position は正しく返る (AK Music の -1 問題はない)。
       曲送り時に同じ内容の onMetadataChanged が 2 回届くが、PlayTracker は同じ曲として
       統合する。BUFFERING 中は再生時間を積算しない。
  7. 新しい太字を一切追加しないこと。ほかの節は変更しない。
- Acceptance:
  - 3 つの README と DEVICE-NOTES に「Spotify と Qobuz は未検証」という趣旨の記述が残っていない
    (`grep -n "Qobuz" README*.md docs/DEVICE-NOTES.md` の結果に、Qobuz を未検証・未対応と
    する行がない)。
  - `python3 -m pytest verify/test_readme_ui_labels.py -q` が成功する。
  - `./verify/run_all.sh` が `ALL VERIFY PASS` で終わる。
- Verify: `python3 -m pytest verify/test_readme_ui_labels.py -q && ./verify/run_all.sh`

## Common verify command

`./verify/run_all.sh` (リポジトリのルートで実行。Java ユニットテスト、APK/XAPK の
ビルドと制約検査、verify/ の pytest をまとめて実行し、成功時は `ALL VERIFY PASS` を出力する)
