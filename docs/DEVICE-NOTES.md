# AK ROM device notes (SP2000T / SP3000)

この文書は、A&ultima SP2000T 実機で確認した AK ROM 固有の制約と回避策をまとめたものです。
SP3000 シリーズでも動作確認が完了しており、アプリの動作は変わりません（後述の
[SP3000 シリーズ](#sp3000-シリーズ)を参照）。以下の調査値は SP2000T のものです。
対象機は Android 9（API 28）、Rockchip RK3326、arm64-v8a、画面 1080x1920・480 dpi、
セキュリティパッチ 2019-07-05 です。実機調査時のシリアル番号は `<serial>` と表記します。

## Install path

AK ファームウェアでは、素の `.apk` は端末上では必ず「解析中に問題が発生しました」となり、インストールできません。
MTP で内部ストレージの `OpenService` フォルダへ `.xapk` を置き、Open APP Service から
インストールする経路だけが利用できます。

実機では、`base.apk`、空の config split 1 本、`manifest.json` を含み、
`style="plain"` とした XAPK のインストールに成功しました。投入前に、古い `.xapk` と、
AK のインストーラが同じ場所に作った展開済みフォルダを削除してください。残骸があると
一覧の重複や名前衝突の原因になります。

フル Android ファームウェアに更新した SP3000 では、この経路を使いません。
プレーヤー上の任意のファイルエクスプローラーから、`.xapk` ではなく `.apk` を
インストールしてください。

## Package name whitelist

AK ROM の `PackageInstaller.validateInstallLocked` は package 名を 40 件の whitelist で
強制しています。`com.arn.scrobble` は梱包や署名を変えても
`INSTALL_FAILED_INVALID_APK: Not apk supported` になります。
`fm.last.android` は whitelist に含まれ、実機で未使用だったため AKScrobble が借用しています。

実機から確認した whitelist の全 40 件は次のとおりです。

```text
com.amazon.mp3
com.amazon.ziggy.android
com.apkfab.installer
com.apkpure.installer
com.appgeneration.itunerfree
com.apple.android.music
com.apple.android.music.classical
com.aspiro.tidal
com.audible.application
com.bandcamp.android
com.bbc.globaliplayerradio.international
com.foobar2000.foobar2000
com.google.android.inputmethod.japanese
com.google.android.inputmethod.latin
com.idagio.app
com.iloen.melon
com.ktmusic.geniemusic
com.neowiz.android.bugs
com.netease.cloudmusic
com.now.moov
com.onkyomusic.android
com.pandora.android
com.qobuz.music
com.sirius
com.skysoft.kkbox.android
com.soundcloud.android
com.spotify.music
com.synology.
com.tencent.ibg.joox
deezer.android.app
fm.awa.liverpool
fm.last.android
fm.player
fm.xiami.main
net.nugs.multiband
net.siamdev.nattster.manman
ru.yandex.music
skplanet.musicmate
tunein.player
uk.co.sevendigital.android
```

## Enabling ADB

`com.iriver.tester.factorytool` が実在し、exported な `.DebugSettingActivity` で ADB を
有効化できます。`.UserDebugActivity` は再起動後も ADB を維持するための画面です。
一方、`.PreOtaActivity` は SP2000T には存在しません。

機種や ROM によって Activity 名が異なる可能性があるため、コンポーネント名を
ハードコードせず、exported な Activity を動的に列挙して選ぶべきです。この知見は
実機調査に使った自作ツールで確認しました。

## Battery optimization

AK ROM には `android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` を受け取る Activity がなく、
`cmd package query-activities` は `No activities found` を返します。ただし設定アプリには、
非対応と表示するだけのスタブ
`com.iriver.settings/.warnning.WarnnigUnsupportedActivity` が登録されているため、
`resolveActivity` は非 null を返します。したがって、アプリ内から電池最適化の除外を
要求することは原理的にできません。

唯一の手段は次の ADB コマンドです。doze 自体は有効で、実機では
`mDeepEnabled=true` でした。

```bash
adb shell dumpsys deviceidle whitelist +<package>
```

## Uninstall side effects

署名鍵を変えたビルドを入れるにはアンインストールが必要ですが、実機では
アプリのデータ以外も失われました。

- doze のホワイトリストからパッケージが外れます。`dumpsys deviceidle whitelist` に
  残っていた登録が、アンインストール後は消えていました。ADB での再登録が要ります。
- 付与済みの実行時権限（ストレージ）が取り消されます。
- Last.fm のセッションキーは SharedPreferences にあるため失われ、認証のやり直しが
  必要です。`api_key` / `api_secret` を `config.properties` に置いてあれば、
  そちらは共有ストレージ上なので残ります。

## Media session access

通知リスナー（`NotificationListenerService`）は AK ROM の内部状態で拒否され、利用には
root が必要です。`MediaSessionManager.getActiveSessions()` にも特権が必要です。
一方、`MediaBrowser` は権限なしで接続でき、セッショントークンと再生情報を取得できます。

## Verified players

実機で確認済みなのは次の 3 プレーヤーだけです。

- AK Music: `com.iriver.ak.music`
- Apple Music: `com.apple.android.music`（接続時の root は `__EMPTY_ROOT__`）
- Qobuz: `com.qobuz.music`（サービスは `com.qobuz.android.mobile.component.mediaplayer.MusicService`、接続時の root は `QOBUZ_EMPTY_MEDIA_ROOT`）

`com.amazon.mp3` は `onGetRoot` が null を返すため接続できません。Spotify は
未検証・未対応です。

## Player quirks

AK Music はセッション活性化直後に `PlaybackState.position` が `-1` を返します。
AKScrobble は position を信用せず、単調時計で再生時間を自前積算します。

AK Music の metadata にはアルバム名がないため、media ID を使って MediaStore から
補完します。MediaStore はタグ欠損時にリテラル `<unknown>`
（`MediaStore.UNKNOWN_STRING`）を返します。これを albumArtist として送信すると、Last.fm は
`ignoredCode 1 = "Artist was ignored"` として scrobble 全体を破棄します。
AKScrobble は `AlbumInfo.normalize` でこの値を null に正規化します。

Qobuz の root は未知の呼び出し元向けの空ルート `QOBUZ_EMPTY_MEDIA_ROOT` で、Apple Music の
`__EMPTY_ROOT__` と同種です。セッショントークンは取得でき、`onMetadataChanged` と
`onPlaybackStateChanged` も届きます。呼び出し元が `uses-permission` を 1 つも宣言していないと、
Qobuz は `onGetLibraryRoot` で `IllegalStateException("Caller wasn't found in the system?")`
を投げ、Qobuz のプロセスがクラッシュします。AKScrobble はマニフェストで権限を宣言しているため
該当しません。この前提は `verify/test_manifest_permissions.py` と `verify/test_verify_apk.py` で
固定しています。

Qobuz の metadata には TITLE / ARTIST / ALBUM / DURATION(ms) があり、ALBUM_ARTIST はありません。
MEDIA_ID は UUID 文字列なので AKScrobble 側の mediaId は -1 になります。アルバム補完は AK Music
専用のため影響はありません。`com.qobuz.mobile.TRACK_ISRC` などの独自キーもあります。
PlaybackState は再生が 3、一時停止が 2 です。曲送りでは BUFFERING(6) を経由して 3 に戻り、
position は 0 付近に戻ります。position は正しく返るため、AK Music の -1 問題はありません。
曲送り時に同じ内容の `onMetadataChanged` が 2 回届きますが、PlayTracker は同じ曲として統合します。
BUFFERING 中は再生時間を積算しません。

## WebView

搭載 WebView は `com.android.webview` 83.0.4103.120（Chromium 83）と古いものですが、
Last.fm のログインページは正常に描画でき、端末上で認証操作を完了できました。

## E2E results

実機で確認した結果は次のとおりです。

- `track.updateNowPlaying`: PASS
- `track.scrobble`: `accepted=4`
- オフライン蓄積からネットワーク復帰後の自動送信: 7 秒
- 端末再起動後の自動復帰: PASS
- Qobuz（SP3000、v1.0.2、リリース鍵で上書きインストール）:
  - 上書きインストール後も doze ホワイトリスト、Last.fm 認証、ストレージ権限は維持されました。
  - Qobuz が停止していても接続時にプロセスが起動します（Apple Music などと同じ挙動）。
  - `track.updateNowPlaying`: PASS、`track.scrobble`: `accepted=1`。
  - 一時停止と再開、曲送り（二重送信なし）: PASS。
  - Qobuz を強制終了したあと、AKScrobble を再起動せずに `suspended`、`connecting`、`connected` と戻り、
    再生を再開できました。
  - 全項目を通して Qobuz のクラッシュはありません。

## SP3000 シリーズ

SP3000 シリーズで動作確認が完了しています。アプリの動作は SP2000T と変わりません。

- インストール: AK ファームウェアのままなら SP2000T と同じく Open APP Service の
  `.xapk` を使います。フル Android ファームウェアに更新した場合は、ファイル
  エクスプローラーから `.apk`（`.xapk` ではない）をインストールします。
- AK Music のアルバム情報: AK Music は公開するメタデータにアルバム名を含めません。
  メタデータだけを読むスクロブラー（Pano Scrobbler など）ではアルバム情報を取れませんが、
  AKScrobble は MediaStore から補完するため、正しい値を Last.fm へ送信できます。
