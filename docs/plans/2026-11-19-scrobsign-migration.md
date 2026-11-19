# Last.fm 署名を scrobsign に移し、API キー入力方式 (BYO) を廃止する

要件の出典: GitHub issue kakeru-ikeda/akscrobble#21 と、ユーザー決定 (既存ユーザーの再認証、
config.properties の秘密行の除去)。リリース PR のタイトルは `feat:` で始める (release-please)。

## Open questions

既定の扱いを決めてあるので、回答がなくても計画は成立する。違う場合だけ指示がほしい。

1. **Pro 未加入による拒否の Last.fm エラーコード。** 公式には明示されていない。既定: エラー 10 (Invalid API key)
   と 26 (Suspended API key) を「サービス側の問題」として扱い、キューを保持してバックオフする。実機で別の
   コードを観測したら `LastfmResponse.isServiceProblem()` に足す。
2. **config.properties の扱いが issue とユーザー決定で食い違う。** issue は「キーが書かれていても無視し、
   秘密が端末に残ることを案内する」。ユーザー決定は「移行時に api_key / api_secret / session_key の行を
   削除し、意味のある内容が残らなければファイルごと削除する」。**ユーザー決定を採用する** (Task 4 と Task 9)。
3. **config.properties からの session の取り込み。** 既定: `session_key` と `session_api_key` は
   「config > prefs」の優先順位でフィールドごとに解決する。どちらか一方でも欠ければ未認証とする。
   旧版の `session_key` だけが書かれたファイルは、移行時に行が消える。
4. **「署名サービスが使えない」(403 app_disabled / method_forbidden、404 unknown_app) のときの「送信を止める」の解釈。**
   既定: キューを保持し、既存のバックオフ (最大 1 時間間隔) で再試行する。新しい scrobble の enqueue、
   ネットワーク復帰、手動送信でも再試行する。署名が 1 回でも成功したら表示を消す。完全に止めると、
   サービス側が直った後にユーザーが手動で再開しなければならないため。
5. **Last.fm エラー 4 (Invalid authentication token)。** 既定: 従来どおり再認証が必要な扱い (エラー 9 と同じ)。

## Preconditions (オーケストレーター)

- この worktree には `tools/android-sdk` が無い (`.gitignore` 対象)。最初の委譲の前に、本体チェックアウトの
  SDK を指すシンボリックリンクを作る:
  `ln -s /home/kakeru-ikeda/github/akscrobble/tools/android-sdk /home/kakeru-ikeda/github/akscrobble/feat-last.fm-scrobsign-api/tools/android-sdk`
  (無いと `verify/run_all.sh` が `missing tool` で失敗する)。
- 新しい依存パッケージはない (全タスク共通)。

## Goal

- 端末から `api_key` と `api_secret` を無くす。署名 (`api_sig`) は scrobsign
  (`POST https://scrobsign.sukisuki.club/v1/akscrobble/sign`) から取得し、Last.fm へは従来どおり端末から直接 POST する。
- ユーザーは API キーを用意しない。セットアップは「Last.fm で承認する」と「ソース」の 2 段階にする。
- session は `session_key` と、取得時の `api_key` (`session_api_key`) の組で保存する。署名応答の
  `api_key` が `session_api_key` と違えば session を無効とみなし、再認証を促す。キューは捨てない。
- issue 表 (§5) のエラー分類を実装する。署名の通信失敗・429・5xx はキュー保持とバックオフ。
  403/404 (使えない) は送信を止めて画面に表示する。`invalid_params` は該当アイテムだけを退避する。
  Last.fm エラー 9 は sk を消して再認証を促す。10 と 26 はキューを保持して「サービス側の問題」を表示する。
  13 は署名を取り直して 1 回だけ再試行する。
- 既存ユーザーの移行: 新版の初回起動で一度だけ prefs の `api_key`、`api_secret`、`session_key`、
  `user_name`、`auth_invalid`、`auth_invalid_for` を消す。config.properties から秘密の行を除き、再認証を促す。
  キュー (ScrobbleDb) は残し、再認証の後に送る。
- `BuildSecrets` の生成、`build/secrets.env.example`、`make_release.sh` の secrets 処理を削除する。
- README (ja/en/ko) を新方式に合わせる。署名サービスが `sk` とメタデータを受け取るが保存しないことを書く。

## Non-goals

- scrobsign 側の変更、Scrobcord の対応、旧キーの失効作業。
- バージョン番号と CHANGELOG の更新 (release-please に任せる)。nyanstall での stable 昇格。
- 署名の結果をキャッシュして永続化すること (禁止。キューに署名を保存しない)。
- `Const.USER_AGENT` (Last.fm と更新確認で使う UA) の変更。署名サービス向けの UA だけを新しく作る。
- ScrobbleDb のスキーマ変更。

## Design notes

- **core に置くもの (JVM 単体テスト対象。`android.` の部分文字列を書かない):**
  - `core/Scrobsign`: 署名 API の URL とタイムアウト定数、署名対象パラメータの抽出、JSON リクエストの組み立て。
  - `core/SignResult`: 署名 API の応答とエラーの解析と分類 (`Kind`: OK / RETRYABLE / UNAVAILABLE / INVALID_PARAMS / REJECTED)。
  - `core/Signer` と `core/LastfmTransport` (インターフェース) と `core/LastfmCall`: 署名 → session と
    api_key の組の照合 → Last.fm へ POST → エラー 13 なら 1 回だけ署名を取り直す。auth.getSession の
    署名を token ごとに 1 回だけ取って使い回す。`LastfmClient` はこれに HTTP を差し込むだけの薄い層になる。
    フェイクの Signer と Transport で、使い回し、13 の再試行、値を変えずに送ることをテストできる。
  - `core/LegacyConfig`: config.properties から旧版の秘密の行を除く純粋関数。
- **org.json を core で使わない (issue からの逸脱):** JVM テストのクラスパスにある `android.jar` の
  `org.json` は中身の無いスタブで、呼ぶと `RuntimeException("Stub!")` になる。応答の解析は既存の
  `core/update/MiniJson` を使い、リクエストの JSON は `Scrobsign.requestJson` で組み立てる。
  Android 側 (`SignerClient`) も同じ core の関数を使うので、経路は 1 つになる。
- **QueuePolicy の Outcome を増やす:** `HOLD` (DB を変えずにバックオフ。署名の一時障害)、
  `SIGNER_UNAVAILABLE`、`SERVICE_PROBLEM` (Last.fm 10/26)、`PARTIAL_INVALID` (署名の invalid_params で
  添字が分かる)。HOLD、SIGNER_UNAVAILABLE、SERVICE_PROBLEM は `markRetry` を呼ばない。
  試行回数を消費しないので、長い障害でもアイテムが dead にならない。不採用案: すべて RETRY に寄せる。
  試行回数 10 回 (約 7 時間) で dead になり、「キューを保持」に反する。
- **署名サービスの invalid_request / 405 / 413 / 415 と、添字の無い invalid_params** はクライアント側の不具合
  なので REJECTED (= RETRY 扱い、試行回数を消費) にする。HOLD にするとキューの先頭が永久に詰まる。
- **null または空の値は署名サービスに送る前に弾く:** `LastfmCall` がローカルで
  `SignResult.invalidLocally(params)` (Kind INVALID_PARAMS) を作る。scrobble のバッチでは、その添字の
  アイテムだけが退避される。
- **セッション不一致:** `sk` を含む要求で、署名応答の `api_key` と保存済みの `session_api_key` が違う場合
  (`session_api_key` が空の場合も含む) は、Last.fm に送らずに `LastfmResponse.sessionMismatch()`
  (エラーコード 9 相当) を返す。Sender はこれを AUTH_BLOCKED として扱う。
- **AUTH_BLOCKED の扱い:** prefs の `session_key`、`session_api_key`、`user_name` を消し、`auth_invalid` と
  `auth_invalid_for` (指紋) を立てる。指紋は config.properties 側に古い session が残っている場合に
  無限に再送しないために残す。指紋の計算は削除する `LastfmSig` から `Credentials.fingerprint()` に移す。
- **LaunchRoute:** キーが無くなるので、SETUP の判定を「session が無く、かつセットアップを
  『あとで』で閉じていない」に変える。`decide(Credentials, boolean setupDismissed)`。
  「あとで」と認証完了で `Prefs.SETUP_DISMISSED` を立てる。移行したユーザーはこれが立っていないので、
  起動するとセットアップの認証ステップに案内される。そこで再認証を促す表示を出す。
- **移行は pref フラグで一度だけ実行する:** `migrated_scrobsign` が立っていなければ prefs を消す。
  ファイルの除去は別のフラグ `config_scrubbed` にする。ストレージ権限がある状態で両方の候補ファイルを
  処理し終えたときだけ立てる。権限が後から付いた場合に再試行するため。呼び出し元は
  `MainActivity.onCreate` (起動経路)、`ScrobbleService.onCreate` (起動時の自動開始)、権限付与のコールバック。
- **途中のコミットについて:** Task 5 から Task 9 の間は UI と移行が新方式に揃っていない
  (例: Task 5 の後もセットアップにキー入力欄が残る)。各コミットでビルドとテストは通るが、
  リリースは全タスク完了後に限る。

### 共通検証コマンド (`--verify`)

```sh
./verify/run_all.sh
```

単体テストだけを速く回すコマンド (以降 `UNIT` と呼ぶ):

```sh
bash -c 'set -euo pipefail; source tools/env.sh; T=$(mktemp -d); java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -classpath "$ANDROID_JAR" -d "$T" $(find scrobbler/src scrobbler/test -name "*.java"); java -cp "$T:$ANDROID_JAR" ScrobblerTests'
```

### 全タスク共通の制約

- `scrobbler/src/com/akscrobble/core/**` に部分文字列 `android.` を書かない (`verify/test_scrobbler_purity.py`)。
  `scrobbler/test/*.java` は `com.akscrobble.core.*` 以外を import しない。
- Activity などの非 core ソースに日本語の文字列リテラルを書かない。UI 文言は `core/Strings.java` に
  JA/EN/KO の 3 列で追加する (`verify/test_no_hardcoded_strings.py`、`StringsTests`)。
- 新しい Java テストクラスは `scrobbler/test/` に置き、`static void run()` を持たせる。
  `scrobbler/test/ScrobblerTests.java` の `main` から呼ぶ。アサーションは `TestKit.check` / `TestKit.eq` を使う。
- 秘密を書かない: テストの固定データ、ログ、コメントに本物らしい api_key、api_sig、sk、token を書かない。
  `fake-key`、`fake-sig`、`fakeSession1` のような明らかな偽値を使う。`verify/test_no_secrets.py` は
  `(key|secret|token|sk)\s*=\s*["']?[0-9a-f]{32}` を拒否するので、32 桁の 16 進を値にしない。
  ログでは `api_sig`、`sk`、`token`、`api_key` を必ず `***` に伏せる。
- 「core/ のロジック」を含むタスクはテストを先に書く。手順 1 でテストを書いて UNIT が失敗することを確かめ、
  そのあと実装する。

---

### Task 1: core に署名 API のリクエスト組み立てと応答解析を追加する

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/Scrobsign.java` (新規)
  - `scrobbler/src/com/akscrobble/core/SignResult.java` (新規)
  - `scrobbler/test/ScrobsignTests.java` (新規)
  - `scrobbler/test/ScrobblerTests.java` (`ScrobsignTests.run();` を 1 行追加)
- Files NOT to touch: 上記以外のすべて (特に `LastfmClient.java`、`LastfmRequest.java`、`LastfmResponse.java`、`verify/**`)
- New dependencies: none
- Steps:
  1. 先に `ScrobsignTests.java` を書く (下の Acceptance の全項目)。UNIT が失敗することを確かめる。
  2. `Scrobsign` (final、private コンストラクタ) を実装する:
     - `public static final String SIGN_URL = "https://scrobsign.sukisuki.club/v1/akscrobble/sign";`
     - `public static final int CONNECT_TIMEOUT_MS = 10000; public static final int READ_TIMEOUT_MS = 15000;`
       `public static final int MAX_RESPONSE_BYTES = 64 * 1024;`
     - `public static TreeMap<String, String> signableParams(Map<String, String> lastfmParams)`:
       コピーを返す。キー `method`、`api_key`、`api_sig`、`format`、`callback` を除く。値は変えない (trim しない)。
     - `public static String requestJson(String method, Map<String, String> params)`:
       `{"method":"<m>","params":{"k":"v",...}}` を返す。空白は入れない。params はキーの辞書順。
       文字列のエスケープ規則: `"` は `\"`、`\` は `\\`、U+0000〜U+001F は `\u00XX` (小文字の 16 進 4 桁)。
       それ以外 (非 ASCII を含む) はそのまま出す。`method` が null か空、`params` が null、値が null、
       キーが上の 5 つの禁止キーのどれか、のいずれかなら `IllegalArgumentException` を投げる。
  3. `SignResult` (final、不変) を実装する:
     - `public enum Kind { OK, RETRYABLE, UNAVAILABLE, INVALID_PARAMS, REJECTED }`
     - public final フィールド: `Kind kind; int httpStatus; String apiKey; String apiSig; String errorCode; String message; List<String> invalidParams` (変更不可リスト、空でも null にしない)。
     - `public static SignResult parse(int httpStatus, String body)`:
       - 200: body が JSON オブジェクトで、`api_key` と `api_sig` が空でない文字列 → OK。値は trim も変換もしない。
         それ以外 (JSON でない、欠けている、文字列でない) → RETRYABLE、errorCode `"malformed_response"`。
       - 200 以外: `{"error":{"code","message","details":[{"param","reason"}]}}` を `com.akscrobble.core.update.MiniJson.parse` で読む
         (失敗しても例外を外へ出さない)。code による分類:
         `app_disabled` / `method_forbidden` / `unknown_app` → UNAVAILABLE。
         `invalid_params` → INVALID_PARAMS (`details[].param` を invalidParams に入れる)。
         `rate_limited` / `app_misconfigured` / `internal` → RETRYABLE。
         `invalid_request` / `method_not_allowed` / `payload_too_large` / `unsupported_media_type` → REJECTED。
         code が無い、または未知の場合は HTTP ステータスで決める: 429 と 500 以上 → RETRYABLE。
         403 と 404 → RETRYABLE (プロキシや CDN の HTML の可能性があるため)。その他の 4xx → REJECTED。
         それ以外 → RETRYABLE。errorCode には code (無ければ `"http_<status>"`) を入れる。
     - `public static SignResult transportError(String message)` → RETRYABLE、httpStatus 0、errorCode `"transport"`。
     - `public static SignResult invalidLocally(List<String> params)` → INVALID_PARAMS、httpStatus 0、errorCode `"invalid_params"`。
     - `public boolean isOk()`。
     - `public List<Integer> invalidIndices()`: invalidParams のうち `^[A-Za-z]+\[(\d{1,2})\]$` に一致するものの添字を、
       重複を除いて昇順で返す。
     - `toString()` は kind、httpStatus、errorCode だけを含める (apiKey と apiSig は含めない)。
  4. `ScrobblerTests.main` に `ScrobsignTests.run();` を追加する。
- Acceptance (すべて `ScrobsignTests` で検査する):
  - `signableParams` が `method` / `api_key` / `api_sig` / `format` / `callback` を除き、`" Spaced "` のような値を変えない。
  - `requestJson("auth.getToken", 空 map)` が `{"method":"auth.getToken","params":{}}` と一致する。
  - `requestJson("track.scrobble", {sk, artist[0], track[0], timestamp[0]})` のキーが辞書順
    (`artist[0]`、`sk`、`timestamp[0]`、`track[0]`) に並ぶ。値に含まれる `"` は `\"`、`\` は `\\`、
    U+0001 は `\u0001` になり、日本語 (例: `曲名`) はエスケープされずにそのまま出る。
  - 禁止キーまたは null 値で `IllegalArgumentException` が出る。
  - `parse(200, {"api_key":"fake-key","api_sig":"fake-sig"})` が OK になり、値を保つ。
    `parse(200, "{}")` と `parse(200, "<html>")` は RETRYABLE。
  - 各エラーコード (11 種) が上の分類どおりになる。`invalid_params` の details
    `[{"param":"artist[3]"},{"param":"track[3]"},{"param":"timestamp[12]"},{"param":"sk"}]` から
    `invalidIndices()` が `[3, 12]` になる。
  - `parse(502, "<html>Bad Gateway</html>")` と `parse(404, "")` は RETRYABLE。`parse(418, "")` は REJECTED。
  - `transportError("x").kind == RETRYABLE`。
  - UNIT が通る。`python3 -m pytest verify/test_scrobbler_purity.py verify/test_no_secrets.py -q` が通る。
- Verify: `./verify/run_all.sh`

### Task 2: core に署名付き Last.fm 呼び出し (LastfmCall) を追加する

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/Signer.java` (新規)
  - `scrobbler/src/com/akscrobble/core/LastfmTransport.java` (新規)
  - `scrobbler/src/com/akscrobble/core/LastfmCall.java` (新規)
  - `scrobbler/src/com/akscrobble/core/LastfmRequest.java` (api_key を含まないビルダーと `formBody` を追加。既存メソッドは残す)
  - `scrobbler/src/com/akscrobble/core/LastfmResponse.java` (フィールドとファクトリの追加のみ。分類メソッドは変えない)
  - `scrobbler/test/LastfmCallTests.java` (新規)
  - `scrobbler/test/ScrobblerTests.java` (`LastfmCallTests.run();` を追加)
- Files NOT to touch: `LastfmClient.java`、`Sender.java`、`QueuePolicy.java`、
  `LastfmResponse` の `isAuthFailure` / `isRetryable` / `isPermanent`、`verify/**`
- New dependencies: none
- Steps:
  1. 先に `LastfmCallTests.java` を書く。フェイクの Signer (受け取った method と params を記録し、
     決められた SignResult を順に返す) と、フェイクの Transport (受け取った form body を記録し、
     決められた XML の LastfmResponse を順に返す) を使う。UNIT が失敗することを確かめる。
  2. `public interface Signer { SignResult sign(String method, Map<String, String> params); }`
  3. `public interface LastfmTransport { LastfmResponse post(String formBody); }`
  4. `LastfmRequest` にオーバーロードを追加する (既存の apiKey 付きメソッドと `signedBody` は Task 6 で消すので、ここでは残す):
     - `public static TreeMap<String, String> nowPlaying(TrackInfo track, String sk)`
     - `public static TreeMap<String, String> scrobbleBatch(List<PendingScrobble> items, String sk)`
     - `public static TreeMap<String, String> getToken()`
     - `public static TreeMap<String, String> getSession(String token)`
     - これらは `method` を含み、`api_key` を含まない。それ以外の内容は既存版と同じにする (putOptional の正規化も同じ)。
     - `public static String formBody(Map<String, String> params)`: キーの辞書順に `k=URLEncoder.encode(v, "UTF-8")` を `&` でつなぐ (署名はしない)。
  5. `LastfmResponse` に追加する:
     - public final フィールド `String apiKey` (要求に使った api_key、無ければ null)、
       `SignResult.Kind signerKind` (署名の失敗でなければ null)、`String signerErrorCode`、
       `List<Integer> signerInvalidIndices` (変更不可、空でも null にしない)。
     - `public LastfmResponse withApiKey(String apiKey)`: apiKey だけを差し替えたコピーを返す。
     - `public static LastfmResponse signerFailure(SignResult result)`: status `"failed"`、errorCode 0、
       transportError false、errorMessage `"signing service: " + result.errorCode`、signerKind = result.kind、
       signerInvalidIndices = result.invalidIndices()。
     - `public static LastfmResponse sessionMismatch()`: status `"failed"`、errorCode 9、errorMessage
       `"Session belongs to a different API key"`、transportError false。
     - `public boolean isSignerFailure()` (signerKind != null)。
     - 既存の `parse` と `transportError` の結果は変えない (新フィールドは null または空)。
  6. `LastfmCall` (final) を実装する:
     - `public LastfmCall(Signer signer, LastfmTransport transport, String sessionKey, String sessionApiKey)` (null は "" にする)
     - `public LastfmResponse getToken()` → `execute(LastfmRequest.getToken(), false)`。
     - `public LastfmResponse getSession(String token)`: インスタンス内に `cachedToken` と `cachedSignature` を持つ。
       token が cachedToken と同じで、cachedSignature が OK ならそれを使い、署名サービスを呼ばない。
       違えば署名を取り、OK のときだけキャッシュする。エラー 13 でも署名を取り直さない。
     - `public LastfmResponse updateNowPlaying(TrackInfo track)` / `public LastfmResponse scrobble(List<PendingScrobble> batch)`:
       sessionKey が空なら、署名サービスを呼ばずに既存 `LastfmClient.missingSession()` と同じ XML
       (`<lfm status="failed"><error code="9">Missing session key</error></lfm>`) を parse して返す。
       それ以外は `execute(params, true)`。
     - `private LastfmResponse execute(TreeMap<String, String> params, boolean allowResign)`:
       1. params の値に null か空文字列があれば、そのキーを集めて
          `LastfmResponse.signerFailure(SignResult.invalidLocally(keys))` を返す (署名サービスを呼ばない)。
       2. `signer.sign(params.get("method"), Scrobsign.signableParams(params))`。OK でなければ `signerFailure` を返す。
       3. params が `sk` を含み、`sessionApiKey` が空か `signature.apiKey` と違えば `sessionMismatch().withApiKey(signature.apiKey)` を返す (Transport を呼ばない)。
       4. params のコピーに `api_key` = signature.apiKey と `api_sig` = signature.apiSig を加え、
          `transport.post(LastfmRequest.formBody(copy))` を呼ぶ。結果に `withApiKey(signature.apiKey)` を付ける。
       5. 結果の errorCode が 13 で allowResign が true なら、手順 2 から 1 回だけやり直す (2 回目は allowResign false)。
  7. `ScrobblerTests.main` に `LastfmCallTests.run();` を追加する。
- Acceptance (`LastfmCallTests` で検査する):
  - scrobble で署名サービスへ渡る params に `api_key` と `method` が無い。method 引数は `"track.scrobble"`。
  - Transport に渡る body に `api_key=fake-key` と `api_sig=fake-sig` があり、`artist%5B0%5D=` の値が
    署名に渡した値を URL エンコードしたものと一致する (値が変わっていない)。
  - 応答の `apiKey` が署名応答の api_key になる。
  - `getSession("tok1")` を 2 回呼ぶと Signer は 1 回だけ呼ばれ、Transport は 2 回呼ばれる。
    `getSession("tok2")` で再び Signer が呼ばれる。署名が RETRYABLE だった場合はキャッシュされない。
  - scrobble で Transport が 1 回目にエラー 13 を返すと、Signer と Transport がそれぞれ 2 回呼ばれる。
    2 回目もエラー 13 なら 3 回目は無い (結果の errorCode は 13)。
  - sessionApiKey が `"fake-key-old"` で署名応答が `"fake-key"` なら、Transport は呼ばれず、
    結果の errorCode が 9、`isAuthFailure()` が true になる。sessionApiKey が空の場合も同じ。
  - Signer が UNAVAILABLE を返すと Transport は呼ばれず、`isSignerFailure()` が true、`signerKind == UNAVAILABLE`。
  - `artist[1]` が空文字列のバッチでは Signer が呼ばれず、`signerKind == INVALID_PARAMS`、`signerInvalidIndices == [1]`。
  - sessionKey が空の scrobble は Signer を呼ばず、errorCode 9。
  - 新しい `LastfmRequest.getToken()` が `{method=auth.getToken}` だけを含む。
  - 既存の `LastfmTests` がそのまま通る。UNIT が通る。
- Verify: `./verify/run_all.sh`

### Task 3: キュー送信のエラー分類を issue §5 の表に合わせる

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/LastfmResponse.java` (分類メソッド)
  - `scrobbler/src/com/akscrobble/core/QueuePolicy.java`
  - `scrobbler/src/com/akscrobble/Sender.java`
  - `scrobbler/src/com/akscrobble/Prefs.java` (定数とメソッドの追加)
  - `scrobbler/test/QueuePolicyTests.java`、`scrobbler/test/LastfmTests.java` (分類の期待値を更新、追加)
- Files NOT to touch: `LastfmClient.java`、`LastfmCall.java`、`AuthActivity.java`、`ScrobbleService.java`、すべての Activity、`verify/**`
- New dependencies: none
- Steps:
  1. 先にテストを更新、追加する (Acceptance の項目)。UNIT が失敗することを確かめる。
  2. `LastfmResponse`:
     - `isAuthFailure()`: `!isOk() && !transportError && signerKind == null && (errorCode == 4 || errorCode == 9)`。
     - 新規 `isServiceProblem()`: `!isOk() && signerKind == null && (errorCode == 10 || errorCode == 26)`。
     - `isRetryable()`: `!isOk() && (transportError || errorCode` が 8 / 11 / 13 / 16 / 29 のいずれか
       `|| signerKind == RETRYABLE || signerKind == REJECTED || (signerKind == INVALID_PARAMS && signerInvalidIndices.isEmpty()))`。
     - `isPermanent()` は `!isOk() && !isRetryable()` のまま。
  3. `QueuePolicy`:
     - `public enum Outcome { DONE, RETRY, DEAD, AUTH_BLOCKED, HOLD, SIGNER_UNAVAILABLE, SERVICE_PROBLEM, PARTIAL_INVALID }`
     - `classify(response, attempts)` の判定順: isOk → DONE。signerKind UNAVAILABLE → SIGNER_UNAVAILABLE。
       signerKind RETRYABLE → HOLD。signerKind INVALID_PARAMS かつ添字あり → PARTIAL_INVALID。
       isAuthFailure → AUTH_BLOCKED。isServiceProblem → SERVICE_PROBLEM。
       isRetryable かつ `attempts + 1 < MAX_ATTEMPTS` → RETRY。それ以外 → DEAD。
     - 新規 `public static List<Integer> invalidIndices(LastfmResponse response, int batchSize)`:
       `signerInvalidIndices` のうち `0 <= i < batchSize` のものを昇順で返す。
  4. `Prefs` に追加する: `SESSION_API_KEY = "session_api_key"`、`SIGNER_UNAVAILABLE = "signer_unavailable"`、
     `SERVICE_PROBLEM = "service_problem"`、メソッド `public void remove(String... keys)` (1 回の edit で remove して apply)。
  5. `Sender.drain` のループで新しい Outcome を扱う (既存の DONE / RETRY / DEAD の処理は変えない):
     - HOLD: DB を変えない。`StatusBus.put("sender", "signing service unreachable")`、`shouldRetry = true`、return。
     - SIGNER_UNAVAILABLE: `prefs.putBoolean(Prefs.SIGNER_UNAVAILABLE, true)`、DB を変えない、
       `StatusBus.put("sender", "signing service unavailable")`、`shouldRetry = true`、return。
     - SERVICE_PROBLEM: `prefs.putBoolean(Prefs.SERVICE_PROBLEM, true)`、DB を変えない、
       `StatusBus.put("sender", "Last.fm service problem")`、`shouldRetry = true`、return。
     - PARTIAL_INVALID: `QueuePolicy.invalidIndices(response, batch.size())` が空なら
       `database.markRetry(ids(batch), response.errorMessage)`、`shouldRetry = true`、return。
       空でなければ、その添字のアイテムの id だけを `database.markDead(ids, "Signing service rejected item")` にして `continue`
       (残りは pending のまま、次の takeBatch で送り直される)。
     - AUTH_BLOCKED: 既存の処理 (AUTH_INVALID と AUTH_INVALID_FOR を立てる) に加えて、
       `prefs.remove(Prefs.SESSION_KEY, Prefs.SESSION_API_KEY, Prefs.USER_NAME)` を呼ぶ。指紋は削除前の creds で計算する。
     - DONE (markSuccessfulBatch の後): `SIGNER_UNAVAILABLE` と `SERVICE_PROBLEM` が true なら false に戻す。
- Acceptance:
  - QueuePolicyTests: エラー 9 と 4 → AUTH_BLOCKED。10 と 26 → SERVICE_PROBLEM (以前は AUTH_BLOCKED)。
    13 → RETRY (attempts 0)。16 → RETRY、attempts 9 では DEAD。6 → DEAD。
    `signerFailure(parse(503, app_misconfigured))` → HOLD。attempts 9 でも HOLD。
    `signerFailure(parse(403, app_disabled))` → SIGNER_UNAVAILABLE。`signerFailure(parse(404, unknown_app))` → SIGNER_UNAVAILABLE。
    添字 `artist[2]` 付きの invalid_params → PARTIAL_INVALID、`invalidIndices(r, 2)` は空、`invalidIndices(r, 3)` は `[2]`。
    `sk` だけの invalid_params → RETRY。`payload_too_large` → RETRY。
    `sessionMismatch()` → AUTH_BLOCKED。`transportError` → RETRY。
  - LastfmTests の「code 9 is auth failure」などの既存項目が新しい定義で通る (10/13/26 を auth として期待する項目があれば新しい期待値に直す)。
  - UNIT と `./verify/run_all.sh` が通る。
- Verify: `./verify/run_all.sh`

### Task 4: config.properties の雛形から API キーを外し、旧版の秘密の行を除く関数を追加する

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/ConfigFile.java` (`template()` のみ)
  - `scrobbler/src/com/akscrobble/core/LegacyConfig.java` (新規)
  - `scrobbler/test/ConfigTests.java` (雛形の検査を更新)
  - `scrobbler/test/LegacyConfigTests.java` (新規)
  - `scrobbler/test/ScrobblerTests.java` (`LegacyConfigTests.run();` を追加)
- Files NOT to touch: `Credentials.java`、`ConfigStore.java`、`AppActions.java`、すべての Activity、`verify/**`
- New dependencies: none
- Steps:
  1. 先にテストを書く。UNIT が失敗することを確かめる。
  2. 実装前に、現在の `ConfigFile.template()` のコメント行のうち、API キーの説明に当たる 6 行
     (`# Obtain API credentials at ...` から `# Leave session_key empty; ...` まで) を
     `LegacyConfig` の `private static final String[] LEGACY_COMMENTS` に、`#` を含む trim 後の文字列としてそのまま写す。
  3. `ConfigFile.template()` を次の内容に置き換える (`api_key` と `api_secret` の文字列を含めない):
     ```
     # AKScrobble configuration
     #
     # Last.fm authorization is done in the app. No API key is needed.
     # Leave session_key and session_api_key empty; authorization stores them.
     #
     # Lines starting with # or ; are comments.
     session_key=
     session_api_key=
     user_name=
     ```
  4. `LegacyConfig` (final、private コンストラクタ) を実装する:
     - `public static final class Result { public final String text; public final boolean changed; public final boolean meaningful; }`
     - `public static Result strip(String text)`:
       - null → `("", false, false)`。
       - 先頭の BOM (U+FEFF) は保持する。行は改行文字 (`\n` または `\r\n`) ごと 1 行として扱い、残す行は改行も含めて元のまま出す。
       - 削除する行: (a) trim 後に `#` / `;` で始まらず、`=` を含み、`=` より前を trim して小文字にしたキーが
         `api_key`、`api_secret`、`session_key` のどれか。(b) trim 後の文字列が `LEGACY_COMMENTS` のどれかと完全一致する。
       - `changed` = 1 行以上削除した。`meaningful` = 結果を `ConfigFile.parse` したとき、値が空でない項目が 1 つ以上ある。
- Acceptance:
  - ConfigTests: 雛形が `api_key` も `api_secret` も含まない。`session_key=`、`session_api_key=`、`user_name=` を含む。
    雛形を parse すると `session_key` が空文字列になる。last.fm/api/account/create、Callback URL、
    Load configuration、api_key source、take precedence を検査する旧項目は削除する。
    parse と Credentials.resolve の既存の検査は変えない。
  - LegacyConfigTests:
    - 旧雛形 (Task 4 実施前の `ConfigFile.template()` の全文をテストに文字列として写す) に `api_key=fake-key`、
      `api_secret=fake-secret`、`session_key=fakeSession1` を書いたもの → changed true、meaningful false。
      結果に `fake-` も `fakeSession1` も含まれない。
    - `"api_key=fake-key\r\nsanitize=on\r\n# note\r\n"` → text が `"sanitize=on\r\n# note\r\n"`、changed true、meaningful true。
    - `" API_KEY = fake-key \n"` (大文字、空白) も削除される。`# api_key=fake-key` (コメント) は残る。
    - 秘密の行が無い `"sanitize=on\n"` → changed false、text は入力と同一。
    - BOM 付きの入力で BOM が残る。null → text `""`、changed false。
  - UNIT と `./verify/run_all.sh` が通る。
- Verify: `./verify/run_all.sh`

### Task 5: 署名サービス経由の送信に切り替える (SignerClient、LastfmClient、呼び出し元)

- Files to touch:
  - `scrobbler/src/com/akscrobble/SignerClient.java` (新規)
  - `scrobbler/src/com/akscrobble/LastfmClient.java` (書き換え)
  - `scrobbler/src/com/akscrobble/AuthActivity.java` (`requestToken`、`requestSession`、`saveSession`、`credentials` 確認の削除)
  - `scrobbler/src/com/akscrobble/Sender.java` (LastfmClient の生成と指紋の入力)
  - `scrobbler/src/com/akscrobble/ScrobbleService.java` (`TrackerSink.onNowPlaying` のみ)
  - `scrobbler/src/com/akscrobble/ConfigStore.java` (`credentials()` に session_api_key を追加)
  - `scrobbler/src/com/akscrobble/core/Credentials.java` (`sessionApiKey` の追加と `isComplete` の変更)
  - `scrobbler/test/ConfigTests.java`、`scrobbler/test/LaunchRouteTests.java` (Credentials の変更に合わせる)
- Files NOT to touch: `LastfmRequest.java` の既存メソッドと `LastfmSig.java` (Task 6 で削除)、
  `LaunchRoute.java`、`SetupActivity.java`、`CredentialsActivity.java`、`SettingsActivity.java`、`MainActivity.java`、
  `build/**`、`verify/**`
- New dependencies: none
- Steps:
  1. `Credentials`: public final フィールド `sessionApiKey` を追加する。`resolve` は既存のシグネチャのまま、
     `session_api_key` も `choose("session_api_key", file, prefs, "", sources)` で解決する。
     `isComplete()` を `sessionKey.length() > 0 && sessionApiKey.length() > 0` に変える。
     `apiKey` / `apiSecret` / `source` は残す (Task 7 で削除)。
  2. `ConfigStore.credentials`: `saved.put("session_api_key", prefs.getString(Prefs.SESSION_API_KEY));` を追加する。
  3. `SignerClient implements com.akscrobble.core.Signer`:
     - `public SignerClient(String userAgent)` と `public static SignerClient forContext(Context context)`
       (UA は `"AKScrobble/" + versionName + " (Android " + Build.VERSION.RELEASE + ")"`。versionName は
       `PackageManager.getPackageInfo(getPackageName(), 0).versionName`、取れなければ `"0"`)。
     - `sign(method, params)`: `Scrobsign.requestJson` の結果を UTF-8 で `Scrobsign.SIGN_URL` へ POST する。
       ヘッダーは `Content-Type: application/json; charset=utf-8`、`Accept: application/json`、`User-Agent`。
       タイムアウトは `Scrobsign.CONNECT_TIMEOUT_MS` / `READ_TIMEOUT_MS`。リダイレクトは追わない。
       2xx なら input stream、それ以外は error stream から最大 `Scrobsign.MAX_RESPONSE_BYTES` を読み、
       `SignResult.parse(code, body)` を返す。`IOException` → `SignResult.transportError(message)`。
       `IllegalArgumentException` → `SignResult.invalidLocally(空リスト)`。finally で disconnect。
     - ログは `Log.d(Const.TAG, "sign " + method + " -> " + result)` だけにする (SignResult.toString は秘密を含まない)。body、params、apiSig はログに出さない。
     - `NetThread` からだけ呼ぶ旨の Javadoc を付ける。
  4. `LastfmClient` を書き換える:
     - コンストラクタを `public LastfmClient(Context context, String sessionKey, String sessionApiKey)` にする
       (`apiKey` と `apiSecret` は削除)。内部で `new LastfmCall(SignerClient.forContext(context), transport, sessionKey, sessionApiKey)` を持つ。
       transport は既存の `post` の HTTP 処理 (URL、ヘッダー、タイムアウト、2xx 以外は error stream を読む、
       `LastfmResponse.parse`、IOException → transportError、ログのマスク) を `LastfmTransport` として実装した private な内部クラスまたはラムダ相当にする。
       HTTP ステータスで弾かず、必ず本文を parse する (既存の挙動を維持)。
     - public メソッド: `getToken()`、`getSession(String token)`、`updateNowPlaying(TrackInfo)`、`scrobble(List<PendingScrobble>)` (すべて LastfmCall に委譲)。
       `post(Map)` と `missingSession()` は削除する。`maskedBody` は残し、`api_sig`、`sk`、`api_key`、`token` を伏せる。
     - 同じインスタンスの `getSession` は token ごとの署名キャッシュを共有する、と Javadoc に書く。
  5. `AuthActivity`:
     - フィールド `private LastfmClient client;` を onCreate で `new LastfmClient(this, "", "")` として 1 回だけ作る。
     - `requestToken`: API キー未設定の確認 (`AUTH_CREDENTIALS_MISSING` の表示) を削除する。
       NetThread 上で `client.getToken()`。OK かつ token があれば `approvalUrl = buildApprovalUrl(response.apiKey, token)`。
       token を取り直したら `automaticSessionAttempted = false` にする。
     - `requestSession`: `client.getSession(token)` を使う (自動と手動の再試行で署名が使い回される)。
       成功したら `saveSession(response.sessionKey, response.sessionName, response.apiKey)`。
     - `saveSession(String session, String user, String apiKey)`: `Prefs.SESSION_KEY`、`Prefs.SESSION_API_KEY`、`Prefs.USER_NAME` を保存して `completeAuthentication()`。
     - `completeAuthentication()` で `AUTH_INVALID` に加えて `SIGNER_UNAVAILABLE` と `SERVICE_PROBLEM` も false にする。
     - `importConfig()` と設定ファイル読込ボタンは Task 7 で扱うので、ここでは `Prefs.API_KEY` / `API_SECRET` を書く 2 行だけを削除する。
  6. `Sender`: `new LastfmClient(context, creds.sessionKey, creds.sessionApiKey)` を drain ごとに 1 回作り、ループ内で使う。
     `credentialFingerprint` の入力を `creds.sessionApiKey + "|" + creds.sessionKey` に変える (`LastfmSig.hexMd5` は Task 6 まで使ってよい)。
  7. `ScrobbleService.TrackerSink.onNowPlaying`: `creds.isComplete()` が false なら何もしない。true なら
     `new LastfmClient(ScrobbleService.this, creds.sessionKey, creds.sessionApiKey).updateNowPlaying(outgoing)`。
  8. テスト: ConfigTests の Credentials の検査に `session_api_key` を加え、「complete credentials」は
     session_key と session_api_key の両方がある場合だけ true になるように直す。
     `api_key` / `api_secret` が無くても session の 2 つがあれば complete、のケースを追加する。LaunchRouteTests は通るように最小限だけ直す。
- Acceptance:
  - `grep -rn "apiSecret" scrobbler/src/com/akscrobble/LastfmClient.java scrobbler/src/com/akscrobble/AuthActivity.java scrobbler/src/com/akscrobble/Sender.java scrobbler/src/com/akscrobble/ScrobbleService.java` が何も出さない。
  - `grep -n "signedBody" scrobbler/src/com/akscrobble/LastfmClient.java` が何も出さない。
  - `SignerClient` が `Scrobsign.SIGN_URL`、`requestJson`、`SignResult.parse` を使い、URL やヘッダーに sk や token を載せない。
  - AuthActivity の承認 URL の api_key が `response.apiKey` (署名応答の値) から作られ、アプリ内の定数を使わない。
  - UNIT と `./verify/run_all.sh` が通る (APK のビルドを含む)。
- Verify: `./verify/run_all.sh`

### Task 6: 端末内署名と BuildSecrets を削除する

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/LastfmSig.java` (削除)
  - `scrobbler/src/com/akscrobble/core/LastfmRequest.java` (`signedBody`、apiKey 付きの 4 メソッド、`base(method, apiKey)` を削除し、`base(method)` に整理)
  - `scrobbler/src/com/akscrobble/core/Credentials.java` (`public String fingerprint()` を追加)
  - `scrobbler/src/com/akscrobble/Sender.java` (`credentialFingerprint` を `creds.fingerprint()` に置き換え、`LastfmSig` の import を削除)
  - `scrobbler/src/com/akscrobble/ConfigStore.java` (`builtin()` のリフレクションを削除し、`Credentials.resolve(file, saved, "", "")` にする)
  - `scrobbler/test/LastfmTests.java` (LastfmSig と signedBody の検査を削除し、`formBody` と新ビルダーの検査に置き換え)
  - `scrobbler/test/ConfigTests.java`、`scrobbler/test/LaunchRouteTests.java` (builtin の検査を削除、fingerprint の検査を追加)
  - `build/build_scrobbler.sh` (secrets.env の読込と BuildSecrets の生成、`SRCS+=` 行を削除)
  - `build/make_release.sh` (末尾の `secrets.env` による漏洩検査ブロックと、`AKS_SKIP_SECRETS=1` の前置を削除)
  - `build/secrets.env.example` (削除)
  - `verify/run_all.sh` (`AKS_SKIP_SECRETS=1 ` の前置を削除)
  - `verify/test_no_embedded_credentials.py` (新規)
- Files NOT to touch: `verify/test_no_secrets.py`、`.gitignore`、すべての Activity、`README*`
- New dependencies: none
- Steps:
  1. 先に `verify/test_no_embedded_credentials.py` と Java テストの変更を書く。
  2. `Credentials.fingerprint()`: `MessageDigest` の MD5 で `sessionApiKey + "|" + sessionKey` を UTF-8 で小文字 16 進 32 桁にして返す (`LastfmSig.hexMd5` と同じアルゴリズム)。
  3. 上の Files to touch のとおりに削除と置き換えをする。`LastfmRequest` の公開メソッドは
     `nowPlaying(TrackInfo, String sk)`、`scrobbleBatch(List<PendingScrobble>, String sk)`、`getToken()`、`getSession(String token)`、`formBody(Map)` だけにする。
  4. `make_release.sh` 冒頭のコメント `# Build release APK and XAPK artifacts without embedded API credentials.` は
     `# Build release APK and XAPK artifacts.` にする。
  5. `verify/test_no_embedded_credentials.py`: 次を検査する pytest。
     (a) `scrobbler/` と `build/` 以下のファイルに `BuildSecrets` と `LastfmSig` が現れない。
     (b) `build/secrets.env.example` が存在しない。
     (c) `build/build_scrobbler.sh` と `build/make_release.sh` に `secrets.env` と `AKS_SKIP_SECRETS` が現れない。
     (d) `scrobbler/src/com/akscrobble/core/LastfmRequest.java` に `secret` (大文字小文字を区別しない) が現れない。
- Acceptance:
  - 上の pytest が通る。
  - ConfigTests: `fingerprint()` が 32 桁の小文字 16 進を返し、session_api_key か session_key が違えば値が変わる
    (LastfmTests の MD5 既知ベクトルの検査は LastfmSig と一緒に削除する)。
  - LastfmTests: `formBody` が日本語を `%E6%9B%B2%E5%90%8D` のように UTF-8 でパーセントエンコードし、`api_sig` を含まない。
    新ビルダーの `scrobbleBatch(items, "fakeSession1")` が添字 0 始まりの連番、秒単位の duration、null album の省略を保つ (既存の検査を移す)。
  - `verify/test_no_secrets.py` は変更なしで通る。
  - `./verify/run_all.sh` が通る (APK と XAPK のビルドを含む)。
- Verify: `./verify/run_all.sh`

### Task 7: Credentials を session だけに縮め、API キー画面と取得元の表示を削除する

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/Credentials.java`
  - `scrobbler/src/com/akscrobble/core/LaunchRoute.java`
  - `scrobbler/src/com/akscrobble/ConfigStore.java` (`credentials()`)
  - `scrobbler/src/com/akscrobble/Prefs.java` (`SETUP_DISMISSED` の追加)
  - `scrobbler/src/com/akscrobble/MainActivity.java` (`LaunchRoute.decide` の呼び出し 2 箇所)
  - `scrobbler/src/com/akscrobble/SetupActivity.java` (`route()` と `goHome()` のみ。キー入力 UI の削除は Task 8)
  - `scrobbler/src/com/akscrobble/AuthActivity.java` (設定ファイル読込ボタンと `importConfig()` の削除、`completeAuthentication` の追加処理)
  - `scrobbler/src/com/akscrobble/SettingsActivity.java` (API キー行と取得元表示の削除)
  - `scrobbler/src/com/akscrobble/AppActions.java` (`apiKeySource` の削除、`importConfig` のキー行の削除)
  - `scrobbler/src/com/akscrobble/CredentialsActivity.java` (削除)
  - `scrobbler/AndroidManifest.xml` (CredentialsActivity の `<activity>` を削除)
  - `scrobbler/test/ConfigTests.java`、`scrobbler/test/LaunchRouteTests.java`
- Files NOT to touch: `core/Strings.java` (キーの削除は Task 11)、`README*`、`verify/**`、`Sender.java`、`LastfmClient.java`
- New dependencies: none
- Steps:
  1. 先にテストを書き換える。UNIT が失敗することを確かめる。
  2. `Credentials`: フィールドを `sessionKey` と `sessionApiKey` だけにする。`resolve(Map<String,String> file, Map<String,String> prefs)`
     (フィールドごとに config > prefs、値は trim、null の map は空扱い)。`isComplete()` と `fingerprint()` は残す。
     `apiKey`、`apiSecret`、`source()`、`normalizeField`、builtin の引数を削除する。
  3. `LaunchRoute.decide(Credentials credentials, boolean setupDismissed)`: credentials が null か `!isComplete()` なら
     `setupDismissed ? AUTH : SETUP`。それ以外は HOME。
  4. `Prefs.SETUP_DISMISSED = "setup_dismissed"` を追加する。
  5. `ConfigStore.credentials`: prefs から `session_key` と `session_api_key` だけを読んで `Credentials.resolve(file, saved)` を呼ぶ。
  6. `MainActivity`: onCreate と `refreshBanner` で `LaunchRoute.decide(creds, new Prefs(this).getBoolean(Prefs.SETUP_DISMISSED))` を使う。
  7. `SetupActivity`: `route()` を同じ形にする。「あとで」(`goHome` を呼ぶボタン) を押したときに
     `Prefs.SETUP_DISMISSED` を true にしてから `goHome()` する。
  8. `AuthActivity`: `AUTH_IMPORT_CONFIG` のボタンと `importConfig()` を削除する。`completeAuthentication()` で
     `Prefs.SETUP_DISMISSED` を true にする。
  9. `SettingsActivity`: `keyRow` (`SET_API_KEY` の行) と `updateRows` 内の取得元の表示を削除する。認証行はそのまま残す。
  10. `AppActions`: `apiKeySource` を削除する。`importConfig` から `api_key` / `api_secret` のコピーを削除する。
      `session_key` は、ファイルに `session_key` と `session_api_key` の両方が空でなく書かれている場合だけ、2 つとも prefs にコピーする。
  11. `CredentialsActivity.java` とマニフェストの登録を削除する。`grep -rn CredentialsActivity scrobbler` が何も出さないこと。
  12. テスト: ConfigTests の Credentials 検査を新しい resolve に合わせる (file が優先、空白は未設定、片方だけでは incomplete、
      `api_key` が file にあっても何も変わらない)。LaunchRouteTests: session なしかつ dismissed false → SETUP、
      session なしかつ dismissed true → AUTH、session_key だけ (session_api_key なし) → SETUP、両方あり → HOME (dismissed に関係なく)、null → SETUP。
- Acceptance:
  - `grep -rnE "apiSecret|apiKeySource|BuildSecrets|creds\.apiKey|credentials\.apiKey|Credentials\.resolve\([^)]*,[^)]*,[^)]*\)" scrobbler/src scrobbler/test`
    が何も出さない (`LastfmResponse.apiKey` と `SignResult.apiKey` は残ってよい)。
  - `python3 -m pytest verify -q` が通る (manifest と title の検査、README のラベル検査を含む)。
  - UNIT と `./verify/run_all.sh` が通る。
- Verify: `./verify/run_all.sh`

### Task 8: セットアップを「認証 → ソース」の 2 段階にし、ホームに状態バナーを出す

- Files to touch:
  - `scrobbler/src/com/akscrobble/SetupActivity.java`
  - `scrobbler/src/com/akscrobble/MainActivity.java` (`refreshBanner` とバナーのクリック処理)
  - `scrobbler/src/com/akscrobble/AuthActivity.java` (`completeAuthentication` で `MIGRATION_NOTICE` を消す)
  - `scrobbler/src/com/akscrobble/Prefs.java` (`MIGRATION_NOTICE` の追加)
  - `scrobbler/src/com/akscrobble/core/Strings.java` (キーの追加と値の変更のみ。削除はしない)
- Files NOT to touch: `README*`、`verify/**`、`Sender.java`、`core/` の Strings.java 以外
- New dependencies: none
- Steps:
  1. `Prefs.MIGRATION_NOTICE = "migration_notice"` を追加する (true にするのは Task 9)。
  2. `Strings` にキーを追加する (定数名、キー文字列、ja / en / ko):
     - `SETUP_REAUTH_NOTICE` `"setup.reauth_notice"`:
       「アップデートで Last.fm の認証方式が変わりました。もう一度認証してください。未送信の scrobble は保持され、認証後に送信されます。」/
       "This update changed how AKScrobble signs in to Last.fm. Please authorize again. Unsent scrobbles are kept and will be sent after you authorize." /
       「업데이트로 Last.fm 인증 방식이 바뀌었습니다. 다시 인증해 주세요. 전송되지 않은 scrobble은 보관되며 인증 후 전송됩니다.」
     - `SETUP_SIGNING_NOTE` `"setup.signing_note"`:
       「送信のたびに署名サービス scrobsign.sukisuki.club が要求に署名します。署名サービスは session key と曲の情報を受け取りますが、保存しません。」/
       "Each request is signed by the signing service scrobsign.sukisuki.club. It receives your session key and track details but does not store them." /
       「요청마다 서명 서비스 scrobsign.sukisuki.club가 서명합니다. 서명 서비스는 session key와 곡 정보를 받지만 저장하지 않습니다.」
     - `HOME_BANNER_MIGRATED` `"home.banner_migrated"`:
       「アップデートにより再認証が必要です。タップして認証してください。未送信の scrobble は保持しています。」/
       "This update requires you to authorize again. Tap to authorize. Unsent scrobbles are kept." /
       「업데이트로 다시 인증해야 합니다. 탭하여 인증하세요. 전송되지 않은 scrobble은 보관 중입니다.」
     - `HOME_BANNER_SIGNER_UNAVAILABLE` `"home.banner_signer_unavailable"`:
       「署名サービスを利用できないため送信を止めています。未送信の scrobble は保持しています。」/
       "Sending is paused because the signing service is unavailable. Unsent scrobbles are kept." /
       「서명 서비스를 사용할 수 없어 전송을 멈췄습니다. 전송되지 않은 scrobble은 보관 중입니다.」
     - `HOME_BANNER_SERVICE_PROBLEM` `"home.banner_service_problem"`:
       「Last.fm 側の問題で送信できません。未送信の scrobble は保持し、あとで再送します。」/
       "Last.fm is rejecting requests because of a problem on its side. Unsent scrobbles are kept and will be retried." /
       「Last.fm 측 문제로 전송할 수 없습니다. 전송되지 않은 scrobble은 보관하고 나중에 다시 보냅니다.」
  3. `Strings` の既存キーの値を変える (キーは変えない):
     - `SETUP_STEP_AUTH`: 「1 認証」/ "1 Authorize" /「1 인증」。`SETUP_STEP_SOURCES`: 「2 ソース」/ "2 Sources" /「2 소스」。
     - `SETUP_HEADING_AUTH`: 「Last.fm で認証します」/ "Authorize AKScrobble on Last.fm" /「Last.fm에서 인증합니다」。
     - `SETUP_AUTH_HELP`: 「Last.fm の承認ページで AKScrobble を許可してください。API キーの用意は不要です。」/
       "Allow AKScrobble on the Last.fm approval page. You do not need your own API key." /
       「Last.fm 승인 페이지에서 AKScrobble을 허용하세요. API 키를 따로 준비할 필요가 없습니다.」
     - `SETUP_AFTER_KEYS`: 「認証後」/ "After authorization" /「인증 후」。
     - `AUTH_WEBVIEW_UNAVAILABLE`: 「WebView を利用できません。PC 用 URL を表示して PC で承認してください。」/
       "WebView is unavailable. Show the PC URL and approve on a PC." /「WebView를 사용할 수 없습니다. PC용 URL을 표시해 PC에서 승인하세요.」
     - 変更前に `grep -n "\*\*" README.md README.ja.md README.ko.md` で、変更する値が README の太字に使われていないことを確かめる。
       使われていれば、その値は変えずに作業を止めて報告する。
  4. `SetupActivity`: キー入力ステップを丸ごと削除する (`buildKeysGroup`、`addInputField`、`saveAndProceed`、`makeTemplate`、`loadFile`、
     `apiKey` / `apiSecret` / `keysGroup` / `inputPane` / `filePane` のフィールド、関連 import)。
     `refreshRoute` は HOME ならホームへ行き、それ以外は認証グループを表示する。ステップ表示は
     `Ui.steps(this, 1, {SETUP_STEP_AUTH, SETUP_STEP_SOURCES})`。認証グループの見出しの下に、
     `Prefs.MIGRATION_NOTICE` が true のときだけ `SETUP_REAUTH_NOTICE` を `Ui.WARN` 色 12sp で表示する。
     認証ボタンの下に `SETUP_SIGNING_NOTE` を `Ui.MUTED` 色 11sp で常に表示する。ストレージ権限の要求 (`REQ_STORAGE`) は残す (Task 9 のファイル整理で使う)。
  5. `MainActivity.refreshBanner`: 優先順位は `AUTH_INVALID` → `HOME_BANNER_AUTH_INVALID`、
     route が AUTH かつ `MIGRATION_NOTICE` → `HOME_BANNER_MIGRATED`、route が AUTH → `HOME_BANNER_NOT_AUTHED`、
     `SIGNER_UNAVAILABLE` → `HOME_BANNER_SIGNER_UNAVAILABLE`、`SERVICE_PROBLEM` → `HOME_BANNER_SERVICE_PROBLEM`、いずれでもなければ非表示。
     バナーのクリックは、表示中の文言が認証系 (上の 3 つ) のときだけ AuthActivity を開く。それ以外では何もしない。
     状態はフィールド `bannerOpensAuth` で持つ。`refreshBanner` は既存の 1 秒ごとの `refresh` でも呼び、Sender が立てたフラグが画面に出るようにする。
  6. `AuthActivity.completeAuthentication`: `Prefs.MIGRATION_NOTICE` を false にする。
- Acceptance:
  - `grep -n "API_KEY\|API_SECRET\|EditText" scrobbler/src/com/akscrobble/SetupActivity.java` が何も出さない。
  - `StringsTests` (全キーに ja/en/ko がある、プレースホルダーが一致) と `python3 -m pytest verify -q` が通る。
  - UNIT と `./verify/run_all.sh` が通る。
  - 実機確認 (オーケストレーター): 下の「実機確認」の手順 3 と 7。
- Verify: `./verify/run_all.sh`

### Task 9: 既存ユーザーの一回限りの移行を実装する

- Files to touch:
  - `scrobbler/src/com/akscrobble/LegacyMigration.java` (新規)
  - `scrobbler/src/com/akscrobble/Prefs.java` (定数の追加、`API_KEY` / `API_SECRET` を `LEGACY_API_KEY` / `LEGACY_API_SECRET` に改名)
  - `scrobbler/src/com/akscrobble/ConfigStore.java` (候補ファイルの公開アクセサの追加)
  - `scrobbler/src/com/akscrobble/MainActivity.java` (onCreate の先頭と onRequestPermissionsResult)
  - `scrobbler/src/com/akscrobble/SetupActivity.java` (onRequestPermissionsResult)
  - `scrobbler/src/com/akscrobble/ScrobbleService.java` (onCreate、`new Sender(...)` より前)
  - `scrobbler/src/com/akscrobble/AppActions.java` (診断ダンプのマスク対象)
- Files NOT to touch: `core/**` (Task 4 の `LegacyConfig` を使うだけで変更しない)、`README*`、`verify/**`、`ScrobbleDb.java`
- New dependencies: none
- Steps:
  1. `Prefs`: `API_KEY` → `LEGACY_API_KEY = "api_key"`、`API_SECRET` → `LEGACY_API_SECRET = "api_secret"` に改名する (値は同じ)。
     追加: `MIGRATED_SCROBSIGN = "migrated_scrobsign"`、`CONFIG_SCRUBBED = "config_scrubbed"`。
     改名で参照が壊れた箇所はすべて直す (`grep -rn "Prefs.API_KEY\|Prefs.API_SECRET" scrobbler/src` が空になること)。
  2. `ConfigStore`: `public File[] candidateFiles()` を追加する。primary と secondary (null でなければ) を返す。
  3. `LegacyMigration` (final、private コンストラクタ):
     - `public static synchronized void run(Context context)`: 下の 2 つを順に呼ぶ。例外は Throwable まで捕まえて `Log.w(Const.TAG, "legacy migration failed: " + e.getClass().getSimpleName())` だけにする (値をログに出さない)。
     - prefs の移行 (`MIGRATED_SCROBSIGN` が false のときだけ): `hadLegacy` = `LEGACY_API_KEY`、`LEGACY_API_SECRET`、`SESSION_KEY` のどれかが空でない。
       `prefs.remove(LEGACY_API_KEY, LEGACY_API_SECRET, SESSION_KEY, SESSION_API_KEY, USER_NAME, AUTH_INVALID, AUTH_INVALID_FOR)`。
       `hadLegacy` なら `MIGRATION_NOTICE` を true にする。最後に `MIGRATED_SCROBSIGN` を true にする。
       ScrobbleDb には触れない。
     - ファイルの整理 (`CONFIG_SCRUBBED` が false で、かつ `AppActions.hasStorage(context)` が true のときだけ):
       `candidateFiles()` の各ファイルについて、`isFile()` なら UTF-8 で読み、`LegacyConfig.strip(text)` を呼ぶ。
       `changed` かつ `meaningful` なら結果の text で上書きする。`changed` かつ `!meaningful` なら削除する。
       各ファイルの処理は個別に try/catch する。すべてのファイルを例外なく処理できたときだけ `CONFIG_SCRUBBED` を true にする。
       ファイルの内容と結果をログに出さない (出すのは「scrubbed」「deleted」とファイル名だけ)。
  4. 呼び出し: `MainActivity.onCreate` の `installCrashHandler()` の直後、LaunchRoute の判定より前で `LegacyMigration.run(this)`。
     `MainActivity.onRequestPermissionsResult` と `SetupActivity.onRequestPermissionsResult` の REQ_STORAGE 分岐で `LegacyMigration.run(this)`。
     `ScrobbleService.onCreate` の `super.onCreate()` の直後で `LegacyMigration.run(this)`。
     ファイル I/O は小さいので main スレッドで実行してよい。
  5. `AppActions` の診断ダンプ: マスク対象を `SESSION_KEY`、`SESSION_API_KEY`、`LEGACY_API_KEY`、`LEGACY_API_SECRET` にする。コメントも合わせて直す。
- Acceptance:
  - `grep -rn "Prefs.API_KEY\|Prefs.API_SECRET" scrobbler/src` が何も出さない。
  - `LegacyMigration` が ScrobbleDb を参照しない (`grep -n ScrobbleDb scrobbler/src/com/akscrobble/LegacyMigration.java` が空)。
  - `MIGRATED_SCROBSIGN` が true なら prefs の移行は 2 回目以降に何もしない。`CONFIG_SCRUBBED` はストレージ権限が無いときに true にならない (コードで確認する)。
  - UNIT と `./verify/run_all.sh` が通る。
  - 実機確認 (オーケストレーター): 下の「実機確認」の手順 1〜4。
- Verify: `./verify/run_all.sh`

### Task 10: README (ja/en/ko) を新方式に合わせる

- Files to touch: `README.md`、`README.ja.md`、`README.ko.md`、`verify/test_readme_ui_labels.py` (`ALLOWED` の更新のみ)、
  `docs/images/credentials.png` (削除)
- Files NOT to touch: `scrobbler/**`、`build/**`、他の `verify/**`、`CHANGELOG.md`、`docs/plans/**`
- New dependencies: none
- Steps:
  1. 3 言語で同じ構成に揃えて、次を変える:
     - 冒頭のスクリーンショット表から「API キー設定」の列 (`docs/images/credentials.png`) を削除する。画像ファイルも削除する。
     - 「必要なもの」から Last.fm API キーとシークレット、API アカウント作成ページ、**Callback URL** / **Application homepage** の説明を削除する。
     - インストール手順: API キーの作成、`config.properties` に `api_key` / `api_secret` を書く手順、**アプリで入力** / **ファイルを置く** /
       **ファイルを読み込む** / **保存して認証へ** の説明を削除する。代わりに「初回起動でセットアップ画面が開く →
       **Last.fm 認証へ進む** → Last.fm の承認ページで許可する → ソースを選ぶ」と書く。
     - 新節「署名サービスについて」(en: "About the signing service"、ko: 「서명 서비스에 대하여」): Last.fm への要求の署名 (`api_sig`) は
       開発者が運用する scrobsign (`https://scrobsign.sukisuki.club`) が行う。scrobble の送信そのものは端末から Last.fm へ直接行う。
       署名のために `sk` (session key) と曲のメタデータ (アーティスト、曲名、アルバム、再生時刻など) を受け取るが、**保存しない** ことを
       平文で書く (この強調は太字にしない。太字は UI ラベル専用)。署名サービスが使えないときはホームにバナーが出て、
       未送信の scrobble は保持されることも書く。
     - 「旧版からの更新」節: 更新後の初回起動で再認証が必要になる。未送信の scrobble は保持され、再認証後に送られる。
       保存済みの API キーとシークレットは削除される。`/sdcard/AKScrobble/config.properties` に書かれた `api_key` /
       `api_secret` / `session_key` の行は、ストレージ権限があれば自動で削除される (他の行は残る。残る内容が無ければファイルごと削除)。
     - トラブルシューティング: 設定の **Last.fm API キー** と取得元の説明を削除する。代わりに、バナー
       「署名サービスを利用できない」「Last.fm 側の問題」の意味と対処 (待つ、issue に報告する) を書く。
     - 診断ダンプの説明: マスクされる値を `session_key`、`session_api_key` にする。
     - ビルド節: `build/secrets.env.example` と API 認証情報の埋め込みの説明を削除する。
     - `config.properties` の平文の秘密に関する注意書き (`api_secret` が平文で置かれる、など) を削除する。
  2. 太字 (`**...**`) は `core/Strings.java` の該当言語の列に実在するラベルだけにする。`verify/test_readme_ui_labels.py` の `ALLOWED` から、
     README から消えた強調 (`Callback URL`、`Application homepage`、「推奨: 設定ファイル。」などの設定ファイル/アプリ入力の小見出し 9 件) を削除する。
     root 不要の 3 件は残す。
  3. README に本物らしいキー、sig、sk、token の例を書かない。
- Acceptance:
  - `python3 -m pytest verify/test_readme_ui_labels.py -q` が通る。
  - `grep -n "api_secret\|account/create\|secrets.env\|credentials.png" README.md README.ja.md README.ko.md` の結果が、
    「旧版からの更新」節で削除対象として `api_secret` に言及する行だけになる。
  - 3 言語の節の構成 (見出しの数と順序) が一致する。
  - `./verify/run_all.sh` が通る。
- Verify: `./verify/run_all.sh`

### Task 11: 使われなくなった UI 文言を削除する

- Files to touch: `scrobbler/src/com/akscrobble/core/Strings.java`
- Files NOT to touch: それ以外のすべて
- New dependencies: none
- Steps:
  1. 次の候補の定数それぞれについて `grep -rn "Strings\.<NAME>\b" scrobbler/src scrobbler/test` を実行する。
     参照が 0 件の定数だけ、定数宣言と表の行の両方を削除する:
     `CRED_TITLE`、`CRED_HELP`、`CRED_API_KEY`、`CRED_API_SECRET`、`CRED_SOURCE`、`CRED_SHOW_SECRET`、`CRED_SAVE`、`CRED_EMPTY`、`CRED_SAVED`、
     `SETUP_STEP_KEYS`、`SETUP_HEADING_KEYS`、`SETUP_GET_KEY_AT`、`SETUP_TAB_INPUT`、`SETUP_TAB_FILE`、`SETUP_KEY_HINT`、`SETUP_SAVE_AND_AUTH`、
     `SETUP_FILE_HELP`、`SETUP_FILE_NOTE`、`SETUP_MAKE_TEMPLATE`、`SETUP_LOAD_FILE`、`SETUP_TEMPLATE_CREATED`、`SETUP_TEMPLATE_EXISTS`、
     `SETUP_FILE_KEYS_MISSING`、`SET_API_KEY`、`SET_SOURCE_CONFIG`、`SET_SOURCE_PREFS`、`SET_SOURCE_BUILTIN`、`SET_SOURCE_NONE`、`SET_KEY_FROM`、
     `MAIN_BTN_CREDENTIALS`、`AUTH_CREDENTIALS_MISSING`、`AUTH_IMPORT_CONFIG`、`AUTH_CONFIG_SESSION_MISSING`。
  2. 参照が残っている定数は削除しない (`CRED_AUTH` はセットアップの認証ボタンで使う)。
- Acceptance:
  - 候補のうち参照が 0 件だった定数が `Strings.java` に残っていない。`StringsTests` (定数と表のキーの一致) が通る。
  - `python3 -m pytest verify -q` (README のラベル検査を含む) が通る。
  - `./verify/run_all.sh` が通る。
- Verify: `./verify/run_all.sh`

---

## 実機確認 (ワーカーのタスクではない。オーケストレーターとユーザーが SP3000 で行う)

Task 11 の後に、ADB で接続した SP3000 でまとめて行う。ログやファイルの中身に秘密が含まれる可能性があるので、
出力をコミット、issue、README に貼らない。

1. **更新前の状態を作る:** 旧版 (1.2.1) でログイン済みであることを確かめる。`adb shell svc wifi disable` で
   オフラインにして 2 曲以上を閾値まで再生し、ホームの送信キューが 2 件以上になることを確かめる。
2. **同じ署名鍵で上書きインストールする** (鍵が違うとアンインストールが必要になり、prefs とキューが消えて移行を試せない)。
   インストール済みが release 鍵なら `AKS_KEYSTORE` / `AKS_KS_PASS` を設定して
   `./build/build_scrobbler.sh fm.last.android <現行より大きい versionCode> artifacts/verify/base.apk 1.3.0-dev` を実行し、
   `adb install -r artifacts/verify/base.apk` で入れる。
3. `adb shell am start -n fm.last.android/com.akscrobble.MainActivity` で起動する。セットアップ画面が「1 認証 / 2 ソース」で開き、
   再認証の案内 (SETUP_REAUTH_NOTICE) と署名サービスの説明が出ることを確かめる。
4. `adb shell ls -l /sdcard/AKScrobble/` と `adb shell grep -c -E '^[[:space:]]*(api_key|api_secret|session_key)[[:space:]]*=' /sdcard/AKScrobble/config.properties`
   を実行する。ファイルが無いか、件数が 0 であること (新しい雛形には `session_key=` が空で 1 行あるので、
   雛形が再生成された場合は `grep -E '^session_key=.+'` で値が空であることを確かめる)。
5. **認証:** Last.fm 認証へ進み、承認ページで許可する。ホームに戻り、ユーザー名が表示されることを確かめる。
   `adb logcat -d -s AKSCROB | grep -E 'sign (auth\.getToken|auth\.getSession)'` で署名の成功を確かめる。
6. **オフラインキューの送信:** `adb shell svc wifi enable` の後、ホームの送信ボタンを押す。キューが 0 件になり、
   Last.fm のプロフィールに手順 1 の曲が載ることを確かめる。
7. **Now playing と scrobble:** 再生を始めて Last.fm の「Scrobbling now」に出ること、閾値を過ぎると scrobble されることを確かめる。
   ホームにバナーが出ていないことを確かめる。
8. **オフラインからの復帰:** wifi を切って 1 曲 scrobble し、wifi を戻すと自動で送られることを確かめる。
9. **ログに秘密が無いこと:** `adb logcat -d | grep -cE 'api_sig=[0-9a-f]|sk=[A-Za-z0-9]{6}|token=[A-Za-z0-9]{6}'` が 0 であること。
10. (任意) 署名サービスの疎通を手元で確かめる: `curl -s -o /dev/null -w '%{http_code}\n' https://scrobsign.sukisuki.club/healthz` が 200。
    `curl -s -X POST -H 'Content-Type: application/json' -d '{"method":"auth.getToken","params":{}}' https://scrobsign.sukisuki.club/v1/no-such-app/sign` が `unknown_app` を返す。
