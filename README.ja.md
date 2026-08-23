[English](README.md) | [日本語](README.ja.md) | [한국어](README.ko.md)

# AKScrobble

## このアプリについて

AKScrobble は Astell&Kern デジタルオーディオプレーヤー向けの Last.fm スクロブラーです。
Android 9 を搭載した A&ultima SP2000T で動作確認済みです。ほかの Astell&Kern
モデルではテストしていません。

**root は不要です。** インストールから scrobble まで、すべて素の端末で動作します。
電池最適化のコマンド 1 つのために ADB を有効化する必要がありますが、それも
アプリ内から行えます。

端末ファームウェアのインストール制限を満たすため、このアプリはパッケージ名
`fm.last.android` を使用しています。それによる影響については、
[既知の制限](#既知の制限)を参照してください。

| メイン画面 | サニタイズ設定 | ADB を有効化 |
|---|---|---|
| ![メイン画面](docs/images/main.png) | ![サニタイズ設定](docs/images/sanitize.png) | ![ADB を有効化](docs/images/adb.png) |

スクリーンショットは SP2000T で表示言語を英語にしたものです。

## 対応プレーヤー

以下のプレーヤーで動作確認済みです。

- AK Music (`com.iriver.ak.music`)
- Apple Music (`com.apple.android.music`)

Amazon Music (`com.amazon.mp3`) は `onGetRoot` メソッドが `null` を返すため接続できません。
Spotify と Qobuz は未検証で、現在は対応していません。これら、または別のプレーヤーへの
対応が必要な場合は、[issue を作成](https://github.com/kakeru-ikeda/akscrobble/issues)
してください。要望に応じて対応を検討します。

## 必要なもの

- Last.fm アカウント
- [API アカウント作成ページ](https://www.last.fm/api/account/create)で取得できる
  Last.fm API キーとシークレット。**Callback URL** と **Application homepage** は
  空のままで構いません。AKScrobble はどちらも使用しません
- MTP でプレーヤーに接続できるコンピューター

`adb` が必要なのは、電池最適化に関するコマンドを 1 回実行するときだけです。
それ以外の設定はすべてプレーヤー上、または MTP ストレージ経由で行います。

## インストール（ゼロから scrobble できるまで）

1. [GitHub Releases](https://github.com/kakeru-ikeda/akscrobble/releases)から
   `akscrobble-<version>.xapk` をダウンロードします。
2. プレーヤーを MTP でコンピューターに接続します。ファイルをコピーする前に、
   `OpenService` フォルダーから古い `.xapk` ファイルと、プレーヤーが以前に展開した
   フォルダーをすべて削除してください。残っていると、項目の重複や名前の衝突が
   起きることがあります。
3. ダウンロードした `.xapk` ファイルを `OpenService` フォルダーにコピーします。
4. プレーヤー上で OpenService を開き、一覧から AKScrobble をインストールします。
   OpenService でインストールできるアプリは最大 4 本までです。すでに 4 本入っている
   場合は、いずれかをアンインストールしてから AKScrobble を入れてください。
5. AKScrobble を起動し、上部のボタンで言語を選びます。
6. ストレージ権限を許可します。AKScrobble が MediaStore から AK Music のアルバム名を
   取得するために必要です。
7. Last.fm の API アカウントを作成し、認証情報を AKScrobble に渡します。方法は 2 つ
   ありますが、**設定ファイルの方が圧倒的に楽です**。32 桁のキーをプレーヤーの
   ソフトウェアキーボードで打つのはかなり厳しいためです。

   **推奨: 設定ファイル。** アプリを一度起動すると、内部ストレージに
   `AKScrobble/config.properties` が作られます。MTP で開いて 2 つの値を埋めてください。

   ```properties
   api_key=取得した api_key
   api_secret=取得した api_secret
   ```

   このファイルは認証情報を解決するたびに読まれるので、**ボタンを押す必要はありません**。
   `session_key` は空のままで構いません（認証時にアプリが取得して保存します）。
   このファイルの値は、アプリ内で入力した値より優先されます。

   **代替: アプリ内で入力する。** **API キー** を開いて 2 つの値を貼り付け、保存します。
   フィールドごとに現在どこから解決されているかが表示されるので、意図した値が
   使われているか確認できます。

   ![API キー画面](docs/images/credentials.png)

8. **Last.fm 認証** をタップし、WebView でアクセスを承認して、アプリにセッションを
   取得させます。
9. **サービス開始** をタップします。
10. AKScrobble を電池最適化の対象外にします。コンピューターが必要なのはこの手順だけです。
    アプリで **ADB を有効化** を開き、強調表示された `DebugSettingActivity` ボタンを
    タップして ADB を有効にします。コンピューターでプレーヤーが `adb devices` に
    表示されることを確認し、次を実行します。

    ```sh
    adb shell dumpsys deviceidle whitelist +fm.last.android
    ```

プレーヤー上で `.apk` を直接開こうとしないでください。Astell&Kern ではこのインストール
経路が無効化されているため、必ずパッケージ解析エラーになります。上記の OpenService
`.xapk` 手順を使用してください。

## サニタイズルール

AKScrobble は、メタデータを表示、キューへの追加、Last.fm への送信の前に整形できます。
組み込みルールは以下のとおりです。

| ルール ID | 対象 | 既定値 | 効果 |
| --- | --- | --- | --- |
| `strip-zero-width` | ALL | ON | ゼロ幅文字を削除します。 |
| `fullwidth-space` | ALL | OFF | 全角スペースを通常のスペースに置換します。 |
| `album-ep` | ALBUM | ON | 末尾の ` - EP` を削除します。 |
| `album-single` | ALBUM | ON | 末尾の ` - Single` を削除します。 |
| `album-deluxe` | ALBUM | OFF | 末尾にある括弧付きの Deluxe edition ラベルを削除します。 |
| `album-remaster` | ALBUM | OFF | 末尾にある括弧付きの remaster ラベルを削除します。年または `Version` が含まれる場合も対象です。 |
| `album-bonus` | ALBUM | OFF | 末尾の `(Bonus Track Version)` を削除します。 |
| `collapse-spaces` | ALL | ON | 連続するスペースまたはタブを 1 個のスペースにまとめます。 |
| `trim-ends` | ALL | ON | 先頭と末尾の空白を削除します。 |

**サニタイズ設定** を開くと、組み込みルールを有効または無効にできます。カスタムルールを
追加するには、一意の ID を指定し、対象（`ALL`、`TRACK`、`ARTIST`、`ALBUM`、または
`ALBUM_ARTIST`）を選択して、正規表現と置換文字列を入力してから保存します。ルールに
頼る前に、プレビュー欄でサンプルのメタデータを試し、それぞれの変更結果を確認してください。

安全対策として、置換後の値が空白だけになる場合、AKScrobble は元の値を保持します。

## scrobble の仕組み（開発者向け）

AKScrobble は、権限を必要とせずに `MediaBrowser` を使って各対象アプリの
`MediaBrowserService` に接続し、`MediaController` を通してメタデータと再生状態の
更新を購読します。AK ファームウェアが内部状態によって拒否し、回避には root 権限が
必要となる `NotificationListenerService` は使用しません。特権アクセスが必要な
`MediaSessionManager.getActiveSessions()` も使用しません。

再生時間は `PlaybackState.position` を信用せず、単調増加クロックに基づいて積算します。
AK Music はセッションがアクティブになった直後に `position = -1` を返すことがあるためです。
曲の長さの 50% または 4 分（`Const.SCROBBLE_CAP_MS`）のうち、早いほうに達すると
scrobble します。30 秒未満の曲（`Const.SCROBBLE_MIN_DURATION_MS`）は scrobble しません。

AK Music はメタデータにアルバム名を含めないため、AKScrobble は MediaStore から解決します。
MediaStore が返したリテラル `<unknown>` は `null` に変換します。そうしないと Last.fm が
ignored code 1 で scrobble 全体を拒否するためです。メタデータは SQLite キューに入る前に
サニタイズされます。ネットワーク接続が戻ったときと 5 分ごとにキューを送信します。一時的な
失敗はバックオフ付きで再試行し、恒久的に拒否された項目は `dead` として記録します。

プレーヤーを追加するには、`scrobbler/src/com/akscrobble/core/Const.java` の `TARGETS` に
`{package name, fully qualified MediaBrowserService class name}` を追加します。サービス名は
次のコマンドで調べます。

```sh
adb shell dumpsys package <package>
```

これは対象アプリの `onGetRoot` が接続を受け入れる場合にだけ動作します。Amazon Music は
接続を拒否します。未対応アプリをローカルで動作させられた場合は、
[issue を作成](https://github.com/kakeru-ikeda/akscrobble/issues)するか、
[Pull Request](https://github.com/kakeru-ikeda/akscrobble/pulls)を送ってください。

## トラブルシューティング

### AKScrobble がインストール一覧に表示されない

プレーヤーの `OpenService` フォルダーに、古い `.xapk` ファイルやプレーヤーが以前に
展開したフォルダーがないか確認してください。それらを削除し、現在の `.xapk` だけを
フォルダーにコピーして、OpenService を開き直します。

### パッケージの解析中に問題が発生したと表示される

`.apk` を直接開こうとしていないか確認してください。AK ファームウェアではこの
インストール経路が無効化されています。リリースの `.xapk` をダウンロードし、上記の
手順どおり OpenService からインストールしてください。

### Last.fm 認証に失敗する

**API キー** を開き、各欄に表示されている取得元の行を確認してください。認証情報は
欄ごとに `config`、`prefs`、`builtin` の順で解決されます。
`/storage/emulated/0/AKScrobble/config.properties` に空でない `api_key` または
`api_secret` があると、アプリ内で入力した値より優先されます。保存した値が反映されない
ように見える場合は、取得元の行でこれを確認し、そのファイル内の該当プロパティを空にするか
削除してから、認証を再試行してください。また、両方の認証情報が同じ Last.fm API
アカウントのものであることも確認してください。

### 曲が scrobble されない

アプリを開き、サービスの状態、現在の曲、経過時間、対応プレーヤー、ネットワーク接続、
電池最適化の対象外になっているかを確認してください。30 秒未満の曲は意図的にスキップされます。
現在の曲がまったく表示されない場合は、対応プレーヤーを使用していることを確認し、サービスを
再起動してください。

より詳しい状態を記録するには、**診断ダンプを保存** をタップします。AKScrobble は
`STATUS`、`PREFS`、`QUEUE`、`DEVICE` セクションを含む
`/storage/emulated/0/AKScrobble/diag-*.txt` を書き出します。`PREFS` 内の `session_key` と
`api_secret` の値は `***` にマスクされます。issue に添付する前にファイルを確認してください。

### キュー項目が dead になる

**キューを見る** を開き、記録されたエラーを確認します。`dead` の項目は、一時的な
ネットワーク障害で遅延しているのではなく、恒久的に拒否されています。エラーの指示に従って
無効な認証情報またはメタデータを修正し、キュー画面から dead 項目を再試行または削除します。
問題を報告するときは、診断ダンプからキュー件数とその周辺の状態を確認できます。

## ソースからのビルド

```sh
./tools/fetch_sdk.sh                                   # installs Android SDK under tools/android-sdk/
./build/build_scrobbler.sh fm.last.android 1 out.apk   # base APK
./build/make_release.sh 1 0.1.0                        # apk + xapk under artifacts/release/
./verify/run_all.sh                                    # unit tests + APK/XAPK constraint checks
```

配布する成果物は、明示的に指定した鍵で署名する必要があります。`tools/env.sh` が
フォールバックする開発用の鍵はパスワードがこのリポジトリに載っているため、
配布物の署名に使ってはいけません。リリース鍵は一度だけ作り、以後ずっと同じものを
使い続けてください。ビルド時に次の 2 つを設定します。

```sh
export AKS_KEYSTORE=/secure/path/akscrobble-release.keystore
export AKS_KS_PASS='<リリース鍵のパスワード>'
./build/make_release.sh 20 1.0.0
```

`make_release.sh` はどちらかが未設定なら実行を拒否し、鍵がまだ無い場合は
作成用の `keytool` コマンドを表示します。あとから署名鍵を変えると、
すでにインストールした人が更新できなくなります。


ビルドには Gradle も Android Studio も使用しません。スクリプトから `aapt2`、`javac`、`d8`、
`zipalign`、`apksigner` を直接呼び出し、アプリには `res/` ディレクトリがありません。
AK ファームウェアは v3 署名を受け入れないため、APK は v1 と v2 の署名だけを使用します。

ローカルビルドに API 認証情報を埋め込むには、`build/secrets.env.example` を
`build/secrets.env` にコピーして値を入力します。リリースビルドは意図的にこのファイルを
迂回し、API キーやシークレットを埋め込みません。

## 既知の制限

- AK ファームウェアのインストール許可リストでは掲載されたパッケージ名だけが許可されるため、
  パッケージ名 `fm.last.android` を借用しています。そのため AKScrobble は公式 Last.fm
  アプリと共存できません。
- 異なる鍵で署名された APK では既存インストールを更新できません。インストールする前に、
  古いインストールを一度アンインストールする必要があります。アンインストールで
  消えるものはアプリ本体だけではありません（SP2000T で実測）。
  - Last.fm のセッションが失われ、認証をやり直す必要があります。`api_key` と
    `api_secret` は `config.properties` に置いてあれば残ります。
  - 端末の doze ホワイトリストからパッケージが外れるため、
    `adb shell dumpsys deviceidle whitelist +fm.last.android` を再実行する
    必要があります。
  - ストレージ権限、言語設定、自作のサニタイズルールが初期化されます。
  - 未送信のキューは失われるので、アンインストール前に送信しておいてください。
- 現在対応しているのは AK Music と Apple Music だけです。
- `ScrobbleDb.onUpgrade` はテーブルを削除するため、データベーススキーマが変わる更新では
  未送信のキュー項目がすべて消去されます。
- `/storage/emulated/0/AKScrobble/config.properties` は共有ストレージ上にあるため、
  `READ_EXTERNAL_STORAGE` を持つアプリなら読めます。32 桁のキーを端末で打つのが
  現実的でないためインストール手順ではこのファイルを推奨していますが、
  `api_secret` が平文で置かれることは事実です。気になる場合は、ファイルの値を空にして
  アプリ内で入力してください。その場合はアプリ専用の領域に保存されます。

## ライセンスと免責事項

AKScrobble は MIT License の下で公開されています。Last.fm と提携しておらず、Last.fm が
承認した公式アプリケーションでもありません。パッケージ名 `fm.last.android` は、
AK ファームウェアのインストール制限を満たす目的だけで借用しています。

## ほかの Astell&Kern モデルについて

AKScrobble は root が使えない／不要な端末を対象にしています。SP3000 や SP4000
シリーズをお持ちの場合は、より良い選択肢があります。
[PPPPatrick0 氏の Astell&Kern DAP Root Guide](https://github.com/PPPPatrick0/AstellKern-DAP-Root-Guide) で ADB を通し、
[Pano Scrobbler](https://github.com/kawaiiDango/pano-scrobbler) を
インストールしてください。本アプリより遥かに高機能です。

## スペシャルサンクス

- [PPPPatrick0 氏](https://github.com/PPPPatrick0/AstellKern-DAP-Root-Guide) — Astell&Kern DAP の root ガイド。
  本プロジェクトの調査はここで示された ADB 到達経路から始まりました。

端末固有の調査結果と制約については、
[端末に関する注記](docs/DEVICE-NOTES.md)を参照してください。
