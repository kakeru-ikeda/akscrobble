[English](README.md) | [日本語](README.ja.md) | [한국어](README.ko.md)

<p align="center"><img src="docs/images/icon.png" alt="AKScrobble icon" width="128"></p>

# AKScrobble

## このアプリについて

AKScrobble は Astell&Kern デジタルオーディオプレーヤー向けの Last.fm スクロブラーです。
Android 9 を搭載した A&ultima SP2000T と、SP3000 シリーズで動作確認済みです。
ほかの Astell&Kern モデルではテストしていません。

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

### SP3000 で使うメリット

AK Music は公開するメタデータにアルバム名を含めないため、そのメタデータだけを読む
スクロブラー（Pano Scrobbler など）ではアルバム情報を取得できません。AKScrobble は
アルバム名を MediaStore から引くので、AK Music の再生を正しいアルバム名のまま
Last.fm へ送信できます。

## 対応プレーヤー

以下のプレーヤーで動作確認済みです。

- AK Music (`com.iriver.ak.music`)
- Apple Music (`com.apple.android.music`)
- Qobuz (`com.qobuz.music`)

Amazon Music (`com.amazon.mp3`) は `onGetRoot` メソッドが `null` を返すため接続できません。
Spotify は未検証で、現在は対応していません。Spotify、または別のプレーヤーへの
対応が必要な場合は、[issue を作成](https://github.com/kakeru-ikeda/akscrobble/issues)
してください。要望に応じて対応を検討します。

Qobuz は `ALBUM_ARTIST` を公開しないため、アルバムアーティストなしで曲を送信します。

## 必要なもの

- Last.fm アカウント
- [API アカウント作成ページ](https://www.last.fm/api/account/create)で取得できる
  Last.fm API キーとシークレット。**Callback URL** と **Application homepage** は
  空のままで構いません。AKScrobble はどちらも使用しません
- MTP でプレーヤーに接続できるコンピューター

`adb` が必要なのは、電池最適化に関するコマンドを 1 回実行するときだけです。
それ以外の設定はすべてプレーヤー上、または MTP ストレージ経由で行います。

## インストール（ゼロから scrobble できるまで）

インストールするファイルは、プレーヤーのファームウェアによって異なります。

- Astell&Kern ファームウェア: 下記の手順 1〜4 のとおり、Open APP Service で
  `.xapk` をインストールします。
- フル Android ファームウェア（SP3000 シリーズ）: プレーヤー上の任意のファイル
  エクスプローラーアプリで `.apk` をインストールします。`.xapk` ではなく、通常の
  `.apk` である点に注意してください。

フル Android ファームウェアの場合は、手順 1〜4 を次の内容に置き換え、手順 5 から
続けてください。それ以降の手順は同じです。

1. [GitHub Releases](https://github.com/kakeru-ikeda/akscrobble/releases)から
   `akscrobble-<version>.apk` をプレーヤーにダウンロードするか、MTP でコピーします。
2. プレーヤー上でファイルエクスプローラーアプリを開き、`.apk` をタップして
   インストールします。Android から求められたら、そのファイルエクスプローラーに
   アプリのインストールを許可してください。

Astell&Kern ファームウェアの場合は、以下の手順をすべて実行してください。

1. [GitHub Releases](https://github.com/kakeru-ikeda/akscrobble/releases)から
   `akscrobble-<version>.xapk` をダウンロードします。
2. プレーヤーを MTP でコンピューターに接続します。ファイルをコピーする前に、
   `OpenService` フォルダーから古い `.xapk` ファイルと、プレーヤーが以前に展開した
   フォルダーをすべて削除してください。残っていると、項目の重複や名前の衝突が
   起きることがあります。
3. ダウンロードした `.xapk` ファイルを `OpenService` フォルダーにコピーします。
4. プレーヤー上で Open APP Service を開き、一覧から AKScrobble をインストールします。
   Open APP Service でインストールできるアプリは最大 4 本までです。すでに 4 本入っている
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

   そのあと、メイン画面の **設定ファイル読込** を押してください。`session_key` は
   空のままで構いません（認証時にアプリが取得して保存します）。このファイルの値は、
   アプリ内で入力した値より優先されます。

   反映されたかは、メイン画面下部のステータス表示で確認できます。
   `api_key source:` が `config` になり、手順 8 のあと `auth:` に Last.fm の
   ユーザー名が出れば成功です。`api_key source:` が `unset` のままなら
   ファイルが読めていないので、置き場所と綴りを確認して
   **設定ファイル読込** を押し直してください。

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

Astell&Kern ファームウェアでは、プレーヤー上で `.apk` を直接開こうとしないでください。
このインストール経路は無効化されているため、必ずパッケージ解析エラーになります。
上記の Open APP Service `.xapk` 手順を使用してください。これは Astell&Kern
ファームウェアだけの話で、フル Android ファームウェアには当てはまりません。

## 自己更新

AKScrobble は起動時に 更新サーバーへ更新を確認します。更新を承諾すると、APK の署名と
SHA-256 を検証してからダウンロードとインストールを行います。AK ファームでは自己更新が
使えない可能性が高く、失敗した場合はアプリ内の案内に従って Releases から `.xapk` を
ダウンロードし、Open APP Service でインストールしてください。自己更新に対応した最初の
バージョンは、上記の手順で手動インストールする必要があります。

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
フォルダーにコピーして、Open APP Service を開き直します。

### パッケージの解析中に問題が発生したと表示される

Astell&Kern ファームウェアで `.apk` を直接開こうとしていないか確認してください。
このファームウェアではインストール経路が無効化されています。リリースの `.xapk` を
ダウンロードし、上記の手順どおり Open APP Service からインストールしてください。
フル Android ファームウェアでは、`.apk` をファイルエクスプローラーから通常どおり
インストールできます。

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

通常のリリースでは release-please を使います。`feat:` や `fix:` などの Conventional Commits を
`main` に push するとリリース PR が更新されます。その PR を merge すると APK と XAPK の
ビルド・署名、証明書の照合、GitHub Release への添付、更新サーバーへの APK 登録が行われます。
更新サーバーの管理画面で `stable` への昇格を手動で行います。

緊急リリースでは Android SDK をインストールし、既存のリリーススクリプトを使います。
配布物には明示的に指定した鍵で署名してください。`tools/env.sh` が使う開発用キーストアは
パスワードがリポジトリに含まれているため、配布物には絶対に使わないでください。
リリース鍵は一度作成したら、すべてのリリースで同じものを使います。

```sh
./tools/fetch_sdk.sh
export AKS_KEYSTORE=/secure/path/akscrobble-release.keystore
export AKS_KS_PASS='<リリース鍵のパスワード>'
./build/make_release.sh 1000002 1.0.2
./verify/run_all.sh
```

`make_release.sh` はどちらかが未設定なら実行を拒否し、鍵がまだ無い場合は
作成用の `keytool` コマンドを表示します。あとから署名鍵を変えると、
すでにインストールした人が更新できなくなります。

バージョンコードは `build/ci_version_code.sh` が `MAJOR*1000000 + MINOR*1000 + PATCH` で
計算します。たとえば `1.0.2` のコードは `1000002` です。


ビルドには Gradle も Android Studio も使用しません。スクリプトから `aapt2`、`javac`、`d8`、
`zipalign`、`apksigner` を直接呼び出し、ランチャーアイコンの
リソース（`scrobbler/res`）は `aapt2` でコンパイルしています。
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
- 現在対応しているのは AK Music、Apple Music、Qobuz です。
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

AKScrobble は SP2000T と SP3000 シリーズで動作確認済みで、どちらでも動作は
同じです。SP4000 シリーズではテストしていません。

より高機能なスクロブラーが欲しく、ADB を通すことをいとわない場合は、
[PPPPatrick0 氏の Astell&Kern DAP Root Guide](https://github.com/PPPPatrick0/AstellKern-DAP-Root-Guide) と
[Pano Scrobbler](https://github.com/kawaiiDango/pano-scrobbler) を参照してください。
ただし Pano Scrobbler は AK Music のアルバム情報を取得できません。詳しくは
[SP3000 で使うメリット](#sp3000-で使うメリット)を参照してください。

## スペシャルサンクス

- [PPPPatrick0 氏](https://github.com/PPPPatrick0/AstellKern-DAP-Root-Guide) — Astell&Kern DAP の root ガイド。
  本プロジェクトの調査はここで示された ADB 到達経路から始まりました。

端末固有の調査結果と制約については、
[端末に関する注記](docs/DEVICE-NOTES.md)を参照してください。
