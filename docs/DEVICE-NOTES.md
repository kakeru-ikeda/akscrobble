# AK ROM (SP2000T) device notes

この文書は、A&ultima SP2000T 実機で確認した AK ROM 固有の制約と回避策をまとめたものです。
対象機は Android 9（API 28）、Rockchip RK3326、arm64-v8a、画面 1080x1920・480 dpi、
セキュリティパッチ 2019-07-05 です。実機調査時のシリアル番号は `<serial>` と表記します。

## Install path

素の `.apk` は端末上では必ず「解析中に問題が発生しました」となり、インストールできません。
MTP で内部ストレージの `OpenService` フォルダへ `.xapk` を置き、Open APP Service から
インストールする経路だけが利用できます。

実機では、`base.apk`、空の config split 1 本、`manifest.json` を含み、
`style="plain"` とした XAPK のインストールに成功しました。投入前に、古い `.xapk` と、
AK のインストーラが同じ場所に作った展開済みフォルダを削除してください。残骸があると
一覧の重複や名前衝突の原因になります。

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

実機で確認済みなのは次の 2 プレーヤーだけです。

- AK Music: `com.iriver.ak.music`
- Apple Music: `com.apple.android.music`（接続時の root は `__EMPTY_ROOT__`）

`com.amazon.mp3` は `onGetRoot` が null を返すため接続できません。Spotify と Qobuz は
未検証・未対応です。

## Player quirks

AK Music はセッション活性化直後に `PlaybackState.position` が `-1` を返します。
AKScrobble は position を信用せず、単調時計で再生時間を自前積算します。

AK Music の metadata にはアルバム名がないため、media ID を使って MediaStore から
補完します。MediaStore はタグ欠損時にリテラル `<unknown>`
（`MediaStore.UNKNOWN_STRING`）を返します。これを albumArtist として送信すると、Last.fm は
`ignoredCode 1 = "Artist was ignored"` として scrobble 全体を破棄します。
AKScrobble は `AlbumInfo.normalize` でこの値を null に正規化します。

## WebView

搭載 WebView は `com.android.webview` 83.0.4103.120（Chromium 83）と古いものですが、
Last.fm のログインページは正常に描画でき、端末上で認証操作を完了できました。

## E2E results

実機で確認した結果は次のとおりです。

- `track.updateNowPlaying`: PASS
- `track.scrobble`: `accepted=4`
- オフライン蓄積からネットワーク復帰後の自動送信: 7 秒
- 端末再起動後の自動復帰: PASS
