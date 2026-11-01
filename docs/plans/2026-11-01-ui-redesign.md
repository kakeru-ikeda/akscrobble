# UI 刷新 (セットアップ導線 / ホーム / 設定) とソース別の有効・無効

## Open questions

既定の扱いを決めてあるので、回答がなくても計画は成立する。違う場合だけ指示がほしい。

1. **全体スイッチ OFF と未インストールの表示の優先順位。** 既定: 全体 OFF なら、未インストールの
   ソースも含めて全て「停止中」と表示する (要件の文面どおり)。優先順位は
   停止中 > 未インストール > 無効 > 接続状態。
2. **生ステータス表示の行き先。** 現ホーム下部の等幅ステータス欄 (StatusBus 全量) は README の
   トラブルシューティングが参照している。既定: ホームからは外し、設定の「端末」に
   「詳細ステータス」行を 1 つ追加して、タップ時に AlertDialog で `StatusBus.snapshotText()` を
   1 回だけ表示する (定期更新なし)。モックには無い行なので、不要なら Task 7 から外す。
3. ~~README のスクリーンショット~~ **解決済み (2026-11-01):** 計画内で差し替える。ユーザーが
   SP2000 を ADB 接続した後に、Task 13 で実機から adb で撮影し、docs/images の既存ファイル名で
   上書きする。
4. **キーはあるが未認証 (session_key 無し) で起動した場合。** 既定: ホームに入れて、上部の赤い
   バナーで認証へ誘導する。ゲートで止めるのは api_key / api_secret の未設定だけにする。
5. 版数の更新とリリース作業はこの計画に含めない (release-please に任せる)。

## Goal

AKScrobble の UI を「使ってもらう」ための構成に作り直す。基本機能は変えない。SP2000
(AK ROM、Android 9、5 インチ) で軽く動くネイティブ実装に限る。

- 起動時に Last.fm の api_key / api_secret が未設定なら、必ずセットアップ画面 (SetupActivity) を
  出す。ここでは「アプリで入力」と「ファイルを置く」をタブで切り替える。キーが揃ったら Last.fm
  認証 (AuthActivity) へ進み、認証が済んだらホームへ入る。
- ホーム (MainActivity) はモック②のとおりにする。全体スイッチ、ユーザー名、再生中の曲、
  ソース 3 件 (スイッチと状態表示)、送信キュー、ADB への入口、認証失効バナーを置く。
- 設定 (SettingsActivity、新規) はモック③のとおりにする。ホームの歯車から開き、「‹ 戻る」か
  戻るキーで閉じる。
- AK Music / Apple Music / Qobuz ごとに有効・無効を切り替えられるようにする。既定は全て有効
  (現在の挙動を維持)。切り替えるとサービスを再起動せず、そのソースだけを接続または切断する。
- 既存画面 (Credentials / Auth / Queue / Sanitize / Adb) の見た目を共通の Ui ヘルパーで統一し、
  各 Activity に重複している addButton / button / text ヘルパーを無くす。

## Non-goals

- 機能の追加と削除 (ソース別の有効・無効とセットアップ導線を除く)。スクロブル判定、送信、
  キュー、サニタイズ、ADB、更新確認 (UpdateChecker / UpdateFlow) の挙動は変えない。
- XML レイアウト、RecyclerView、Fragment、外部ライブラリ、画像、影、グラデーション、
  アニメーション、ライトテーマ。
- 下タブ、ナビゲーションドロワー。
- 新しい UI テスト基盤 (Espresso など)。UI の動作は実機で目視確認する。
- README のスクリーンショットの枚数とファイル名の変更 (差し替えは Task 13 で行うが、既存の
  4 枚と同じファイル名を踏襲する)。
- Const.TARGETS の変更 (3 ソース固定のまま)。

## 制約 (全タスク共通。ワーカーは必ず守る)

- Java 8 の構文だけを使う (ラムダ、diamond 演算子、try-with-resources は既存コードに合わせて
  使わない)。Gradle は無い。ビルドは `build/build_scrobbler.sh` が aapt2/javac/d8 を直接呼ぶ。
- minSdk 21 / targetSdk 28。API 23 以上の API は `Build.VERSION.SDK_INT >= 23` で分岐する。
- `scrobbler/src/com/akscrobble/core/` は Android 非依存の純 Java にする。コメントも含めて、
  部分文字列 `android.` を書いてはいけない (`verify/test_scrobbler_purity.py`)。
  `scrobbler/test/` は `com.akscrobble.core.*` しか import できない。
- `scrobbler/src/com/akscrobble/*.java` の文字列リテラルに日本語を書いてはいけない
  (`verify/test_no_hardcoded_strings.py`)。UI 文言は必ず `core/Strings.java` に JA/EN/KO の
  3 言語で追加し、`Ui.s(context, Strings.KEY, args...)` で取得する。`{0}` 形式の引数の集合は
  3 言語で一致させる (StringsTests が検査する)。
- 既存の Strings キーは削除しない。README の太字が参照しているため。未使用になっても残す。
- manifest に登録する全 Activity は、`onCreate` で `setTitle(Ui.s(` を呼ぶ
  (`test_every_activity_localizes_its_title`。MainActivity は対象外)。テーマが NoActionBar でも
  呼ぶ。
- 新しい Activity は `scrobbler/AndroidManifest.xml` に `android:exported="false"` で登録する。
  既存の uses-permission は消さない。
- 色は Ui のパレット定数だけを使い、Activity に `Color.BLACK` などを直書きしない。寸法は
  `Ui.dp(...)` を使い、px を直書きしない。
- 軽さの方針: 1 画面の View 数を抑える (目安はホームで 60 個以下)。定期更新はホームの 1 秒
  更新だけにする。DB の件数取得は 5 秒に 1 回に間引き、`setText` は値が変わったときだけ呼ぶ。
  Bitmap は使わない。ファイルを新規に書き出さない。

## Design notes

- **テーマ:** `res/values/styles.xml` に `AKS.Theme` (parent `android:Theme.Material.NoActionBar`)
  を置き、`<application android:theme>` で全画面に適用する。XML はリソース値だけで、レイアウトは
  作らない。タイトルバーは各画面が `Ui.screen(...)` で自前に描く (モックの歯車と「‹ 戻る」を
  置くため)。却下案: 端末既定テーマのまま色を直書きする案。AK ROM の DeviceDefault は明暗が
  機種依存で、ダーク固定にできない。
- **Ui ヘルパー:** 既存の `Ui.java` (`lang` / `s`) に View 生成の静的メソッドを足し、部品を 1 か所に
  まとめる。角丸は単色の `GradientDrawable`、押下時の見た目は単色の `StateListDrawable` だけで
  作る。Material Button の elevation 影は `setStateListAnimator(null)` で消す。スイッチは
  `android.widget.Switch` を使い、API 23 以上なら thumb/track を tint する。歯車アイコンは
  `Ui.GearView` (Canvas に円 1 つと歯 8 本を描く小さな View) で描き、SVG も画像も使わない。
- **ソース別の有効・無効:** 接続対象を決める計算は core の `Sources.reconcile(active, enabled)` に
  純 Java で切り出し、単体テストを付ける。ScrobbleService は `playerLinks` を
  `Map<String, PlayerLink>` に変え、`applySources()` で差分だけを start/stop する。UI から
  切り替えたときは Prefs を書き、サービスが有効 (SERVICE_ENABLED=true) なら
  `ACTION_SOURCES` を送る。これでサービスは再起動せず、該当ソースの PlayerLink と PlayTracker
  だけを作り直すか破棄する。無効化するときは `tracker.onDisconnected(...)` を呼んでから破棄する
  (切断時の既存挙動と同じ)。却下案: 切り替えのたびにサービスを再起動する案。他のソースの
  接続と送信中の drain が途切れる。
- **ソースの表示状態:** `core/SourceStatus.of(serviceEnabled, sourceEnabled, installed, linkState)`
  で 6 状態を決める (停止中 / 未インストール / 無効 / 接続中 / 接続しています / 未接続)。
  未インストールかどうかはホームの onResume で 1 回だけ PackageManager に問い合わせ、1 秒更新では
  問い合わせない。
- **再生中の曲:** ScrobbleService が StatusBus に `np_title_<key>` / `np_artist_<key>` /
  `np_album_<key>` (サニタイズ後の値) を追加で出す。ホームは `core/NowPlaying.pick(...)` で
  表示するソースを選ぶ (再生中 = playback が 3 のソースを優先し、無ければ曲情報を持つ最初の
  ソース)。現在の `track_sanitized_*` を文字列分解する案は、区切り文字が曲名に含まれると
  壊れるので採らない。
- **最終送信時刻:** 今は記録していない。Sender が受理件数 1 件以上の送信成功時に
  `Prefs.LAST_SENT_AT` (epoch ms、long) を書く。
- **起動ゲート:** `core/LaunchRoute.decide(Credentials)` が SETUP / AUTH / HOME を返す。
  MainActivity.onCreate で SETUP なら SetupActivity を開いて finish する。SetupActivity は onResume
  ごとに判定し直す。SETUP ならキー入力、AUTH なら「認証へ」、HOME ならホームへ移ってから
  finish する。AuthActivity は成功時に finish するので、戻った先の Setup.onResume がホームへ
  進める。判定は既存の `ConfigStore.credentials()` (config > prefs > BuildSecrets) だけで行う。
  config ファイルが読めないとストレージ権限なしで SETUP と誤判定するため、SetupActivity も
  onCreate でストレージ権限を要求し、許可された時点で判定し直す。
- **MainActivity の分割:** 設定ファイル取込、ストレージ権限、電池最適化、診断ダンプ、
  サービス操作、更新ダイアログの処理を `AppActions` (Android 依存。core ではない) に移す。
  ホーム、設定、セットアップはこれを共有する。
- **言語切替:** 設定で Prefs.LANGUAGE を変えたら `recreate()` する。ホームは生成時の言語を
  保持しておき、onResume で現在の言語と異なれば `recreate()` する。

## 共通検証コマンド (`--verify`)

```sh
./verify/run_all.sh
```

単体テストだけを速く回すコマンド (以降のタスクで `UNIT` と呼ぶ):

```sh
bash -c 'set -euo pipefail; source tools/env.sh; T=$(mktemp -d); java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -classpath "$ANDROID_JAR" -d "$T" $(find scrobbler/src scrobbler/test -name "*.java"); java -cp "$T:$ANDROID_JAR" ScrobblerTests'
```

## 実機確認 (UI テストが無いため。オーケストレーターかユーザーが行う)

各タスクの Acceptance に「実機確認」として書いた項目は、ワーカーの検証には含めない。
Task 8 と Task 11 の後に SP2000 (またはエミュレータ API 28、5 インチ相当) へまとめて
インストールして確認する。README のスクリーンショット差し替え (Task 13) は、Task 11 の実機確認と
Task 12 の README 更新が終わってから、ADB 接続済みの SP2000 で行う。

---

### Task 1: 文言テーブルとダークテーマを追加する

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/Strings.java`
  - `scrobbler/res/values/styles.xml` (新規)
  - `scrobbler/res/values/aks_colors.xml` (新規)
  - `scrobbler/AndroidManifest.xml`
  - `scrobbler/src/com/akscrobble/AuthActivity.java` (status の文字色 1 行だけ)
- Files NOT to touch: `scrobbler/src/com/akscrobble/core/*` (Strings.java を除く)、`verify/**`、
  `README*.md`、その他の Activity
- New dependencies: none
- Steps:
  1. `Strings.java` に、次の定数とテーブル行を追加する。定数名、キー文字列、JA / EN / KO を
     下の表のとおりにする。定数は既存の並びの末尾 (`MAIN_VERSION` などの後) に置き、
     テーブル行は `TABLE` の末尾に置く。`‹` は U+2039。
     | 定数 | キー | JA | EN | KO |
     |---|---|---|---|---|
     | UI_BACK | ui.back | ‹ 戻る | ‹ Back | ‹ 뒤로 |
     | UI_SETTINGS | ui.settings | 設定 | Settings | 설정 |
     | SETUP_TITLE | setup.title | セットアップ | Setup | 초기 설정 |
     | SETUP_STEP_KEYS | setup.step_keys | 1 Last.fm キー | 1 Last.fm key | 1 Last.fm 키 |
     | SETUP_STEP_AUTH | setup.step_auth | 2 認証 | 2 Authorize | 2 인증 |
     | SETUP_STEP_SOURCES | setup.step_sources | 3 ソース | 3 Sources | 3 소스 |
     | SETUP_HEADING_KEYS | setup.heading_keys | まず Last.fm の API キーを設定します | First, set up your Last.fm API key | 먼저 Last.fm API 키를 설정합니다 |
     | SETUP_HEADING_AUTH | setup.heading_auth | 次に Last.fm で認証します | Next, authorize AKScrobble on Last.fm | 다음으로 Last.fm에서 인증합니다 |
     | SETUP_AUTH_HELP | setup.auth_help | API キーを設定しました。Last.fm で AKScrobble を承認してください。 | Your API key is set. Approve AKScrobble on Last.fm. | API 키를 설정했습니다. Last.fm에서 AKScrobble을 승인하세요. |
     | SETUP_GET_KEY_AT | setup.get_key_at | 取得先: {0} | Get one at: {0} | 발급처: {0} |
     | SETUP_TAB_INPUT | setup.tab_input | アプリで入力 | Type in app | 앱에서 입력 |
     | SETUP_TAB_FILE | setup.tab_file | ファイルを置く | Use a file | 파일 배치 |
     | SETUP_KEY_HINT | setup.key_hint | 32 桁の英数字 | 32 letters and digits | 32자리 영숫자 |
     | SETUP_SAVE_AND_AUTH | setup.save_and_auth | 保存して認証へ | Save and authorize | 저장하고 인증 |
     | SETUP_FILE_HELP | setup.file_help | 本体の次の場所に config.properties を置いて、読み込みを押します。キーの入力は不要です。 | Put config.properties at the path below, then tap Load file. No typing needed. | 아래 위치에 config.properties를 두고 불러오기를 누르세요. 키를 입력할 필요가 없습니다. |
     | SETUP_FILE_NOTE | setup.file_note | api_key / api_secret を書いたファイルです。無ければテンプレートを作れます。 | The file holds api_key / api_secret. You can create a template if it is missing. | api_key / api_secret을 적은 파일입니다. 없으면 템플릿을 만들 수 있습니다. |
     | SETUP_MAKE_TEMPLATE | setup.make_template | テンプレートを作る | Create template | 템플릿 만들기 |
     | SETUP_LOAD_FILE | setup.load_file | ファイルを読み込む | Load file | 파일 불러오기 |
     | SETUP_TEMPLATE_CREATED | setup.template_created | テンプレートを作成しました: {0} | Template created: {0} | 템플릿을 만들었습니다: {0} |
     | SETUP_TEMPLATE_EXISTS | setup.template_exists | ファイルは既にあります: {0} | The file already exists: {0} | 파일이 이미 있습니다: {0} |
     | SETUP_FILE_KEYS_MISSING | setup.file_keys_missing | ファイルに api_key と api_secret が見つかりません。 | api_key and api_secret were not found in the file. | 파일에서 api_key와 api_secret을 찾을 수 없습니다. |
     | SETUP_STORAGE_NEEDED | setup.storage_needed | ファイルを扱うにはストレージ権限が必要です。 | Storage permission is required to use the file. | 파일을 사용하려면 저장소 권한이 필요합니다. |
     | SETUP_LATER | setup.later | あとで(ホームへ) | Later (go to home) | 나중에 (홈으로) |
     | SETUP_AFTER_KEYS | setup.after_keys | キー設定後 | After key setup | 키 설정 후 |
     | HOME_SCROBBLING | home.scrobbling | スクロブル中 | Scrobbling | 스크로블 중 |
     | HOME_STOPPED | home.stopped | 停止中 | Stopped | 중지됨 |
     | HOME_SENDING_AS | home.sending_as | {0} として送信 | Sending as {0} | {0}(으)로 전송 |
     | HOME_NOW_PLAYING | home.now_playing | 再生中 · {0} | Now playing · {0} | 재생 중 · {0} |
     | HOME_NOTHING_PLAYING | home.nothing_playing | 再生中の曲はありません | Nothing is playing | 재생 중인 곡이 없습니다 |
     | HOME_SEC_SOURCES | home.sec_sources | ソース | Sources | 소스 |
     | HOME_SEC_QUEUE | home.sec_queue | 送信キュー | Scrobble queue | 전송 대기열 |
     | HOME_SEC_DEVICE | home.sec_device | 端末 | Device | 기기 |
     | HOME_QUEUE_PENDING | home.queue_pending | 待機 {0} 件 | {0} pending | 대기 {0}건 |
     | HOME_QUEUE_DETAIL | home.queue_detail | dead {0} 件 · 最終送信 {1} | {0} dead · last sent {1} | dead {0}건 · 마지막 전송 {1} |
     | HOME_NEVER_SENT | home.never_sent | なし | never | 없음 |
     | HOME_ADB_SUB | home.adb_sub | 開発者向け設定を開く | Open developer settings | 개발자 설정 열기 |
     | HOME_BANNER_AUTH_INVALID | home.banner_auth_invalid | Last.fm の認証が無効になりました。タップして再認証してください。 | Last.fm authorization is no longer valid. Tap to authorize again. | Last.fm 인증이 더 이상 유효하지 않습니다. 눌러서 다시 인증하세요. |
     | HOME_BANNER_NOT_AUTHED | home.banner_not_authed | Last.fm の認証がまだです。タップして認証してください。 | Last.fm is not authorized yet. Tap to authorize. | 아직 Last.fm 인증을 하지 않았습니다. 눌러서 인증하세요. |
     | SRC_CONNECTED | src.connected | 接続中 | Connected | 연결됨 |
     | SRC_CONNECTING | src.connecting | 接続しています | Connecting | 연결하는 중 |
     | SRC_DISCONNECTED | src.disconnected | 未接続(再試行待ち) | Not connected (will retry) | 연결 안 됨 (재시도 대기) |
     | SRC_DISABLED | src.disabled | 無効 | Disabled | 비활성 |
     | SRC_NOT_INSTALLED | src.not_installed | 未インストール | Not installed | 설치되지 않음 |
     | SRC_STOPPED | src.stopped | 停止中 | Stopped | 중지됨 |
     | SET_SEC_ACCOUNT | settings.sec_account | アカウント | Account | 계정 |
     | SET_SEC_SCROBBLE | settings.sec_scrobble | スクロブル | Scrobbling | 스크로블 |
     | SET_SEC_VERSION | settings.sec_version | バージョン | Version | 버전 |
     | SET_API_KEY | settings.api_key | Last.fm API キー | Last.fm API key | Last.fm API 키 |
     | SET_KEY_FROM | settings.key_from | {0} から取得 | From {0} | {0}에서 가져옴 |
     | SET_SOURCE_CONFIG | settings.source_config | 設定ファイル | the configuration file | 설정 파일 |
     | SET_SOURCE_PREFS | settings.source_prefs | アプリ内の設定 | in-app settings | 앱 내 설정 |
     | SET_SOURCE_BUILTIN | settings.source_builtin | 組み込みのキー | the built-in key | 내장 키 |
     | SET_SOURCE_NONE | settings.source_none | 未設定 | Not set | 설정 안 됨 |
     | SET_AUTHED | settings.authed | 認証済み ({0}) | Authorized ({0}) | 인증됨 ({0}) |
     | SET_AUTH_INVALID | settings.auth_invalid | 再認証が必要です | Authorization needed again | 다시 인증해야 합니다 |
     | SET_SANITIZE | settings.sanitize | タイトルの整形(サニタイズ) | Title cleanup (sanitize) | 제목 정리 (문자열 정리) |
     | SET_LANGUAGE | settings.language | 言語 | Language | 언어 |
     | SET_STORAGE | settings.storage | ストレージ権限 | Storage permission | 저장소 권한 |
     | SET_GRANTED | settings.granted | 許可済み | Granted | 허용됨 |
     | SET_NOT_GRANTED | settings.not_granted | 未許可(タップで要求) | Not granted (tap to request) | 허용 안 됨 (눌러서 요청) |
     | SET_STATUS | settings.status | 詳細ステータス | Detailed status | 상세 상태 |
     | SET_CHECK_UPDATE | settings.check_update | アップデートを確認 | Check for updates | 업데이트 확인 |
     | SET_UP_TO_DATE | settings.up_to_date | 最新版です。 | You have the latest version. | 최신 버전입니다. |
     | SET_UPDATE_FAILED | settings.update_failed | 更新を確認できませんでした。 | Could not check for updates. | 업데이트를 확인할 수 없습니다. |
  2. `scrobbler/res/values/aks_colors.xml` を作り、`<color>` を 4 つ定義する。
     `aks_bg`=#FF0F0F10、`aks_surface`=#FF1A1A1C、`aks_fg`=#FFF2F1EF、`aks_accent`=#FFE0281C。
  3. `scrobbler/res/values/styles.xml` を作り、`<style name="AKS.Theme"
     parent="android:Theme.Material.NoActionBar">` を定義する。item は次の 7 つ:
     `android:windowBackground`=@color/aks_bg、`android:colorBackground`=@color/aks_bg、
     `android:statusBarColor`=@color/aks_bg、`android:navigationBarColor`=@color/aks_bg、
     `android:colorAccent`=@color/aks_accent、`android:textColorPrimary`=@color/aks_fg、
     `android:windowContentOverlay`=@null。
  4. `AndroidManifest.xml` の `<application>` に `android:theme="@style/AKS.Theme"` を足す。
     その他は変更しない。
  5. `AuthActivity.java` の `status.setTextColor(Color.BLACK);` を
     `status.setTextColor(Color.WHITE);` に変える (ダーク背景で読めなくなるため。Task 9 で
     Ui パレットに置き換える)。`Color` の import は残す。
- Acceptance:
  - StringsTests が全キーで JA/EN/KO の存在と placeholder の一致を PASS する。
  - APK がビルドでき (`run_all.sh` の 2/4)、aapt2 が styles.xml を受け入れる。
  - 実機確認: 全画面が黒系の背景になり、AuthActivity のステータス文字が読める (この時点では
    旧画面のタイトルバーは消える。想定どおり)。
- Verify: `./verify/run_all.sh`

### Task 2: Ui ヘルパーに共通部品を追加する

- Files to touch: `scrobbler/src/com/akscrobble/Ui.java`
- Files NOT to touch: すべての Activity、`core/**`、`res/**`、`verify/**`
- New dependencies: none
- Steps:
  1. 既存の `lang(Context)` と `s(Context, String, Object...)` はシグネチャも挙動も変えない。
  2. パレット定数 (`public static final int`、ARGB) を追加する。
     `BG=0xFF0F0F10, SURFACE=0xFF1A1A1C, SURFACE2=0xFF242427, LINE=0xFF2E2E32,
     FG=0xFFF2F1EF, MUTED=0xFF8F8E8B, ACCENT=0xFFE0281C, ACCENT_PRESSED=0xFFB81F15,
     OK=0xFF3FB27F, WARN=0xFFE0A526, OFF=0xFF4A4A4F, BANNER_BG=0xFF3A1512,
     BANNER_LINE=0xFF6B241E, BANNER_FG=0xFFFFD6D1`。
  3. 次の public static メソッドと型を実装する。View は全て Java で生成する。
     - `int dp(Context c, float dp)`: `TypedValue.applyDimension(COMPLEX_UNIT_DIP, ...)` を四捨五入
       して返す。
     - `GradientDrawable rounded(Context c, int fill, float radiusDp)`: 単色で角丸の矩形。
     - `GradientDrawable rounded(Context c, int fill, float radiusDp, int stroke, boolean dashed)`:
       1dp の枠線付き。dashed なら `setStroke(w, stroke, dp(4), dp(3))`。
     - `StateListDrawable pressable(Context c, int normal, int pressed, float radiusDp)`:
       `state_pressed` のとき pressed 色、それ以外は normal 色。normal=0 なら透明。
     - `final static class Screen { public final LinearLayout bar; public final TextView title;
       public final LinearLayout body; }`
     - `Screen screen(Activity a, String title, View trailing, boolean scroll)`: 縦の root
       (背景 BG) を作り、上部に bar を置く。bar は横並びで padding 16dp/12dp、title は 15sp、
       太字、FG、weight 1。trailing が非 null なら右端に置く。bar の下に 1dp の LINE 線を置く。
       body は縦の LinearLayout で、padding 16dp/14dp、子同士の間隔 12dp
       (`setDividerDrawable` に透明な 12dp の GradientDrawable を渡し、
       `SHOW_DIVIDER_MIDDLE` を指定)。scroll=true なら body を ScrollView に入れ、
       ScrollView を weight 1 で root に置く。false なら body 自体を weight 1 で置く。最後に
       `a.setContentView(root)` する。setTitle は呼ばない (呼ぶのは各 Activity)。
     - `TextView backLink(final Activity a)`: `Strings.UI_BACK` の文言、MUTED、13sp。タップで
       `a.finish()`。左右 padding 8dp。
     - `View gear(Context c, View.OnClickListener l)`: 36dp 四方の `GearView`。contentDescription は
       `Strings.UI_SETTINGS`。背景は `pressable(c, 0, SURFACE2, 18)`。
     - `static final class GearView extends View`: onDraw で中心に半径 r=min(w,h)*0.33 の FG 円を
       塗り、45 度ずつ回転させて幅 r*0.45、長さ r*1.35 の歯を 8 本描く (`canvas.rotate` +
       `drawRect`)。最後に半径 r*0.42 の BG 円で穴を開ける。Paint は ANTI_ALIAS を 1 つ持ち、
       onDraw の中で new しない。
     - `TextView section(Context c, String label)`: 11sp、太字、MUTED、letterSpacing 0.06f、
       左 padding 2dp。
     - `LinearLayout list(Context c)`: 縦並び、背景 `rounded(c, SURFACE, 10)`。
     - `void addRow(LinearLayout list, View row)`: list に子が既にあれば、先に 1px (`Math.max(1,
       dp(0.5f))`) の LINE 色区切り View を足してから row を足す。
     - `final static class Row { public final LinearLayout view; public final TextView title;
       public final TextView subtitle; }`
     - `Row row(Context c, String title, String subtitle, View leading, View trailing,
       View.OnClickListener onClick)`: 横並びで padding 14dp/12dp、子の間隔 12dp、縦は中央揃え。
       中央の縦ブロック (weight 1) に title (14sp、太字、FG) と subtitle (11.5sp、MUTED。null か
       空なら GONE) を置く。onClick が非 null なら、背景を `pressable(c, 0, SURFACE2, 0)` にし、
       trailing が null のとき右端に "›" (MUTED、18sp) を置く。
     - `void setStatus(TextView subtitle, String text, int dotColor)`: "● " (U+25CF) と text を
       連結し、先頭の 1 文字に `ForegroundColorSpan(dotColor)` を付けて setText する。
       dotColor=0 なら点を付けない。
     - `TextView tag(Context c, String abbr, int color)`: 34dp 四方、`rounded(c, color, 8)`、
       白の 12sp 太字、中央寄せ。
     - `Switch toggle(Context c, boolean checked)`: `new Switch(c)`。API 23 以上なら
       thumbTintList (checked=白、それ以外=白) と trackTintList (checked=OK、それ以外=OFF) を
       設定する。`setShowText(false)` にする。
     - `Button button(Context c, String label, boolean primary, View.OnClickListener l)`:
       `setAllCaps(false)`、太字、padding 12dp、`setStateListAnimator(null)` (API 21 以上なので
       常に可)。primary は `pressable(c, ACCENT, ACCENT_PRESSED, 8)` に白文字、それ以外は
       `pressable(c, SURFACE2, LINE, 8)` に FG 文字。LayoutParams は MATCH_PARENT x WRAP_CONTENT。
     - `EditText input(Context c, String hint, String value, boolean monospace)`: 単一行、
       背景 `rounded(c, SURFACE, 8, LINE, false)`、padding 12dp/10dp、FG、hint は OFF、13sp。
       monospace なら `Typeface.MONOSPACE`。
     - `TextView text(Context c, String value, float sp, int color)`: 汎用。
     - `TextView mono(Context c, String value)`: 10sp、MONOSPACE、FG、背景
       `rounded(c, SURFACE, 8)`、padding 10dp、`setTextIsSelectable(true)`。ログ欄用。
     - `TextView pathBox(Context c, String path)`: mono に近いが、背景を
       `rounded(c, SURFACE, 8, OFF, true)` (破線) にし、11.5sp。
     - `CheckBox checkBox(Context c, String label, boolean checked)`: MUTED、12sp。API 21 以上で
       `setButtonTintList` (checked=ACCENT、それ以外=OFF) を設定する。
     - `LinearLayout steps(Context c, int current, String[] labels)`: 縦の container を返す。
       1 段目は高さ 3dp の横バー labels.length 本 (weight 1、間隔 4dp)。index < current は
       ACCENT、それ以外は OFF。2 段目は labels を等分の TextView (11sp、MUTED) で並べる。
       current は 1 始まり。
     - `final static class Segment { public final LinearLayout view; public void select(int index); }`
       と `interface SegmentListener { void onSelected(int index); }`、
       `Segment segment(Context c, String[] labels, int selected, SegmentListener l)`: 背景
       `rounded(c, SURFACE, 8)`、padding 3dp、子は weight 1 の TextView (太字、padding 8dp)。
       選択中は背景 `rounded(c, SURFACE2, 6)` に FG、非選択は透明に MUTED。タップすると select
       してから l を呼ぶ。
     - `TextView banner(Context c, String message, View.OnClickListener l)`: 背景
       `rounded(c, BANNER_BG, 10, BANNER_LINE, false)`、BANNER_FG、12sp、padding 12dp/10dp。
       l は null 可。
  4. Ui に Android の import を追加するのは自由。`core` への依存は `Strings` だけにする。
- Acceptance:
  - 単体テストと APK ビルドが通る (Ui はまだどの画面からも使われていなくてよい)。
  - `Ui.java` に日本語の文字列リテラルが無い。
- Verify: `./verify/run_all.sh`

### Task 3: ソースの定義、表示状態、再生中ソースの選択を core に追加する (TDD)

- Files to touch:
  - `scrobbler/test/SourcesTests.java` (新規。先に書く)
  - `scrobbler/test/ScrobblerTests.java` (`SourcesTests.run();` を 1 行足す)
  - `scrobbler/src/com/akscrobble/core/Sources.java` (新規)
  - `scrobbler/src/com/akscrobble/core/SourceStatus.java` (新規)
  - `scrobbler/src/com/akscrobble/core/NowPlaying.java` (新規)
- Files NOT to touch: `scrobbler/src/com/akscrobble/*.java` (core 以外)、`core/Const.java`、
  `verify/**`
- New dependencies: none
- Steps:
  1. 先に `SourcesTests.java` を書く (TestKit を使う。`static void run()`)。下記 2 から 4 の仕様を
     全て検査し、実装前は失敗することを確認する。最低限、次のケースを含める。
     - `Sources.prefKey(Const.PKG_QOBUZ)` が `"source_enabled_com.qobuz.music"` になる。
     - `statusKey` が apple / akmusic / qobuz を返し、未知の pkg ならその pkg を返す。
     - `displayOrder()` が {AKMUSIC, APPLE, QOBUZ} の順になる。`label` / `tag` が非空になる。
       `serviceOf(PKG_QOBUZ)` が `Const.SVC_QOBUZ` になり、未知の pkg なら null になる。
     - `reconcile`: active が空で全て有効なら、start が TARGETS 順の 3 件、stop が 0 件。
       Apple が active で無効なら、stop は [apple] で start に apple が無い。enabled に無いキーは
       有効扱い。active に TARGETS 外の pkg があれば stop に入る。
     - `SourceStatus.of`: 停止中 > 未インストール > 無効 > 接続状態の優先順位。linkState の
       "connected"→CONNECTED、"connecting"→CONNECTING、"absent"→NOT_INSTALLED、
       "disabled"/"failed"/"suspended"/null→DISCONNECTED。
     - `NowPlaying.pick`: playback_<key>="3" のソースを優先する。無ければ np_title_<key> が
       非空の最初のソース (順序は引数配列の順)。どちらも無ければ null。
  2. `core/Sources.java` (final class、private コンストラクタ):
     - `public static final String PREF_PREFIX = "source_enabled_";`
     - `public static String prefKey(String pkg)` → PREF_PREFIX + pkg
     - `public static String statusKey(String pkg)`: PKG_APPLE→"apple"、PKG_AKMUSIC→"akmusic"、
       PKG_QOBUZ→"qobuz"、それ以外は pkg (ScrobbleService.statusKey と同じ対応)。
     - `public static String[] displayOrder()` → 新しい配列 {PKG_AKMUSIC, PKG_APPLE, PKG_QOBUZ}
     - `public static String label(String pkg)`: "AK Music" / "Apple Music" / "Qobuz"。未知なら pkg。
     - `public static String tag(String pkg)`: "AK" / "Ap" / "Qo"。
     - `public static int tagColor(String pkg)`: AK=0xFF2F6FB3、Apple=0xFFC23A58、
       Qobuz=0xFF3C3C44、その他は 0xFF4A4A4F。
     - `public static String serviceOf(String pkg)`: Const.TARGETS から引く。無ければ null。
     - `public static final class Plan { public final List<String> start; public final List<String> stop; }`
     - `public static Plan reconcile(Collection<String> active, Map<String, Boolean> enabled)`:
       TARGETS の順に走査し、有効 (enabled.get が null か TRUE) で active に無いものを start に、
       無効で active にあるものを stop に入れる。続けて、active のうち TARGETS に無いものを
       stop に足す。リストは unmodifiable にする。
  3. `core/SourceStatus.java`: `public enum SourceStatus { CONNECTED, CONNECTING, DISCONNECTED,
     DISABLED, NOT_INSTALLED, STOPPED; public static SourceStatus of(boolean serviceEnabled,
     boolean sourceEnabled, boolean installed, String linkState) }`。判定順は Step 1 のとおり。
  4. `core/NowPlaying.java`: `public static String pick(String[] pkgs, Map<String, String> status)`。
     キーは `"playback_" + Sources.statusKey(pkg)` と `"np_title_" + Sources.statusKey(pkg)`。
     PLAYING の判定は `String.valueOf(Const.STATE_PLAYING)` と比べる。
  5. ScrobblerTests.main に `SourcesTests.run();` を、`ConfigTests.run();` の後に足す。
  6. core のファイルには、コメントも含めて `android.` を書かない。
- Acceptance:
  - `SourcesTests` の全ケースが PASS し、`ALL TESTS PASS` が出る。
  - `python3 -m pytest verify/test_scrobbler_purity.py -q` が通る。
- Verify: `UNIT` コマンド (上記) と `./verify/run_all.sh`

### Task 4: ScrobbleService をソースごとに接続・切断できるようにする

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/Const.java` (`ACTION_SOURCES` を 1 行足す)
  - `scrobbler/src/com/akscrobble/Prefs.java`
  - `scrobbler/src/com/akscrobble/StatusBus.java`
  - `scrobbler/src/com/akscrobble/ScrobbleService.java`
  - `scrobbler/src/com/akscrobble/Sender.java` (最終送信時刻の記録だけ)
- Files NOT to touch: `PlayerLink.java`、`core/PlayTracker.java`、`core/Sources.java`
  (Task 3 の成果をそのまま使う)、全 Activity、`verify/**`
- New dependencies: none
- Steps:
  1. `Const` に `public static final String ACTION_SOURCES = "com.akscrobble.action.SOURCES";` を
     足す。
  2. `Prefs`:
     - `public static final String LAST_SENT_AT = "last_sent_at";` を足す。
     - `public static String sourceEnabledKey(String pkg)` → `Sources.prefKey(pkg)` を足す。
     - `getBoolean` の既定値を `SANITIZE_ENABLED.equals(key) || key.startsWith(Sources.PREF_PREFIX)`
       にする (ソース別キーは既定 true。既存キーの既定値は変えない)。
     - `public long getLong(String key)` (既定 0L) と `public void putLong(String key, long value)`
       (apply) を足す。
  3. `StatusBus` に `public static synchronized String get(String key)` (無ければ "")、
     `public static synchronized Map<String, String> snapshot()` (LinkedHashMap のコピー)、
     `public static void remove(String key)` (VALUES から削除して、put と同じく listener を呼ぶ) を
     足す。既存メソッドは変えない。
  4. `ScrobbleService`:
     - `playerLinks` を `private final Map<String, PlayerLink> playerLinks = new LinkedHashMap<String, PlayerLink>();`
       に変え、supervisor と onDestroy は `playerLinks.values()` を回す。
     - onCreate の `for (String[] target : Const.TARGETS) {...}` を `applySources();` に置き換える。
     - `private void applySources()` を追加する (main スレッドから呼ぶ前提)。
       a. 各 TARGETS について `prefs.getBoolean(Prefs.sourceEnabledKey(pkg))` を Map に詰める。
       b. `Sources.Plan plan = Sources.reconcile(new ArrayList<String>(playerLinks.keySet()), enabled);`
       c. plan.stop の各 pkg: `playerLinks.remove(pkg)` を `stop()` する。`trackers.remove(pkg)` が
          非 null なら、先に `tracker.onDisconnected(System.currentTimeMillis(),
          SystemClock.elapsedRealtime())` を呼ぶ。続けて `StatusBus.put("link_" + key, "disabled")`
          を呼び、`playback_`、`progress_`、`track_raw_`、`track_sanitized_`、`np_title_`、
          `np_artist_`、`np_album_` の各 key を `StatusBus.remove` する。
          `Log.i(Const.TAG, pkg + " source disabled")`。
       d. plan.start の各 pkg: 今の onCreate と同じく PlayTracker と PlayerLink
          (`Sources.serviceOf(pkg)`) を作り、map に入れて `start()` する。
     - onStartCommand に `Const.ACTION_SOURCES` の分岐を足し、`applySources()` を呼ぶ。既存の
       STOP / FLUSH 分岐は変えない。
     - `publishTrackStatus` で、サニタイズ後の値を `np_title_<key>` / `np_artist_<key>` /
       `np_album_<key>` に追加で put する (null は "" にする)。
     - private な `statusKey(String)` を削除し、呼び出しを `Sources.statusKey(pkg)` に置き換える。
  5. `Sender.drain` で、`markSuccessfulBatch(batch, response);` の直後に
     `if (response.accepted > 0) prefs.putLong(Prefs.LAST_SENT_AT, System.currentTimeMillis());`
     を足す。
  6. UI からの切り替え手順 (Task 8 で使う) を ScrobbleService のクラスコメントに 2 行で書く:
     「Prefs.sourceEnabledKey を書いてから ACTION_SOURCES を送る。サービスは再起動しない」。
- Acceptance:
  - 既定値 (Prefs に source_enabled_* が無い) で 3 ソース全てに接続し、今と挙動が変わらない。
  - 単体テストと APK ビルドが通る。
  - 実機確認 (Task 8 の後にまとめて行う): ホームで Apple Music を OFF にすると、サービスを
    再起動せずに `link_apple: disabled` になり、他のソースの接続が切れない。ON に戻すと再接続する。
- Verify: `./verify/run_all.sh`

### Task 5: 起動判定を core に切り出し、MainActivity の処理を AppActions に移す

- Files to touch:
  - `scrobbler/test/LaunchRouteTests.java` (新規。先に書く)
  - `scrobbler/test/ScrobblerTests.java` (`LaunchRouteTests.run();` を 1 行足す)
  - `scrobbler/src/com/akscrobble/core/LaunchRoute.java` (新規)
  - `scrobbler/src/com/akscrobble/AppActions.java` (新規)
  - `scrobbler/src/com/akscrobble/ConfigStore.java` (`configFile()` の追加だけ)
  - `scrobbler/src/com/akscrobble/MainActivity.java` (移した処理を呼び出しに置き換える)
- Files NOT to touch: 他の Activity、`ScrobbleService.java`、`Sender.java`、`res/**`、
  `verify/**`
- New dependencies: none
- Steps:
  1. 先に `LaunchRouteTests.java` を書く。`Credentials.resolve(file, prefs, builtinKey,
     builtinSecret)` で入力を作り、次を検査する: key か secret が空なら SETUP。key と secret が
     あって session が空なら AUTH。全てあれば HOME。空白だけの値は空として扱う (resolve の
     trim による)。builtin だけでキーが揃っていれば SETUP ではない。null 引数なら SETUP。
  2. `core/LaunchRoute.java`: `public enum LaunchRoute { SETUP, AUTH, HOME; public static
     LaunchRoute decide(Credentials c) }`。c が null なら SETUP。
  3. `ConfigStore` に `public File configFile()` (`primary()` と同じ File を返す) を足す。既存の
     挙動は変えない。
  4. `AppActions.java` (final class、static メソッドだけ) を作り、MainActivity の次の処理を
     **挙動を変えずに** 移す。通知文言は、今と同じ Strings キーで組み立てた String を返す。
     - `static String importConfig(Context c)`: 今の `importConfig` + `copy` と同じ。返り値は
       MAIN_CONFIG_CREATED / MAIN_CONFIG_LOADED / 失敗時の MAIN_FAILURE(MAIN_OP_CONFIG)。
     - `static boolean hasStorage(Context c)` と `static void requestStorage(Activity a, int requestCode)`
       (今の `granted` / `requestStorage` と同じ判定)。
     - `static String storageResult(Context c)` → MAIN_STORAGE_RESULT(read, write)。
     - `static String requestBatteryExemption(Activity a)`: 遷移したら null、そうでなければ通知文言
       (MAIN_ALREADY_BATTERY_EXEMPT / MAIN_BATTERY_UNSUPPORTED / 失敗)。`isUnsupportedStub` と、
       AK ROM に関するコメントも一緒に移す。
     - `static boolean isBatteryExempt(Context c)`。
     - `static String saveDiagnosticDump(Context c)`: 今と同じ内容と伏せ字で書き、
       MAIN_DIAGNOSTIC_SAVED か失敗の文言を返す。`oneLine` も移す。
     - `static String sendService(Context c, String action)`: START / STOP なら
       SERVICE_ENABLED を書き、API 26 以上の START は startForegroundService、それ以外は
       startService を呼ぶ (今と同じ)。返り値は MAIN_REQUEST_SENT か失敗の文言。
     - `static void setSourceEnabled(Context c, String pkg, boolean enabled)`: Prefs を書く。
       SERVICE_ENABLED が true なら `startService(new Intent(c, ScrobbleService.class)
       .setAction(Const.ACTION_SOURCES))` を try/catch (Throwable は Log.w) で送る。
     - `static String apiKeySource(Context c)` → `ConfigStore.credentials(c).source("api_key")`
       ("config" / "prefs" / "builtin" / "none")。
     - `static void showUpdatePrompt(final Activity a, UpdateDecision decision, final UpdateManifest m)`:
       MainActivity の onCreate にある AlertDialog の組み立てをそのまま移す
       (`isFinishing() || isDestroyed()` の確認も含める)。
     - `private static String failure(Context c, String operationKey, Throwable e)`: Log.e を出してから
       MAIN_FAILURE を返す。
  5. MainActivity は各ボタンから AppActions を呼び、返ってきた文言を `showNotice` に渡すように
     書き換える。画面構成 (ボタン列とステータス欄) はまだ変えない。`apiKeySource()` の
     表示値が "unset" から "none" に変わるのは許容する。
- Acceptance:
  - LaunchRouteTests が全て PASS する。
  - MainActivity の見た目と各ボタンの挙動が、Task 4 の時点と同じ。
  - `MainActivity.java` から `saveDiagnosticDump` / `requestBatteryExemption` / `importConfig` の
    本体が消え、AppActions に 1 か所だけある。
- Verify: `UNIT` コマンドと `./verify/run_all.sh`

### Task 6: セットアップ画面 (SetupActivity) と起動ゲートを追加する

- Files to touch:
  - `scrobbler/src/com/akscrobble/SetupActivity.java` (新規)
  - `scrobbler/AndroidManifest.xml` (activity 登録)
  - `scrobbler/src/com/akscrobble/MainActivity.java` (onCreate 冒頭のゲートだけ)
- Files NOT to touch: `AuthActivity.java`、`CredentialsActivity.java`、`core/**`、`verify/**`
- New dependencies: none
- Steps:
  1. manifest に `<activity android:name="com.akscrobble.SetupActivity"
     android:exported="false" android:label="Setup" />` を足す。
  2. MainActivity.onCreate の `installCrashHandler();` の直後に、次を足す:
     `if (LaunchRoute.decide(ConfigStore.credentials(this)) == LaunchRoute.SETUP) {
     startActivity(new Intent(this, SetupActivity.class)); finish(); return; }`
  3. `SetupActivity extends Activity` を作る。
     - onCreate: `setTitle(Ui.s(this, Strings.SETUP_TITLE));`。続けて
       `Ui.screen(this, "AKScrobble", null, true)` を呼ぶ ("AKScrobble" はアプリ名なので
       リテラルでよい)。
     - body に、上から次を置く。
       a. `Ui.steps(this, step, {SETUP_STEP_KEYS, SETUP_STEP_AUTH, SETUP_STEP_SOURCES})`。step は
          判定結果から決め、SETUP なら 1、AUTH なら 2 にする。再表示時は作り直す
          (steps コンテナを remove して add し直してよい)。
       b. **キー入力グループ** (`keysGroup`、縦 LinearLayout): 見出し SETUP_HEADING_KEYS
          (17sp、太字、FG)、副文 SETUP_GET_KEY_AT("last.fm/api/account/create") (12sp、MUTED、
          selectable)、`Ui.segment` (SETUP_TAB_INPUT / SETUP_TAB_FILE)、その下に 2 つのペインを
          置き、セグメントで VISIBLE と GONE を切り替える。
          - 入力ペイン: "API Key" / "API Secret" のラベル (11sp、MUTED。ラベルは英字なので
            リテラル可) と、`Ui.input(this, Ui.s(SETUP_KEY_HINT), prefs 値, true)`。secret は
            パスワード入力にし、`Ui.checkBox(CRED_SHOW_SECRET)` で表示を切り替える
            (CredentialsActivity と同じ実装)。続けて `Ui.button(SETUP_SAVE_AND_AUTH, primary)`。
            押したら、両方を trim する。どちらかが空なら message に CRED_EMPTY を出す。両方あれば
            Prefs.API_KEY / API_SECRET を保存し、`proceed()` を呼ぶ。
          - ファイルペイン: SETUP_FILE_HELP (12sp、MUTED)、
            `Ui.pathBox("/sdcard/" + Const.CONFIG_DIR + "/" + Const.CONFIG_FILE)`、
            SETUP_FILE_NOTE、`Ui.button(SETUP_MAKE_TEMPLATE, primary)`、
            `Ui.button(SETUP_LOAD_FILE, ghost)`。
            - テンプレート: `AppActions.hasStorage` が false なら requestStorage を呼び、
              SETUP_STORAGE_NEEDED を出す。true なら、`configFile().exists()` で既存か確認してから
              `writeTemplateIfAbsent()` を呼ぶ。新規なら SETUP_TEMPLATE_CREATED(path)、既存なら
              SETUP_TEMPLATE_EXISTS(path) を出す。
            - 読み込み: 権限が無ければテンプレートと同じ扱いにする。あれば
              `AppActions.importConfig(this)` を呼び、続けて判定する。SETUP のままなら
              SETUP_FILE_KEYS_MISSING を出す。そうでなければ `proceed()` を呼ぶ。
       c. **認証グループ** (`authGroup`): 見出し SETUP_HEADING_AUTH、SETUP_AUTH_HELP、
          `Ui.button(CRED_AUTH, primary)` (AuthActivity を開く)、`Ui.button(SETUP_LATER, ghost)`
          (`goHome()`)。
       d. message TextView (12sp、FG。空なら GONE)。
       e. セクション見出し HOME_SEC_SOURCES と `Ui.list`。Sources.displayOrder の 3 行を
          `Ui.row(label, null, Ui.tag(...), SETUP_AFTER_KEYS の値 TextView, null)` で並べ、list
          全体の alpha を 0.4f にする (操作不可の予告表示)。
     - onResume で `refreshRoute()` を呼ぶ。SETUP なら keysGroup を VISIBLE、authGroup を GONE、
       step=1。AUTH なら逆にして step=2。HOME なら `goHome()`。
     - `proceed()`: 判定が AUTH なら AuthActivity を開く。HOME なら `goHome()`。SETUP なら
       何もしない。
     - `goHome()`: `startActivity(new Intent(this, MainActivity.class)
       .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))` を呼んでから `finish()` する。
     - onCreate の最後で、ストレージ権限が無ければ `AppActions.requestStorage(this, 1301)` を呼ぶ。
       `onRequestPermissionsResult` で `refreshRoute()` を呼ぶ (config ファイルがあれば
       ここで先へ進む)。
     - 自前の addButton / button / text ヘルパーを作らず、Ui のものだけを使う。
- Acceptance:
  - アプリのデータを消し、BuildSecrets も config も無い状態で起動すると、ホームではなく
    SetupActivity が出る (実機確認)。
  - 入力で保存すると AuthActivity が開く。認証が成功して戻るとホームへ入る (実機確認)。
  - api_key / api_secret を書いた config.properties を置いて「ファイルを読み込む」を押すと、
    AuthActivity へ進む (実機確認)。
  - 認証画面から戻るキーで戻ると step 2 の表示になり、「あとで(ホームへ)」でホームへ入れる
    (実機確認)。
  - `test_every_activity_localizes_its_title` が SetupActivity を含めて通る。
- Verify: `./verify/run_all.sh`

### Task 7: 設定画面 (SettingsActivity) を追加し、手動の更新確認を用意する

- Files to touch:
  - `scrobbler/src/com/akscrobble/SettingsActivity.java` (新規)
  - `scrobbler/AndroidManifest.xml` (activity 登録)
  - `scrobbler/src/com/akscrobble/update/UpdateChecker.java`
  - `scrobbler/src/com/akscrobble/MainActivity.java` (旧ボタン列の先頭に「設定」ボタンを 1 つ
    足すだけ。Task 8 で消す)
- Files NOT to touch: `UpdateFlow.java`、`core/**`、他の Activity、`verify/**`
- New dependencies: none
- Steps:
  1. `UpdateChecker`: 今の `check` の本体を `private static void fetch(Context app, Callback cb,
     boolean reportAll)` に切り出す。`check(Context, Callback)` の挙動は変えない (一度だけ実行し、
     NONE / REJECTED / 失敗では callback を呼ばない)。
     `public static void checkManually(Context c, Callback cb)` を足す。`checked` フラグを無視して
     毎回実行する。NONE なら `cb.onResult(NONE, manifest)`、REJECTED か例外なら
     `cb.onResult(null, null)` を、main スレッドで呼ぶ。
  2. manifest に `<activity android:name="com.akscrobble.SettingsActivity"
     android:exported="false" android:label="Settings" />` を足す。
  3. `SettingsActivity extends Activity`:
     - onCreate: `setTitle(Ui.s(this, Strings.UI_SETTINGS));`、
       `Ui.screen(this, Ui.s(UI_SETTINGS), Ui.backLink(this), true)`。戻るキーは既定の finish の
       ままにする。
     - body の先頭に notice TextView を置く (Ui.banner の見た目は使わず、背景
       `rounded(SURFACE, 10)`、FG、12sp、selectable。空なら GONE)。AppActions が返す文言を
       ここに出す。
     - セクションと行 (いずれも `Ui.section` + `Ui.list` + `Ui.addRow(Ui.row(...))`):
       - SET_SEC_ACCOUNT:
         - SET_API_KEY。subtitle は SET_KEY_FROM(ソース名)。ソース名は `apiKeySource` が
           config→SET_SOURCE_CONFIG、prefs→SET_SOURCE_PREFS、builtin→SET_SOURCE_BUILTIN、
           none→SET_SOURCE_NONE (none のときは SET_KEY_FROM を付けずに SET_SOURCE_NONE だけを
           出す)。タップで CredentialsActivity を開く。
         - MAIN_AUTH。subtitle は、AUTH_INVALID なら SET_AUTH_INVALID (WARN の点)、USER_NAME が
           空でなければ SET_AUTHED(user) (OK の点)、それ以外は MAIN_UNAUTHENTICATED (OFF の点)。
           タップで AuthActivity を開く。
       - SET_SEC_SCROBBLE: SET_SANITIZE → SanitizeActivity。HOME_SEC_QUEUE → QueueActivity。
         SET_LANGUAGE は trailing に現在値 (LANG_SYSTEM / LANG_JAPANESE / LANG_ENGLISH /
         LANG_KOREAN。13sp、MUTED) を置く。タップで `AlertDialog.setSingleChoiceItems` を出し、
         選ばれたら Prefs.LANGUAGE ("" / "ja" / "en" / "ko") を書いて `recreate()` する。
       - HOME_SEC_DEVICE: MAIN_BATTERY_EXEMPTION → `AppActions.requestBatteryExemption`
         (null 以外を notice に出す)。SET_STORAGE は subtitle を SET_GRANTED / SET_NOT_GRANTED に
         する。タップで、未許可なら requestStorage(1401)、許可済みなら storageResult を notice に
         出す。MAIN_SAVE_DIAGNOSTIC → saveDiagnosticDump。SET_STATUS → AlertDialog で
         `StatusBus.snapshotText()` (空なら "service: stopped/no status") を出す。OK ボタンだけ
         置く (Open question 2)。
       - SET_SEC_VERSION: 1 行。title は versionName (取れなければ "?")、subtitle は
         SET_CHECK_UPDATE。タップで `UpdateChecker.checkManually`。OPTIONAL / REQUIRED なら
         `AppActions.showUpdatePrompt`、NONE なら SET_UP_TO_DATE、null なら SET_UPDATE_FAILED を
         notice に出す。
     - onResume で各 subtitle を作り直す (API キーのソース、認証状態、ストレージ)。定期更新は
       しない。
     - `onRequestPermissionsResult(1401)` で storageResult を notice に出し、subtitle を更新する。
  4. MainActivity の旧ボタン列の先頭に `UI_SETTINGS` ボタンを 1 つ足し、SettingsActivity を開く
     (Task 8 でホームを作り直すまでの仮の入口)。
- Acceptance:
  - 設定画面がモック③の 4 セクション構成で表示され、各行から既存の画面か処理が動く (実機確認)。
  - 「‹ 戻る」と戻るキーの両方で前の画面に戻る (実機確認)。
  - 言語を変えると設定画面の文言がその場で切り替わる (実機確認)。
  - 起動時の自動更新確認 (`check`) が今と同じく 1 回だけ動く。
- Verify: `./verify/run_all.sh`

### Task 8: ホーム画面 (MainActivity) を作り直す

- Files to touch: `scrobbler/src/com/akscrobble/MainActivity.java`
- Files NOT to touch: `AppActions.java` (足りない場合だけ、メソッドを足してよい)、`core/**`、
  `ScrobbleService.java`、他の Activity、`verify/**`
- New dependencies: none
- Steps:
  1. 旧ボタン列、言語ボタン、版数表示、等幅ステータス欄、自前の `addButton` /
     `addLanguageButton(s)` をすべて削除する。onCreate の起動ゲート (Task 6)、
     `installCrashHandler` (UI への表示はやめて Log.e だけにする)、ストレージ要求、
     `UpdateChecker.check(... AppActions.showUpdatePrompt ...)`、onResume の
     `UpdateFlow.resumeIfPermitted(this)` は残す。
  2. `Ui.screen(this, "AKScrobble", Ui.gear(this, 設定を開く), true)` で画面を作る。body の
     上から次を置く。
     a. banner (`Ui.banner`、既定は GONE)。AUTH_INVALID なら HOME_BANNER_AUTH_INVALID、
        `LaunchRoute.decide(credentials) == AUTH` なら HOME_BANNER_NOT_AUTHED を出す。タップで
        AuthActivity を開く。credentials は onResume で 1 回だけ解決し、1 秒更新では読まない。
     b. hero (縦の LinearLayout、背景 `rounded(SURFACE, 12)`、padding 14dp)。
        - 上段: title (16sp、太字。SERVICE_ENABLED なら HOME_SCROBBLING、そうでなければ
          HOME_STOPPED)、subtitle (`Ui.setStatus`。USER_NAME があれば HOME_SENDING_AS(user)、
          無ければ MAIN_UNAUTHENTICATED。点の色は有効かつ認証済みなら OK、それ以外は OFF)、
          右端に `Ui.toggle(SERVICE_ENABLED)`。トグルを変えたら `AppActions.sendService(
          ACTION_START または ACTION_STOP)` を呼ぶ。
        - 1dp の LINE 線の下に、再生中の曲を表示する。小見出しは HOME_NOW_PLAYING(Sources.label)
          (11sp、MUTED)。続けて曲名 (14sp、太字) と "artist — album" (12sp、MUTED) を置く。
          `NowPlaying.pick(Sources.displayOrder(), StatusBus.snapshot())` が null か、
          SERVICE_ENABLED が false なら、HOME_NOTHING_PLAYING だけを出す。
     c. `Ui.section(HOME_SEC_SOURCES)` と list。displayOrder の順に 3 行を置き、各行は
        leading=`Ui.tag(tag, tagColor)`、trailing=`Ui.toggle(prefs.getBoolean(sourceEnabledKey))`
        とする。toggle の変更で `AppActions.setSourceEnabled(this, pkg, checked)` を呼び、表示を
        すぐ更新する。subtitle は `SourceStatus.of(SERVICE_ENABLED, 有効, installed[i],
        StatusBus.get("link_" + statusKey))` を文言と点の色に対応させる:
        CONNECTED→SRC_CONNECTED/OK、CONNECTING→SRC_CONNECTING/WARN、DISCONNECTED→
        SRC_DISCONNECTED/WARN、DISABLED→SRC_DISABLED/OFF、NOT_INSTALLED→SRC_NOT_INSTALLED/OFF、
        STOPPED→SRC_STOPPED/OFF。未インストールでもスイッチは押せる。
     d. `Ui.section(HOME_SEC_QUEUE)` と list。1 行目は title HOME_QUEUE_PENDING(pending)、
        subtitle HOME_QUEUE_DETAIL(dead, 最終送信) とし、タップで QueueActivity を開く。
        最終送信は `Prefs.getLong(LAST_SENT_AT)` を "HH:mm" (当日) か "MM/dd HH:mm" (それ以外) で
        表す。0 なら HOME_NEVER_SENT。dead が 1 以上なら点を WARN、0 なら OK にする。2 行目は
        `Ui.button(MAIN_FLUSH, ghost)` で、押すと `AppActions.sendService(ACTION_FLUSH)` を呼び、
        返った文言を `Toast.LENGTH_SHORT` で出す。
     e. `Ui.section(HOME_SEC_DEVICE)` と list。1 行 (MAIN_BTN_ADB、subtitle HOME_ADB_SUB) を置き、
        タップで AdbActivity を開く。
  3. 更新ループ: 今の Handler / refresh Runnable の仕組みは 1 秒間隔のまま使う。各 tick で
     `StatusBus.snapshot()` を 1 回だけ取り、hero、再生中の曲、ソースの subtitle を更新する。
     setText は前回値と違うときだけ呼ぶ (TextView ごとに前回の String を保持するか、
     `getText().toString().equals` で比べる)。pending / dead の DB 件数は 5 tick に 1 回と
     onResume で取得する (ScrobbleDb を開いて閉じる。今の実装と同じ)。
  4. onResume: 生成時の言語 (`Ui.lang(this)` を onCreate で保持) と違えば `recreate()` して
     return する。installed[] を PackageManager で更新し (NameNotFoundException なら false)、
     banner、スイッチの checked、DB 件数を反映してから更新ループを始める。onPause で止める
     (今と同じ)。
  5. View の数は 60 個以下に収める。画像と Bitmap は使わない。
- Acceptance:
  - ホームがモック②の構成 (歯車、hero、ソース 3 行、送信キュー、端末) で表示される
    (実機確認)。
  - 全体スイッチの ON/OFF でサービスが開始・停止し、OFF のとき各ソースが「停止中」になる
    (実機確認)。
  - ソースのスイッチで該当ソースだけが接続・切断され、状態表示が数秒以内に変わる。未インストールの
    アプリは「未インストール」と出る (実機確認。Task 4 の確認項目もここで行う)。
  - AUTH_INVALID=true にすると上部に赤いバナーが出て、タップで AuthActivity が開く (実機確認)。
  - 再生中の曲名とアーティストがホームに出る (実機確認)。
  - SP2000 で起動からホーム表示まで体感で遅くならず、スクロールが引っかからない (実機確認)。
  - `MainActivity.java` に `addButton` が無い。
- Verify: `./verify/run_all.sh`

### Task 9: API キー画面と認証画面を Ui ヘルパーで統一する

- Files to touch: `scrobbler/src/com/akscrobble/CredentialsActivity.java`、
  `scrobbler/src/com/akscrobble/AuthActivity.java`
- Files NOT to touch: `Ui.java` (足りない部品がある場合だけ、メソッドを足してよい)、
  `core/**`、他の Activity、`verify/**`
- New dependencies: none
- Steps:
  1. CredentialsActivity: `Ui.screen(this, Ui.s(CRED_TITLE), Ui.backLink(this), true)` で画面を
     作る。help (12sp、MUTED、selectable、URL を含む)、"API Key" / "API Secret" のラベルと
     `Ui.input(..., true)`、ソース行 (11sp、MUTED)、`Ui.checkBox(CRED_SHOW_SECRET)`、
     `Ui.button(CRED_SAVE, primary)`、`Ui.button(CRED_AUTH, ghost)`、message (12sp、FG) を置く。
     自前の `input` / `text` / `button` ヘルパーは削除する。保存、ソース表示、Secret 表示切替の
     ロジックは変えない。ソース表示は `ConfigStore.credentials(this).source(...)` を使う形に
     置き換えてよい (表示値の "unset" は "none" になる)。
  2. AuthActivity: `Ui.screen(this, Ui.s(AUTH_TITLE), Ui.backLink(this), false)` で画面を作る
     (WebView を weight 1 で置くため、スクロールさせない)。4 つのボタンは
     AUTH_OPEN_APPROVAL を primary、残りを ghost にして `Ui.button` で作る。UA チェックは
     `Ui.checkBox` で作る。status は `Ui.text(..., 12, Ui.FG)` に selectable を付ける。ボタン
     同士の間隔は body の divider に任せる。WebView の背景色は `Ui.BG` にする。自前の
     `addButton` を削除し、`Color` の import が不要になれば消す。トークン取得、セッション取得、
     設定ファイル取込、PC 用 URL、finish の各ロジックは変えない。
- Acceptance:
  - 2 画面がダークテーマで統一され、上部に「‹ 戻る」が出る (実機確認)。
  - Last.fm 認証を最後まで通せる (WebView で承認すると自動でセッションを取得して閉じる)
    (実機確認)。
  - 2 ファイルに `addButton` / `private Button button(` / `Color.BLACK` が無い。
- Verify: `./verify/run_all.sh`

### Task 10: 送信キュー画面と ADB 画面を Ui ヘルパーで統一する

- Files to touch: `scrobbler/src/com/akscrobble/QueueActivity.java`、
  `scrobbler/src/com/akscrobble/AdbActivity.java`
- Files NOT to touch: `Ui.java` (必要なら足すだけ)、`core/**`、他の Activity、`verify/**`
- New dependencies: none
- Steps:
  1. QueueActivity: `Ui.screen(title QUEUE_TITLE, Ui.backLink, true)`。4 つの操作ボタンを
     `Ui.button` で作る (QUEUE_SEND_NOW を primary、残りを ghost)。2 列に並べる場合は横の
     LinearLayout に weight 1 で置く。キュー一覧の TextView は `Ui.mono` に置き換え、
     `Color.WHITE/BLACK` の直書きを消す。自前の `addButton` を削除する。キューの読み込み、
     送信、dead の再試行と削除 (AlertDialog の確認を含む) のロジックは変えない。
  2. AdbActivity: `Ui.screen(title ADB_TITLE, Ui.backLink, true)`。adb 状態行を `Ui.mono`、
     ADB_PRIMARY_HELP を 16sp の FG テキストにする。primary のコンポーネントは
     `Ui.button(..., true, ...)` で作り、20sp と padding 20dp の強調は残す。secondary の
     トグルと各コンポーネントは ghost にする。電池のヘルプとコマンド (コマンドは `Ui.mono`)、
     ログ欄も `Ui.mono` にする。自前の `text` / `button` ヘルパーと Color の直書きを削除する。
     コンポーネントの探索と起動のロジックは変えない。
- Acceptance:
  - 2 画面がダークテーマで統一される。ADB 画面の主ボタンが一目で分かる大きさのまま残る
    (実機確認)。
  - 2 ファイルに `addButton` / `private Button button(` / `Color.BLACK` / `Color.WHITE` が無い。
- Verify: `./verify/run_all.sh`

### Task 11: サニタイズ画面を Ui ヘルパーで統一する

- Files to touch: `scrobbler/src/com/akscrobble/SanitizeActivity.java`
- Files NOT to touch: `SanitizeStore.java`、`core/Sanitizer.java`、`core/SanitizeConfig.java`、
  他の Activity、`verify/**`
- New dependencies: none
- Steps:
  1. `Ui.screen(title SAN_TITLE, Ui.backLink, true)` で画面を作る。
  2. 全体の有効スイッチは、`Ui.list` 内の 1 行 (`Ui.row` + trailing `Ui.toggle`) にする。
     既定ルールとカスタムルールは、`Ui.section` の見出しと、`Ui.list` に `Ui.checkBox` の行を
     並べる形にする (各 CheckBox の文言構成、つまり id + 改行 + 説明は今のまま。実機で決めた
     レイアウト上の理由がコメントにあるので、コメントは残す)。CheckBox の文字色は FG にする。
  3. カスタムルール行の「編集」と「削除」ボタンは、`Ui.button` の ghost を WRAP_CONTENT 幅で
     使う (LayoutParams を上書きする)。
  4. フォームの EditText は `Ui.input`、エラー文は 12sp の ACCENT、保存ボタンは primary にする。
     Spinner はそのまま使い、背景だけ `Ui.rounded(SURFACE, 8)` にする。プレビュー結果は
     `Ui.mono` にする。
  5. 自前の `button` / `editText` / `addHeading` ヘルパーは Ui の呼び出しに置き換えて削除する
     (`scopeSpinner` のようなこの画面固有の部品は残してよい)。保存、`recreate()`、検証、
     プレビューのロジックは変えない。
- Acceptance:
  - ルールの ON/OFF、追加、編集、削除、プレビューが今と同じく動く (実機確認)。
  - ファイルに `private Button button(` と Color の直書きが無い。
- Verify: `./verify/run_all.sh`

### Task 12: README を新しい導線に合わせ、UI の規約を検証に足す

- Files to touch: `README.md`、`README.ja.md`、`README.ko.md`、
  `verify/test_ui_conventions.py` (新規)、`verify/test_readme_ui_labels.py` (ALLOWED を足す
  必要がある場合だけ)
- Files NOT to touch: `scrobbler/**`、`docs/images/**`、`.github/**`、`verify/run_all.sh`
- New dependencies: none
- Steps:
  1. 3 言語の README で、操作手順を新しい画面構成に書き換える。英語版の該当箇所 (日本語版と
     韓国語版も対応する箇所) は次のとおり。
     - 初回起動で Setup 画面が出ること。**Use a file** (ファイルを置く) タブ →
       `/sdcard/AKScrobble/config.properties` に書く → **Load file** を押す。代替手段は
       **Type in app** → **Save and authorize**。旧 **Load configuration file** on the main screen
       の記述を置き換える。「status area at the bottom of the main screen」の確認手順は、
       Settings の **Detailed status** に変える。
     - 認証: Setup から自動で Last.fm 認証画面へ進むこと。後からは Settings の
       **Last.fm authentication** から開けること。
     - **Start service** の手順を、ホームの全体スイッチ (太字にしない。太字にするなら
       **Scrobbling**) に置き換える。ソースごとのスイッチ (AK Music / Apple Music / Qobuz) と
       状態表示の説明を 1 段落足す。
     - **Enable ADB** はホームの「Device」にあること。**Sanitize settings** は Settings の
       **Title cleanup (sanitize)**、**API key** は Settings の **Last.fm API key**、
       **View queue** はホームの送信キュー行 (または Settings の **Scrobble queue**)、
       **Save diagnostic dump** は Settings にあることを書く。
     - 太字は Strings.java の実在ラベルだけに使う (`verify/test_readme_ui_labels.py`)。
       日本語版は JA 列、韓国語版は KO 列の文言と完全一致させる。どうしても UI ラベルでない
       強調が必要なら ALLOWED に足す。
     - スクリーンショットの参照 (`docs/images/main.png` / `credentials.png` / `sanitize.png` /
       `adb.png`) は、ファイル名も枚数も変えずに残す。画像の差し替えは Task 13 で行うので、
       このタスクでは画像に触れない。alt テキストは新しい画面に合わせて直してよい
       (main.png はホーム画面、credentials.png は API キー画面)。
  2. `verify/test_ui_conventions.py` を作り、次の 3 テストを書く (pytest、標準ライブラリだけを
     使う。既存テストと同じく `ROOT = Path(__file__).resolve().parents[1]` を使う)。
     - `test_activities_use_shared_ui_helpers`: `scrobbler/src/com/akscrobble/*Activity.java` の
       どのファイルにも、正規表現 `private\s+\w+\s+(addButton|button|text|input|editText)\s*\(`
       に一致する定義が無い。
     - `test_activities_do_not_hardcode_colors`: 同じファイル群に `Color.BLACK` / `Color.WHITE` /
       `setBackgroundColor(Color.` が無い。
     - `test_new_screens_registered`: manifest に SetupActivity と SettingsActivity が
       `android:exported="false"` で登録され、`<application>` に
       `android:theme="@style/AKS.Theme"` がある。
  3. 失敗した場合は README 側か、テストの対象外にすべき正当な理由を確認する。Activity の
     ソースは変更しない (scrobbler/** は対象外)。違反が残っていたら、その事実を report に書く。
- Acceptance:
  - `python3 -m pytest verify -q` が全て通る (README のラベル検査と新しい規約テストを含む)。
  - 3 言語の README から、旧ホームのボタン列を前提にした記述 (main screen の Load configuration
    file、Start service ボタン、status area) が無くなる。
- Verify: `python3 -m pytest verify/test_readme_ui_labels.py verify/test_ui_conventions.py -q && ./verify/run_all.sh`

### Task 13: README のスクリーンショットを実機 (SP2000) から adb で撮り直して差し替える

- **前提条件 (満たすまで委譲しない):** このタスクは、SP2000 が ADB 接続されているときにしか
  実行できない。委譲する前に、オーケストレーターが `adb devices` を実行し、`device` 状態の
  端末がちょうど 1 台あることを確認する。未接続、`unauthorized`、または複数台なら、このタスクは
  **待機** にしてユーザーに接続を依頼する。ワーカーが自分で接続を待ったり、再試行したりしては
  いけない。ワーカーのサンドボックスから adb サーバー (tcp 5037) と USB に届かない場合は、
  ワーカーに委譲せず、オーケストレーターが直接 (または operator ロールで) 実行する。
  Task 11 の実機確認と Task 12 が終わっていること。
- Files to touch: `docs/images/main.png`、`docs/images/credentials.png`、
  `docs/images/sanitize.png`、`docs/images/adb.png` (上書きだけ)。README の alt テキストは、
  画面と食い違う場合だけ直す (`README.md`、`README.ja.md`、`README.ko.md`)。
- Files NOT to touch: `docs/images/icon.png`、`scrobbler/**`、`verify/**`、`build/**`、README の
  画像パスとファイル名、端末上の本番アプリ `fm.last.android` (アンインストール、データ消去、
  上書きインストールのいずれも禁止。セッションキー、doze ホワイトリスト、ストレージ権限が
  失われるため)。
- New dependencies: none (使うのは adb、python3 + Pillow 10.x。どちらも開発機に既にある)
- 現状 (調査済み): README 3 言語は同じ 4 枚を共有しており、言語別の画像は無い。どれも 540x960 の
  PNG で、英語 UI で撮られている。サイズは 63 KB から 143 KB。`icon.png` は対象外。新しい 4 枚も
  **英語 UI、540x960、同じファイル名** にそろえる。
  | ファイル | 撮る画面 |
  |---|---|
  | main.png | ホーム (MainActivity) |
  | credentials.png | API キー画面 (設定 → Last.fm API key → CredentialsActivity) |
  | sanitize.png | サニタイズ画面 (設定 → Title cleanup (sanitize)) |
  | adb.png | ADB 画面 (ホーム → Enable ADB) |
- Steps:
  1. 接続を確認する: `adb devices` で `device` が 1 台であること。違えば中止して報告する。
     `adb shell wm size` の値を記録する。
  2. **撮影専用の別パッケージをビルドしてインストールする** (本番アプリと端末上の個人情報に
     触れないため):
     `AKS_SKIP_SECRETS=1 ./build/build_scrobbler.sh com.akscrobble.shots 1 artifacts/shots/shots.apk 0.0.0-shots`
     → `adb install -r artifacts/shots/shots.apk`。BuildSecrets を埋め込まないので、起動すると
     セットアップ画面が出る。本番の `fm.last.android` には何もしない。
  3. **個人情報を写さないための準備:**
     - API キーと secret は、ダミー値 `0123456789abcdef0123456789abcdef` と
       `fedcba9876543210fedcba9876543210` を `adb shell input text` で入力する。本物のキーは使わない。
       Last.fm 認証はしない (ユーザー名と session_key は存在しない状態にする)。
     - 端末上の `/sdcard/AKScrobble/config.properties` は撮影パッケージからも読まれる。本物の
       キーが書かれている場合は、ストレージ権限を **拒否** して撮影パッケージから読ませない
       (ファイルを書き換えたり消したりしない)。その場合はキーがファイル由来にならないことを、
       API キー画面のソース表示 (in-app settings) で確かめる。
     - 再生中の曲: 撮影前に `adb shell am force-stop com.iriver.ak.music`、
       `com.apple.android.music`、`com.qobuz.music` を実行し、プレイヤーを止める。ホームの再生中欄が
       「Nothing is playing」になっていることを確認する。実在の曲名が出てしまう場合は、撮影を中断して
       オーケストレーターに報告する (ユーザーに、公開して問題ない曲を再生してもらうか判断を仰ぐ)。
       黙って塗りつぶして済ませてはいけない。
     - ステータスバーの通知アイコンに個人情報 (メッセージ内容など) が出ていないことを確認する。
       必要なら撮影前に通知を消してもらう。
     - 未認証のため、ホーム上部に「Last.fm is not authorized yet」のバナーが出る。これは
       そのまま写してよい。
  4. **画面遷移:** 非公開 Activity は `am start` で開けないので、タップで遷移する。座標は
     `adb shell uiautomator dump /sdcard/aks-ui.xml && adb pull /sdcard/aks-ui.xml` で得た bounds から
     求める (テキストが一致するノードの中心をタップする)。撮影後に `adb shell rm /sdcard/aks-ui.xml`
     を実行する。手順は次のとおり。
     起動 → セットアップでダミーキーを保存 → 開いた認証画面を戻るキーで閉じる → 「Later (go to home)」
     → 設定 → Language → English → 戻る → ホームで全体スイッチを ON にし、ソース 3 件を ON にする。
  5. 各画面を撮影する: `adb exec-out screencap -p > artifacts/shots/raw-<name>.png`
     (exec-out が使えない端末では `adb shell screencap -p /sdcard/aks-shot.png` →
     `adb pull` → `adb shell rm /sdcard/aks-shot.png`)。撮る画面は上の表の 4 つ。API キー画面では、
     Secret の「表示」チェックを外したままにする。
  6. 縮小と最適化 (`artifacts/shots/` は作業用。コミットしない):
     `python3 -I -c` で Pillow を使い、各 raw 画像を `resize((540, 960), Image.LANCZOS)` で縮小して
     `optimize=True` で保存する。200 KB を超えたら `quantize(colors=256)` してから保存し直す。
     出力先は `docs/images/<name>.png` で、既存ファイルを上書きする。
  7. オーケストレーターが 4 枚を目で確認する。新しい UI であること、英語表記であること、
     本物のキー、secret、session、ユーザー名、実在の曲名、通知内容が写っていないことを確かめる。
  8. 後片付け: `adb uninstall com.akscrobble.shots` (撮影用パッケージだけを消す)、端末上に作った
     一時ファイルの削除、`artifacts/shots/` の削除。
  9. README の alt テキストが画面と合わなければ直す。例: 英語版の `Main screen` は `Home screen`
     でよい。画像パスは変えない。
- Acceptance:
  - `docs/images/{main,credentials,sanitize,adb}.png` の 4 枚が新しい UI (ダーク、Ui ヘルパーの
    見た目) の英語画面になっている。`icon.png` は変わっていない。
  - 4 枚とも 540x960 の PNG で、1 枚 200 KB 以下、4 枚の合計 600 KB 以下 (リポジトリ肥大の
    目安。現状の合計は約 390 KB)。
  - README 3 言語が参照する `docs/images/*` が全て存在する (リンク切れなし)。
  - 個人情報 (本物の API キー、secret、session_key、Last.fm ユーザー名、実在の曲名と
    アーティスト名、通知の内容) が写っていない (オーケストレーターが目視で確認)。
  - 本番アプリ `fm.last.android` に変更が無く、撮影用パッケージ `com.akscrobble.shots` が端末から
    消えている。
  - `test_readme_ui_labels` と `./verify/run_all.sh` が通る。
- Verify:
  ```sh
  python3 -I -c 'import re,pathlib;from PIL import Image;R=pathlib.Path(".");refs={m for f in ("README.md","README.ja.md","README.ko.md") for m in re.findall(r"docs/images/[\w.-]+",(R/f).read_text(encoding="utf-8"))};missing=[r for r in refs if not (R/r).is_file()];assert not missing,missing;names=["main","credentials","sanitize","adb"];sizes=[(R/"docs/images"/(n+".png")).stat().st_size for n in names];dims=[Image.open(R/"docs/images"/(n+".png")).size for n in names];assert all(d==(540,960) for d in dims),dims;assert max(sizes)<=200*1024 and sum(sizes)<=600*1024,sizes;print("images OK",sizes)' && python3 -m pytest verify/test_readme_ui_labels.py -q && ./verify/run_all.sh
  ```
