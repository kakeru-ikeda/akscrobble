# akscrobble の nyanstall 対応(CI 署名ビルド、取り込み、自己更新)

> 出典: `/home/server/github/nyanstall/docs/design/2026-10-31-nyanstall-design.md` の Task 13〜16(対象リポ `kakeru-ikeda/akscrobble`)。
> 本計画では、タスク番号を 1〜4 に振り直した(Task 1 = 設計書 Task 13、Task 2 = 14、Task 3 = 15、Task 4 = 16)。ランナーは `### Task N:` の最大番号までを 1 から順に数えるため、13 から始めると存在しない 1〜12 を探してしまう。

## 背景

- nyanstall は、配布物のバージョン管理と自己更新の共通基盤である。akscrobble は Phase 1 で最初に乗せる Android アプリ(Java 製、Gradle なし、public リポ)である。
- 現行のリリースは、手元で `build/make_release.sh <versionCode> <versionName>` を実行して作っている。最新は v1.0.2 で、versionCode は 102 である。Release asset は `akscrobble-1.0.2.apk` と `akscrobble-1.0.2.xapk` の 2 つ。
- 署名
  - 鍵束 `nyanstall-keys/akscrobble/android-release.tar.age` は R2 にアップロード済み。中身は `release.keystore` と `signing.env` の 2 ファイルである。`signing.env` には `AKS_KS_PASS` と `AKS_KS_ALIAS=akscrobble` が書いてある。
  - 復号には `tsuki-neko-com/workflows/actions/nyanstall-key@v1` を使う。workflows リポは PUBLIC で、`v1` は ee683e4(nyanstall-publish と nyanstall-key を追加したコミット)まで進めてある。action の inputs は `app`、`purpose`、`out-dir`、`api-token`、`account-id`、`age-identity`、output は `dir` である。
  - 既存の `build/make_release.sh` は `AKS_KEYSTORE`(keystore のパス)と `AKS_KS_PASS` を要求する。`tools/env.sh` は `AKS_KS_ALIAS` を読む(既定値は `akscrobble`)。
  - release 証明書の SHA-256 は `de8bae26d0744d53e9ad8b3418748a68378f8c7c5240fceb9afcb05d8ac0d6af` で、nyanstall 側にも `signer_sha256` として登録済みである。
- 公開
  - `tsuki-neko-com/workflows/.github/workflows/nyanstall-publish.yml@v1` を使う。inputs は `app`、`tag`、`mode=android`、`asset-pattern`(既定 `*.apk`)、`version-code` である。secrets として `NYANSTALL_CF_API_TOKEN`、`NYANSTALL_CF_ACCOUNT_ID`、`NYANSTALL_ACCESS_CLIENT_ID`、`NYANSTALL_ACCESS_CLIENT_SECRET` を明示宣言している。
  - Release は release-please で作る。
  - versionCode は方式 A(`MAJOR*1000000 + MINOR*1000 + PATCH`)で採番する。v1.0.2 の方式 A の値は 1000002 で、現行の 102 より大きいので問題ない。
- akscrobble の GitHub secret は、`NYANSTALL_CF_API_TOKEN`、`NYANSTALL_CF_ACCOUNT_ID`、`NYANSTALL_ACCESS_CLIENT_ID`、`NYANSTALL_ACCESS_CLIENT_SECRET`、`NYANSTALL_AGE_IDENTITY` を登録済みである。
- 公開 API は `https://nyanstall.sukisuki.club` で、`GET /v1/akscrobble/stable/latest?current_code=N` を呼ぶ。レスポンスの形は設計書 D-14 による。
- Android SDK は `tools/fetch_sdk.sh` で `tools/android-sdk/` に取得する(gitignore 済み)。中身は build-tools 34.0.0 と platforms android-28 である。

## 設計書から実態に合わせて直した点

1. **検証環境の前提**
   - `./verify/run_all.sh` は最初に `tools/env.sh` を読み込む。`env.sh` は、aapt2、d8、apksigner、zipalign、android.jar のどれかが無いと `exit 1` する。そのため、SDK が無いと JVM のユニットテストすら走らない。
   - さらに、run_all は JDK(`java`、`keytool`)と Python パッケージ(`pytest`、`androguard` 4.x とその依存の `loguru`)を使う。
   - 現在のホストには、JDK、pytest、androguard、`tools/android-sdk` のどれも無い。
   - SDK を取得した後なら、APK のビルドも含めて run_all はオフラインで動く。そこで共通の verify は `./verify/run_all.sh` のままにし、委譲前の準備を人間作業として明記した(下の「委譲前の準備」)。
2. **鍵束の中身**
   - 鍵束にあるのは `release.keystore` と `signing.env` だけで、設計書 D-9 にある `release-cert.sha256` は入っていない。
   - `AKS_KEYSTORE` は、action の output `dir` から `"$dir/release.keystore"` として組み立てる。
   - `AKS_KS_PASS` と `AKS_KS_ALIAS` は、同じ run スクリプトの中で `set -a; . "$dir/signing.env"; set +a` によって得る。ステップの `env:` にはファイルの中身を渡せないため、「ステップの env で渡す」を「ステップ内で source する」に直した。
3. **証明書の値**: 証明書の値はユーザーから提示済みである。「オーケストレーターが apksigner で v1.0.2 から取る」という手順はやめ、値を直接書く。
4. **apksigner と aapt2 の場所**: どちらも PATH には無い。`source tools/env.sh` の後に `$APKSIGNER` と `$AAPT2` で使う。
5. **CI の準備**
   - `fetch_sdk.sh` の sdkmanager は Java 17 以上を要求するため、`actions/setup-java`(temurin 17)を足した。
   - `actions/cache` の path は `tools/android-sdk`、key は `hashFiles('tools/fetch_sdk.sh','tools/env.sh')` にした。
   - ci.yml には `actions/setup-python` と、`pytest` と `androguard>=4,<5` のインストールを足した。
6. **workflow_dispatch と既存の v1.0.2**
   - 既存の v1.0.2 の APK は versionCode 102 である。`ci_version_code.sh` で計算した 1000002 を publish に渡すと、APK の中身と食い違う。そこで、publish へ渡す versionCode は、最終的な APK から `$AAPT2 dump badging` で読み取る方式に直した。ビルドした場合は、計算値と一致することも照合する。
   - CI 導入前の tag には `build/ci_version_code.sh` と `build/release-cert.sha256` が無い。そこで、2 つ目の checkout(workflow 自身のコミットを `.ci/` に置く)から読む形にした。
   - build job の `needs: release-please` は、dispatch 時に skip されると build も連鎖して skip される。そのため `if: ${{ !cancelled() && ... }}` の形を明記した。
7. **README の節名**: README には「Release 節」が無い。実際の節は `## Building from source` で、ja は `## ソースからのビルド`、ko は `## 소스에서 빌드` である。Task 1 では README.md のこの節を直す。ja と ko の同じ節は、3 つの README を扱う Task 4 で揃える。
8. **release-please の simple 型**: simple 型は `version.txt` を更新する。そのため、Task 1 で `version.txt`(中身は `1.0.2`)を作るファイルに加えた。
9. **Task 1 の受け入れ基準を機械化**: 受け入れ基準のうち「release.yml に鍵の後片付けと証明書の照合がある」を機械的に確かめるため、`verify/test_release_workflow.py`(文字列での検査)を足した。
10. **Java テストの登録先**: Java のテストは、`scrobbler/test/ScrobblerTests.java` の `main` から各 `XxxTests.run()` を明示的に呼ぶ方式である。登録先は `verify/run_all.sh` ではなく `ScrobblerTests.java` に直した。
11. **JSON の扱い**: core では org.json が使えない。android.jar 版は JVM 上で `Stub!` を投げる。そのため、最小の JSON パーサー `core/update/MiniJson.java` を置くことに確定した。
12. **manifest のキー名**: manifest のキーは D-14 の snake_case である(`version_code`、`min_supported_code`、`update_required`)。Java のフィールドは camelCase にする。この対応を明記した。
13. **純度テストの範囲**: `verify/test_scrobbler_purity.py` は、core 配下の行に `android.` という部分文字列があれば、コメントや文字列の中でも失敗する。`Const.java` が `"com.qobuz." + "android" + ...` と分割しているのはこのためである。この注意を Task 2 に明記した。
14. **言語レベル**
    - `build/build_scrobbler.sh` は `-source 8 -target 8 -bootclasspath android-28.jar` でコンパイルする。run_all の 2/4 でも同じビルドが走る。
    - そのため Java 8 の構文に限る(`var`、`List.of` は使えない)。
    - 実行時は minSdk 21 なので、`java.util.Base64` などの API 26 以上のクラスも使えない。
15. **API 31 のシンボル**
    - コンパイル対象は android-28 である。`PendingIntent.FLAG_MUTABLE`、`SessionParams.setRequireUserAction`、`USER_ACTION_NOT_REQUIRED` は android.jar に無く、そのまま書くとビルドが失敗する。
    - 数値定数とリフレクションで扱う形に直した。`FLAG_MUTABLE` は `0x02000000`、`USER_ACTION_NOT_REQUIRED` は `2` である。
    - targetSdk は 28 なので、無人インストールは事実上効かない。`STATUS_PENDING_USER_ACTION` で確認ダイアログを出す経路が主になる。
16. **SelfInstaller の返り値**: 設計書は `SelfInstaller.install` の返り値を `void` としつつ、手順では `InstallOutcome.NEEDS_PERMISSION` を返すと書いていた。返り値を `InstallOutcome` に統一した。
17. **getPackageArchiveInfo の挙動(Android 9)**: Android 9(API 28)では、`getPackageArchiveInfo(..., GET_SIGNING_CERTIFICATES)` が `signingInfo == null` を返すことがある。対象機の AK SP2000T は Android 9 である。null の場合に `GET_SIGNATURES` で取り直す手順を足した。
18. **文言の置き場所**
    - `scrobbler/res/values*/strings.xml` は存在しない。文言は `core/Strings.java` の TABLE(`{KEY, ja, en, ko}`)と定数で管理し、`StringsTests` がキーと定数、プレースホルダーの整合を検査している。
    - Task 4 では、`core/Strings.java` だけを core の例外として許可した。
19. **「通知」の読み替え**
    - 「通知」は、既存の UI 部品である `android.app.AlertDialog`(QueueActivity の `confirm` と同じ使い方)に読み替えた。OPTIONAL はキャンセル可、REQUIRED は `setCancelable(false)` とする。
    - `Notifications.build` は `setOngoing(true)` のサービス用なので使わない。
20. **README の太字の規則**: README の `**太字**` は、`Strings.java` にあるラベルか、`verify/test_readme_ui_labels.py` の ALLOWED にあるものしか許されない。この規則を Task 4 に明記した。
21. **ネットワーク処理のスレッド**: ネットワーク処理は、既存の `NetThread.post` を再利用する。
22. **文字列リテラルの検査範囲**: `verify/test_no_hardcoded_strings.py` が検査するのは、`scrobbler/src/com/akscrobble/*.java`(直下)だけで、`update/` 配下は対象外である。それでも、日本語の文言は必ず `Strings` を経由させる。
23. **ピン定数の名前**: `verify/test_no_secrets.py` は、`(key|secret|token|sk)\s*=\s*"?[0-9a-f]{32}` に当たる行を秘密と見なす。ピンの定数名は `SIGNER_SHA256` とし、`..._KEY` や `..._TOKEN` のような名前にしない。

## 委譲前の準備(人間作業。Task 1 の前に 1 回)

1. JDK 17 を入れる。例: `sudo apt-get install -y openjdk-17-jdk-headless`
2. Python パッケージを入れる。例: `python3 -m pip install --user --break-system-packages pytest 'androguard>=4,<5'`(Ubuntu 24.04 は PEP 668 の制約がある)
3. `/home/server/github/akscrobble` で `./tools/fetch_sdk.sh` を実行する(ネットワークが要る)。
4. 現在の HEAD で `./verify/run_all.sh` が `ALL VERIFY PASS` になることを確かめ、基準線とする。

## 未解決の質問

なし。判断が要る点は、末尾の「人間作業と判断」にまとめた。

## Goal

- release-please の PR を merge すると、CI がビルド、署名、証明書の照合、GitHub Release への asset 添付、nyanstall への取り込みまでを行う。
- akscrobble が起動時に nyanstall へ更新を問い合わせる。利用者が承諾したら APK をダウンロードし、sha256、パッケージ名、versionCode、署名証明書を検証してから、PackageInstaller でインストールする。
- 自己更新できない機種(AK ファーム)では、xapk と Open APP Service を使う手動の手順へ必ず案内する。

## Non-goals

- xapk の自己更新。xapk は、Release asset として従来どおり添付するだけにする。
- nyanstall 側(Worker、管理画面)と workflows リポの変更。
- stable への自動昇格。`auto_promote = 0` なので、昇格は管理画面で手動で行う。
- Gradle の導入、targetSdk の引き上げ、既存の画面デザインの変更。
- 既存のコミット履歴の書き換え。

## Design notes

- CI のビルドは、既存の `make_release.sh` をそのまま使う。CI 用に別のビルド経路を作らない。手元での緊急リリースと同じ成果物にするためである。
- publish へ渡す versionCode は、最終的な APK から読み取った値にする。計算値を使うと、CI 導入前の tag(v1.0.2 は 102)で中身と食い違うため。
- 証明書のピンは 2 か所に置く。1 つは CI 用の `build/release-cert.sha256`、もう 1 つはクライアント用の `ReleasePin.SIGNER_SHA256` で、テストで一致を保証する。サーバーの `signer_sha256` は参考情報として扱い、検証には使わない(D-17)。
- 純粋ロジック(manifest の解析、判定、sha256)は `core/update` に置き、JVM 上の TestKit で検査する。Android API に依存する部分(ダウンロード、検証、インストール、UI)は `com.akscrobble.update` に置き、APK のビルドが通ることと実機での確認で担保する。
- 却下した案
  - org.json を使う案: JVM 上のテストで `Stub!` になる。
  - API 31 のシンボルを直接参照するために compileSdk を上げる案: `tools/env.sh` と `fetch_sdk.sh` の変更が要り、AK ファームの互換性を検証し直すことになる。
  - 「通知」を Android の Notification で出す案: サービスの常駐通知と衝突する。

## 共通検証コマンド(`--verify`)

```sh
./verify/run_all.sh
```

Task 1 だけ、追加で `actionlint` を実行する。

---

### Task 1: release-please と CI での署名ビルド、nyanstall への取り込みを導入する(設計書 Task 13)

- Files to touch:
  - `.github/workflows/release.yml`(新規)
  - `.github/workflows/ci.yml`(新規)
  - `release-please-config.json`(新規)
  - `.release-please-manifest.json`(新規)
  - `version.txt`(新規)
  - `build/release-cert.sha256`(新規)
  - `build/ci_version_code.sh`(新規、実行可能にする)
  - `verify/test_ci_version_code.py`(新規)
  - `verify/test_release_workflow.py`(新規)
  - `README.md`(`## Building from source` 節のみ)
- Files NOT to touch: `scrobbler/`、`build/make_release.sh`、`build/build_scrobbler.sh`、`build/build_split.sh`、`build/pack_xapk.py`、`tools/env.sh`、`tools/fetch_sdk.sh`、`verify/run_all.sh`、`README.ja.md`、`README.ko.md`
- New dependencies: none(CI では、`actions/checkout@v4`、`actions/setup-java@v4`、`actions/setup-python@v5`、`actions/cache@v4`、`googleapis/release-please-action@v4`、`tsuki-neko-com/workflows/actions/nyanstall-key@v1`、`tsuki-neko-com/workflows/.github/workflows/nyanstall-publish.yml@v1` を使う。ローカルで新たに入れるものは無い)
- Steps:
  1. 前提の確認はオーケストレーターが済ませている。鍵束はアップロード済みで、D-20 の secret も登録済みである。ワーカーは確認しなくてよい。
  2. `build/release-cert.sha256` に、`de8bae26d0744d53e9ad8b3418748a68378f8c7c5240fceb9afcb05d8ac0d6af` と改行 1 つだけを書く。
  3. `build/ci_version_code.sh <versionName>` を書く。
     - 先頭は `#!/usr/bin/env bash` と `set -euo pipefail` にする。
     - 引数は `^[0-9]+\.[0-9]+\.[0-9]+$` に一致しなければならない。`v` 付き、プレリリース付き、2 要素の形式、空文字、引数なしは拒否する。
     - MAJOR は 1 以上 2099 以下、MINOR と PATCH は 0 以上 999 以下とする。
     - 正常なら、`MAJOR*1000000 + MINOR*1000 + PATCH` を 10 進数で 1 行だけ標準出力に出し、exit 0 で終わる。
     - 異常なら、標準エラーに理由を出して exit 2 で終わる。
     - 先頭のゼロは 10 進数として解釈する。bash の算術で 8 進数として扱われないよう、`10#$x` を使う。
  4. `verify/test_ci_version_code.py` を pytest で書く。`subprocess.run(["bash", "build/ci_version_code.sh", arg], cwd=ROOT)` で次を検査する。
     - 正常系: `1.0.2` → `1000002`、`1.0.3` → `1000003`、`1.1.0` → `1001000`、`2099.999.999` → `2099999999`、`1.0.09` → `1000009`
     - exit 2: `0.1.0`、`2100.0.0`、`1.1000.0`、`1.0.1000`、`v1.0.2`、`1.0`、`1.0.2-rc1`、`""`、引数なし
  5. `release-please-config.json` を書く。内容は `{"packages": {".": {"release-type": "simple", "include-v-in-tag": true, "package-name": "akscrobble"}}}` に、`$schema` を足したものとする。`.release-please-manifest.json` は `{".": "1.0.2"}` にする。`version.txt` は `1.0.2` と改行にする。
  6. `.github/workflows/release.yml` を書く。
     - トリガーと全体の設定
       - `on.push.branches: [main]` と `on.workflow_dispatch.inputs.tag`(必須、string。例 `v1.0.2`)を受ける。
       - 最上位の `permissions: {}` を置き、`concurrency: { group: release, cancel-in-progress: false }` を設定する。
     - job `release-please`
       - `if: github.event_name == 'push'`、`permissions: contents: write, pull-requests: write`
       - `googleapis/release-please-action@v4` を使い、`config-file: release-please-config.json` と `manifest-file: .release-please-manifest.json` を渡す。
       - outputs として `release_created` と `tag_name` を出す。
     - job `build`
       - `needs: release-please`、`if: ${{ !cancelled() && (needs.release-please.outputs.release_created == 'true' || github.event_name == 'workflow_dispatch') }}`、`permissions: contents: write`、`runs-on: ubuntu-latest`
       - outputs として `tag` と `version_code` を出す。
       1. meta ステップ: `TAG` を決める。push のときは release-please の `tag_name`、dispatch のときは `inputs.tag` を使う。`^v[0-9]+\.[0-9]+\.[0-9]+$` に合わなければ失敗する。`VN=${TAG#v}` を `$GITHUB_OUTPUT` に書く。
       2. `actions/checkout@v4` で `ref: ${{ steps.meta.outputs.tag }}` を `.` に取る。
       3. `actions/checkout@v4` で `ref: ${{ github.sha }}`、`path: .ci`、`sparse-checkout: build`(`sparse-checkout-cone-mode: true`)を取る。以後、`ci_version_code.sh` と `release-cert.sha256` は、必ず `.ci/build/` から読む。CI 導入前の tag にはこの 2 つが無いためである。
       4. `actions/setup-java@v4` を `distribution: temurin`、`java-version: '17'` で使う。
       5. `actions/cache@v4` を、`path: tools/android-sdk`、`key: android-sdk-${{ runner.os }}-${{ hashFiles('tools/fetch_sdk.sh', 'tools/env.sh') }}` で使う。その後に `./tools/fetch_sdk.sh` を実行する。キャッシュが当たっていれば、`fetch_sdk.sh` は "already installed" で即座に終わる。
       6. 既存 asset の確認ステップ(`GH_TOKEN: ${{ github.token }}`): `gh release view "$TAG" --json assets -q '.assets[].name'` に `akscrobble-$VN.apk` が含まれ、かつ dispatch のときは、`skip_build=true` を出力する。含まれていれば、`gh release download "$TAG" --pattern "akscrobble-$VN.apk" --dir artifacts/release` で取得する。push のときは常に `skip_build=false` とする。
       7. 鍵の取得ステップ(`if: steps.assets.outputs.skip_build != 'true'`): `tsuki-neko-com/workflows/actions/nyanstall-key@v1` を `id: key` として使う。渡す値は次のとおり。
          - `app: akscrobble`
          - `purpose: android-release`
          - `api-token: ${{ secrets.NYANSTALL_CF_API_TOKEN }}`
          - `account-id: ${{ secrets.NYANSTALL_CF_ACCOUNT_ID }}`
          - `age-identity: ${{ secrets.NYANSTALL_AGE_IDENTITY }}`
       8. ビルドステップ(同じ条件)
          - `env:` には `KEY_DIR: ${{ steps.key.outputs.dir }}` と `VN` だけを渡す。
          - `set -x` は使わない。
          - スクリプトの中身は次のとおり。
            - `set -euo pipefail`
            - `VC="$(bash .ci/build/ci_version_code.sh "$VN")"`(実行ビットに依存しないよう、必ず `bash` で呼ぶ)
            - `export AKS_KEYSTORE="$KEY_DIR/release.keystore"`
            - `set -a; . "$KEY_DIR/signing.env"; set +a`
            - `./build/make_release.sh "$VC" "$VN"`
          - パスワードの値は、echo やログに出さない。
       9. 照合ステップ(`if:` を付けない。skip_build に関係なく実行する。`if: always()` も付けない)
          - `source tools/env.sh` を実行する。
          - `APK="artifacts/release/akscrobble-$VN.apk"` とする。
          - `"$APKSIGNER" verify --print-certs "$APK"` の出力から、`Signer #1 certificate SHA-256 digest: ` の後ろの値を取り出す。値が `.ci/build/release-cert.sha256` と一致しなければ失敗する。
          - `"$AAPT2" dump badging "$APK"` の `versionCode='N'` から N を読み取り、`version_code` として出力する。
          - ビルドした場合は、N が `ci_version_code.sh` の値と一致しなければ失敗する。
       10. アップロードステップ(`if: steps.assets.outputs.skip_build != 'true'`): `gh release upload "$TAG" "artifacts/release/akscrobble-$VN.apk" "artifacts/release/akscrobble-$VN.xapk" --clobber` を実行する。
       11. 後片付けステップ(`if: always()`): `rm -rf "${{ steps.key.outputs.dir }}"` を実行する。output が空でも失敗しないよう、`${KEY_DIR:+...}` か `[ -n ]` で守る。
     - job `publish`
       - `needs: build`、`if: ${{ !cancelled() && needs.build.result == 'success' }}`、`permissions: contents: read`
       - `uses: tsuki-neko-com/workflows/.github/workflows/nyanstall-publish.yml@v1` を使う。
       - `with:` には、`app: akscrobble`、`tag: ${{ needs.build.outputs.tag }}`、`mode: android`、`asset-pattern: '*.apk'`、`version-code: ${{ needs.build.outputs.version_code }}` を渡す。
       - `secrets:` には、`NYANSTALL_CF_API_TOKEN`、`NYANSTALL_CF_ACCOUNT_ID`、`NYANSTALL_ACCESS_CLIENT_ID`、`NYANSTALL_ACCESS_CLIENT_SECRET` の 4 つを明示的に渡す。`secrets: inherit` は使わない。
  7. `.github/workflows/ci.yml` を書く。
     - `on: pull_request` と `on.push.branches: [main]` を受け、`permissions: contents: read` とする。
     - 1 つの job の手順は次のとおり。
       1. checkout する。
       2. setup-java(temurin 17)を使う。
       3. setup-python(`'3.12'`)を使う。
       4. `python -m pip install pytest 'androguard>=4,<5'` を実行する。
       5. release.yml と同じ key で SDK をキャッシュする。
       6. `./tools/fetch_sdk.sh` を実行する。
       7. `./verify/run_all.sh` を実行する。
  8. `verify/test_release_workflow.py` を書く。`.github/workflows/release.yml` を文字列として読み、次を検査する。PyYAML は使わない。
     - `release-cert.sha256` と `--print-certs` が含まれる。
     - `if: always()` を持つステップがちょうど 1 つあり、その行から次の `- name:`(または `- uses:`)までの範囲に `rm -rf` が含まれる。行単位の走査で確かめる。
     - `secrets: inherit` を含まない。
     - `set -x` を含まない。
     - `nyanstall-publish.yml@v1` と `nyanstall-key@v1` が含まれる。
     - `build/release-cert.sha256` の中身が 64 桁の小文字 hex と改行 1 つであることも検査する。
  9. `README.md` の `## Building from source` 節を書き換える。
     - 通常のリリース: Conventional Commits(`feat:`、`fix:`)で main に入れると、release-please が release PR を作る。その PR を merge すると、CI がビルド、署名、証明書の照合、Release への asset 添付、nyanstall への登録まで行う。stable への公開は、nyanstall の管理画面で行う。
     - versionCode の規則(`MAJOR*1000000 + MINOR*1000 + PATCH`、`build/ci_version_code.sh`)を書く。
     - 手元で `make_release.sh` を使う既存の手順は、「緊急時の手順」として残す。
     - 新しい `**太字**` は足さない。README の太字の規則に引っかかるためである。
- Acceptance:
  - `./verify/run_all.sh` が緑で、`test_ci_version_code.py` と `test_release_workflow.py` を含む。
  - `actionlint` が緑。
  - release.yml に、鍵の後片付け(`if: always()`)と証明書の照合がある。
- Verify: `./verify/run_all.sh && actionlint`
- オーケストレーターの手動確認(ランナーのタスク外)
  - CI のビルド job は、実際の Release でしか確かめられない。merge 後、最初の release PR を merge したときに、次を確認する。
    - release.yml の build が緑で、ログにパスワードが出ていない。
    - 証明書の照合が通っている。
    - Release に apk と xapk が付いている。
    - publish が 201 か 200 で終わっている。
  - ci.yml の初回実行が緑であることも確認する(SDK のキャッシュが作られる)。

### Task 2: 更新確認とダウンロード、検証の純粋ロジックを実装する(設計書 Task 14)

- Files to touch:
  - `scrobbler/src/com/akscrobble/core/update/MiniJson.java`(新規)
  - `scrobbler/src/com/akscrobble/core/update/UpdateManifest.java`(新規)
  - `scrobbler/src/com/akscrobble/core/update/UpdateDecision.java`(新規)
  - `scrobbler/src/com/akscrobble/core/update/Sha256Sink.java`(新規)
  - `scrobbler/test/UpdateManifestTests.java`(新規)
  - `scrobbler/test/UpdateDecisionTests.java`(新規)
  - `scrobbler/test/Sha256SinkTests.java`(新規)
  - `scrobbler/test/ScrobblerTests.java`(`main` に 3 つの `run()` 呼び出しを足すだけ)
- Files NOT to touch: `scrobbler/AndroidManifest.xml`、既存の `scrobbler/src/com/akscrobble/core/*.java`、`scrobbler/src/com/akscrobble/*.java`、`build/`、`verify/`、`.github/`
- New dependencies: none
- Steps:
  1. 先に、`verify/test_scrobbler_purity.py`、`scrobbler/test/TestKit.java`、`scrobbler/test/ScrobblerTests.java`、既存の `*Tests.java` を 1 つ読む。規則は次のとおり。
     - core 配下の行には、コメントや文字列の中でも `android.` という部分文字列を書かない。
     - テストはデフォルトパッケージに置き、`import com.akscrobble.core.` で始まる import だけを使う(`com.akscrobble.core.update.` も可)。
     - テストクラスは `final class XxxTests { static void run() { TestKit.section("..."); ... } }` の形にする。
     - Java 8 の構文に限る。API 26 以上のクラス(`java.util.Base64` など)は使わない。org.json は使わない。
  2. `MiniJson` を書く。
     - シグネチャは `public static Object parse(String json)` とする。
     - 返す型は、object が `java.util.Map<String,Object>`(`LinkedHashMap`)、array が `java.util.List<Object>`、文字列が `String`、整数が `Long`、小数や指数を含む数値が `Double`、真偽値が `Boolean`、null が `null` である。
     - 文字列のエスケープ(`\" \\ \/ \b \f \n \r \t \uXXXX`)を扱う。
     - 前後の空白は許す。末尾に余分な文字がある場合や、形式が不正な場合は `IllegalArgumentException` を投げる。
     - 入れ子は 32 段までとし、超えたら `IllegalArgumentException` にする。
  3. `UpdateManifest` を書く。
     - `public static UpdateManifest parse(String json)` を用意する。ルートが object でなければ `IllegalArgumentException` にする。
     - public final のフィールドと JSON キーの対応は次のとおり。
       - `String version` ← `version`(必須の文字列)
       - `long versionCode` ← `version_code`(必須の整数、1 以上)
       - `String url` ← `url`(必須の文字列)
       - `long size` ← `size`(必須の整数、1 以上)
       - `String sha256` ← `sha256`(必須。64 桁の hex で、小文字に正規化する)
       - `String notes` ← `notes`(任意、null 可)
       - `Long minSupportedCode` ← `min_supported_code`(任意、null 可)
       - `boolean updateRequired` ← `update_required`(任意で、無ければ false)
     - 必須キーの欠落、型の違い、範囲外の値は `IllegalArgumentException` にする。
     - 未知のキーは、値が入れ子の object や array でも無視する。`signature`、`signer_sha256`、`codesign`、`min_supported`、`published_at` などがこれに当たる。
  4. `UpdateDecision` を `public enum UpdateDecision { NONE, OPTIONAL, REQUIRED, REJECTED; ... }` として書き、`public static UpdateDecision decide(long installedCode, UpdateManifest m, String allowedUrlPrefix)` を持たせる。判定の順序は次のとおり。
     1. `m.versionCode <= installedCode` なら `NONE` を返す。
     2. `allowedUrlPrefix` が `https://` で始まり `/` で終わること、`m.url` が `allowedUrlPrefix` で始まること、`m.url` に `..`、`@`、`\`、空白を含まないこと。どれかを満たさなければ `REJECTED` を返す。
     3. `m.updateRequired`、または `m.minSupportedCode != null && installedCode < m.minSupportedCode` なら `REQUIRED` を返す。
     4. それ以外は `OPTIONAL` を返す。

     既定の接頭辞 `https://nyanstall.sukisuki.club/` は、呼び出し側(Task 4 の `UpdateChecker`)の定数に置く。core には置かない。
  5. `Sha256Sink extends java.io.OutputStream` を書く。
     - コンストラクタは `Sha256Sink(OutputStream delegate)` とする。
     - `write(int)` と `write(byte[], int, int)` で、delegate への書き込みと `MessageDigest("SHA-256")` の更新を同時に行う。
     - `long bytesWritten()` と `String hexDigest()`(小文字 64 桁)を持たせる。`hexDigest()` は 2 回目以降も同じ値を返し、呼んだ後の `write` は `IllegalStateException` にする。
     - `flush` と `close` は delegate へ委ねる。
  6. テストを書く。
     - `UpdateManifestTests.run()`
       - D-14 の Android の例を基にした正常系
       - `min_supported_code` が null の場合と、キーが無い場合
       - 未知の入れ子キー(`codesign: {"a":[1,{"b":null}]}`)
       - 必須キー 5 つの欠落をそれぞれ
       - `version_code` が文字列の場合
       - `sha256` が 63 桁や非 hex の場合
       - 不正な JSON(末尾のゴミ、閉じていない文字列)
       - `あ` のエスケープ
     - `UpdateDecisionTests.run()`
       - `versionCode` が同じ、低い、高い場合
       - `update_required=true` による REQUIRED
       - `min_supported_code` による REQUIRED
       - `http://` の URL による REJECTED
       - 別ホスト(`https://example.com/...`)による REJECTED
       - 接頭辞の偽装(`https://nyanstall.sukisuki.club.evil.example/...`)による REJECTED
       - `..` を含む URL による REJECTED
       - 低い版では、URL が不正でも NONE になること
     - `Sha256SinkTests.run()`
       - `""` → `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
       - `"abc"` → `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`
       - 1 バイトずつ書いた場合と配列でまとめて書いた場合で、同じ値になること
       - `bytesWritten` の値
       - delegate(`ByteArrayOutputStream`)に同じバイト列が届くこと
  7. `ScrobblerTests.main` の `ConfigTests.run();` の後に、`UpdateManifestTests.run(); UpdateDecisionTests.run(); Sha256SinkTests.run();` を足す。
- Acceptance: `./verify/run_all.sh` が緑で、出力に `-- UpdateManifest`、`-- UpdateDecision`、`-- Sha256Sink` の各節と `ALL TESTS PASS` がある。純度テストが通る。APK のビルド(2/4)が通る。
- Verify: `./verify/run_all.sh`

### Task 3: APK のダウンロード、検証、PackageInstaller によるインストールを実装する(設計書 Task 15)

- Files to touch:
  - `scrobbler/src/com/akscrobble/update/ApkDownloader.java`(新規)
  - `scrobbler/src/com/akscrobble/update/ApkVerifier.java`(新規)
  - `scrobbler/src/com/akscrobble/update/SelfInstaller.java`(新規)
  - `scrobbler/src/com/akscrobble/update/InstallResultReceiver.java`(新規)
  - `scrobbler/src/com/akscrobble/update/InstallOutcome.java`(新規)
  - `scrobbler/src/com/akscrobble/update/ReleasePin.java`(新規)
  - `scrobbler/AndroidManifest.xml`
  - `verify/test_manifest_permissions.py`(REQUEST_INSTALL_PACKAGES と Receiver の検査を足すだけ)
  - `verify/test_release_pin.py`(新規)
- Files NOT to touch: `scrobbler/src/com/akscrobble/core/`(Task 2 のクラスは読むだけ)、`scrobbler/src/com/akscrobble/*.java`(MainActivity を含む)、`scrobbler/res/`、`scrobbler/test/`、`build/`、`.github/`
- New dependencies: none
- Steps:
  1. 前提として、コンパイル対象は `platforms/android-28/android.jar` で、`-source 8` である。API 29 以上にしか無いシンボルを直接参照すると、run_all の 2/4(APK のビルド)が失敗する。
     - 定数 `FLAG_MUTABLE = 0x02000000` と `USER_ACTION_NOT_REQUIRED = 2` は、`SelfInstaller` の private static final として定義する。
     - `setRequireUserAction(int)` は、`PackageInstaller.SessionParams.class.getMethod("setRequireUserAction", int.class)` によるリフレクションで呼ぶ。失敗した場合は無視する。
     - targetSdk は 28 なので、無人インストールは事実上効かない。`STATUS_PENDING_USER_ACTION` の経路が主になる。ログタグは `Const.TAG` を使う。
  2. `ReleasePin` を書く。`public static final String SIGNER_SHA256 = "de8bae26d0744d53e9ad8b3418748a68378f8c7c5240fceb9afcb05d8ac0d6af";` を持つ final class とし、private コンストラクタを置く。定数名を `..._KEY` や `..._TOKEN` にしない(`test_no_secrets.py` の規則)。
  3. `verify/test_release_pin.py` を書く。`ReleasePin.java` から `SIGNER_SHA256 = "<hex>"` を正規表現で取り出し、`build/release-cert.sha256` を strip した値と一致すること、および 64 桁の小文字 hex であることを検査する。
  4. `InstallOutcome` を書く。
     - `public enum InstallOutcome { STARTED, NEEDS_PERMISSION, FAILED }` とする。
     - 入れ子のインターフェース `public interface Listener { void onOutcome(InstallOutcome outcome, String message); }` を持たせる。
     - `public static void setListener(Listener l)` と `static void notify(InstallOutcome o, String message)` を持たせる。notify はメインスレッドの `Handler` で呼び、リスナーが null なら何もしない。
  5. `ApkDownloader` に `public static File download(Context context, UpdateManifest m) throws IOException` を書く。
     - 保存先は `new File(context.getCacheDir(), "updates")` の下の `{versionCode}.apk` とする。開始時に、`updates/` 内の他のファイルを削除する。
     - `HttpURLConnection` を使い、接続のタイムアウトは 10000 ms、読み取りは 15000 ms とする。リダイレクトは、`setInstanceFollowRedirects(false)` で追わない。
     - 応答が 200 以外なら `IOException` にする。
     - `Sha256Sink` を経由して書き込む。`m.size` を超えたら、その時点で中断する。
     - 終了後に、`bytesWritten() == m.size` と `hexDigest().equals(m.sha256)` を照合する。不一致や例外の場合は、ファイルを削除して `IOException` を投げる。
  6. `ApkVerifier` に `public static void verify(Context context, File apk, long expectedMinCode)` を書く。不一致は `SecurityException` にする。
     - API 28 以上では、`getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)` を使う。`info.signingInfo` が null の場合は、`GET_SIGNATURES` で取り直す(Android 9 の挙動への対策)。API 27 以下では `GET_SIGNATURES` を使う。
     - `info` が null なら失敗にする。
     - `info.packageName.equals(context.getPackageName())` を照合する。実体は `fm.last.android` だが、文字列で埋め込まない。
     - versionCode は、API 28 以上では `getLongVersionCode()`、それ以外では `versionCode` を使う。値が `expectedMinCode` 以上であることを照合する。
     - 署名は、ちょうど 1 つで、かつその `toByteArray()` の SHA-256(小文字 hex)が `ReleasePin.SIGNER_SHA256` と一致することを照合する。署名が複数ある場合や、`hasMultipleSigners()` が true の場合は失敗にする。サーバーの `signer_sha256` は使わない。
  7. `SelfInstaller` に `public static InstallOutcome install(Context context, File apk)` を書く。
     - API 26 以上で `getPackageManager().canRequestPackageInstalls()` が false なら、`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` を `Uri.parse("package:" + context.getPackageName())` と `FLAG_ACTIVITY_NEW_TASK` 付きで起動し、`NEEDS_PERMISSION` を返す。
     - `PackageInstaller.SessionParams(MODE_FULL_INSTALL)` を作る。API 31 以上では、手順 1 のリフレクションで `setRequireUserAction(2)` を呼ぶ。
     - `createSession`、`openSession`、`openWrite("base.apk", 0, apk.length())` で APK を書き込み、`fsync` する。
     - 明示的な Broadcast の `Intent(context, InstallResultReceiver.class)` を作る。`PendingIntent.getBroadcast` には、`FLAG_UPDATE_CURRENT` を付け、API 31 以上では `FLAG_MUTABLE` の数値も付ける。これを `commit(pi.getIntentSender())` で確定し、`STARTED` を返す。
     - セッションに関する例外は、`abandon` してから呼び出し側へ投げ直す。呼び出し側(Task 4)がフォールバックへ回す。
  8. `InstallResultReceiver extends BroadcastReceiver` を書く。`PackageInstaller.EXTRA_STATUS` で分岐する。
     - `STATUS_PENDING_USER_ACTION`: `Intent.EXTRA_INTENT` を `FLAG_ACTIVITY_NEW_TASK` 付きで `startActivity` する。起動に失敗したら、`FAILED` を通知する。
     - `STATUS_SUCCESS`: 何もしない。
     - それ以外: `InstallOutcome.notify(FAILED, EXTRA_STATUS_MESSAGE)` を呼ぶ。
  9. `AndroidManifest.xml` に、`<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` と、`<receiver android:name="com.akscrobble.update.InstallResultReceiver" android:exported="false" />` を追加する。既存の権限と要素は消さない(Qobuz 対策のテストがある)。
  10. `verify/test_manifest_permissions.py` に 2 つのテストを足す。1 つは `REQUEST_INSTALL_PACKAGES` があること、もう 1 つは `com.akscrobble.update.InstallResultReceiver` の receiver があり、`exported` が `"false"` であることを検査する。既存のテストは変えない。
- Acceptance:
  - `./verify/run_all.sh` が緑。APK のビルド(2/4)が、新しいクラスを含めて通る。
  - マニフェストのテストが、新しい権限と Receiver を検査する。
  - ピンの一致テストが緑。
- Verify: `./verify/run_all.sh`

### Task 4: 起動時に更新を確認し、失敗時は手動の手順へ案内する(設計書 Task 16)

- Files to touch:
  - `scrobbler/src/com/akscrobble/update/UpdateChecker.java`(新規)
  - `scrobbler/src/com/akscrobble/update/UpdateFlow.java`(新規)
  - `scrobbler/src/com/akscrobble/MainActivity.java`(起動時の呼び出しとダイアログの表示だけ)
  - `scrobbler/src/com/akscrobble/core/Strings.java`(キー定数と TABLE の行を足すだけ)
  - `README.md`、`README.ja.md`、`README.ko.md`(自己更新の節を追加する。ja と ko は、ビルド節を Task 1 の英語版に揃える)
- Files NOT to touch: `scrobbler/src/com/akscrobble/core/` の `Strings.java` 以外、Task 3 のクラス(呼び出すだけ)、`scrobbler/AndroidManifest.xml`、`scrobbler/res/`、`build/`、`verify/`、`.github/`
- New dependencies: none
- Steps:
  1. 先に、`verify/test_no_hardcoded_strings.py`、`verify/test_readme_ui_labels.py`、`scrobbler/test/StringsTests.java`、`core/Strings.java` の TABLE の書式、`QueueActivity.confirm` の AlertDialog の使い方を読む。規則は次のとおり。
     - UI の文言は、すべて `Strings` のキーとして足す。定数(`public static final String UPDATE_... = "update.…"`)と TABLE の行(`{KEY, "ja", "en", "ko"}`)は、必ず対で足す。プレースホルダー `{0}` の数は 3 言語で揃える。
     - Java のソースに、日本語の文字列リテラルを書かない。
     - README の `**太字**` は、Strings にある実際のラベル(その言語の列)か、ALLOWED にあるものだけにする。迷ったら太字を使わない。
  2. `Strings` にキーを足す。最低限、次のキーを用意する。
     - `UPDATE_AVAILABLE_TITLE`
     - `UPDATE_REQUIRED_TITLE`
     - `UPDATE_MESSAGE`(`{0}` に新しい版、`{1}` にリリースノート)
     - `UPDATE_INSTALL`
     - `UPDATE_LATER`
     - `UPDATE_DOWNLOADING`
     - `UPDATE_NEEDS_PERMISSION`
     - `UPDATE_FALLBACK_TITLE`
     - `UPDATE_FALLBACK_MESSAGE`

     `UPDATE_FALLBACK_MESSAGE` の ja は「この機種では自動更新できません。xapk を Open APP Service で入れてください。」に `\n{0}` を続けたものとし、`{0}` に URL を入れる。en と ko は同じ意味に訳す。
  3. `UpdateChecker` を書く。
     - 定数は、`MANIFEST_URL = "https://nyanstall.sukisuki.club/v1/akscrobble/stable/latest?current_code="` と `ALLOWED_URL_PREFIX = "https://nyanstall.sukisuki.club/"` とする。
     - `public interface Callback { void onResult(UpdateDecision decision, UpdateManifest manifest); }` を定義する。
     - `public static void check(Context context, Callback cb)` を書く。
       - プロセス内で 1 回だけ動くよう、static の `boolean` で守る(画面の回転で `onCreate` が再び呼ばれる対策)。
       - `NetThread.post` の中で、installed の versionCode を `PackageManager.getPackageInfo(getPackageName(), 0)` から取る(API 28 以上は `getLongVersionCode()`)。
       - `MANIFEST_URL + installed` を `HttpURLConnection`(接続 10 秒、読み取り 15 秒、`User-Agent` は `Const.USER_AGENT`)で取る。200 なら `UpdateManifest.parse` と `UpdateDecision.decide(installed, m, ALLOWED_URL_PREFIX)` を通す。
       - `NONE` と `REJECTED` の場合は、コールバックを呼ばない(REJECTED はログだけ残す)。
       - `OPTIONAL` と `REQUIRED` の場合は、メインスレッドの `Handler` でコールバックを呼ぶ。
       - 例外は、すべて `Log.w(Const.TAG, ...)` に残すだけで握りつぶす。起動を妨げない。
  4. `UpdateFlow` を書く。
     - 定数は `RELEASES_URL = "https://github.com/kakeru-ikeda/akscrobble/releases"` とする。
     - `public static void start(Activity activity, UpdateManifest m)` を書く。
       - `InstallOutcome.setListener` で、`FAILED` を受けたらフォールバックを出すよう登録する。
       - `NetThread.post` の中で、`ApkDownloader.download`、`ApkVerifier.verify(activity, file, m.versionCode)`、`SelfInstaller.install` を順に呼ぶ。
       - `NEEDS_PERMISSION` なら、`UPDATE_NEEDS_PERMISSION` を案内する。
       - どこかで例外(`IOException`、`SecurityException`、`RuntimeException`)が出た場合は、メインスレッドでフォールバックを出す。
     - `public static void showFallback(Activity activity)` を書く。`AlertDialog` で `UPDATE_FALLBACK_TITLE` と `UPDATE_FALLBACK_MESSAGE`(`{0}` は `RELEASES_URL`)を表示する。これは AK ファームを想定した必須の経路である。Activity が `isFinishing()` なら表示しない。
  5. `MainActivity` を変える。
     - `onCreate` の末尾で `UpdateChecker.check(this, ...)` を呼ぶ。
     - `OPTIONAL` なら、キャンセルできる `AlertDialog` を出す。タイトルは `UPDATE_AVAILABLE_TITLE`、ボタンは `UPDATE_INSTALL` と `UPDATE_LATER` とする。
     - `REQUIRED` なら、`setCancelable(false)` の `AlertDialog` を出す。タイトルは `UPDATE_REQUIRED_TITLE` で、ボタンは `UPDATE_INSTALL` だけとする。押した後もフォールバックで閉じられるよう、AlertDialog の閉じる動作は既定のままにする。
     - 利用者が `UPDATE_INSTALL` を押したときだけ、`UpdateFlow.start` に進む。
     - 文言はすべて `Ui.s(this, Strings.UPDATE_...)` で取る。既存のボタンや状態表示は変えない。新しいレイアウトは作らない。
  6. 3 つの README に、自己更新の節を足す。内容は次のとおり。
     - 起動時に nyanstall を確認し、承諾するとダウンロードとインストールに進むこと。
     - 署名と sha256 を検証すること。
     - AK ファームでは自己更新が使えない可能性が高いこと。その場合は、案内に従って Releases から xapk を取り、Open APP Service で入れること。
     - 自己更新を使える最初の版は、手動で入れる必要があること。
     - ja と ko の「ソースからのビルド」節も、Task 1 で書き換えた README.md の内容(release-please と CI でのリリース、緊急時の手元での手順)に揃える。
- Acceptance:
  - `./verify/run_all.sh` が緑。`StringsTests`、文言のテスト、README のラベルのテストを含む。
  - APK のビルドが通る。
- Verify: `./verify/run_all.sh`
- オーケストレーターとユーザーの手動確認(ランナーのタスク外)
  - Task 3 と Task 4 の動作は、実機でしか確かめられない。
  - full Android 機では、更新ダイアログ → ダウンロード → 確認ダイアログ → 上書きインストールの流れを確認する。
  - AK SP2000T(AK ファーム、Android 9)では、自己更新が失敗した場合にフォールバックの案内が出ることを確認する。

---

## 人間作業と判断

1. 委譲前の準備: JDK 17、`pytest` と `androguard>=4,<5`、`./tools/fetch_sdk.sh` の実行、基準線の run_all(上の「委譲前の準備」)。
2. GitHub のリポジトリ設定で、Settings → Actions → General の「Allow GitHub Actions to create and approve pull requests」を有効にする。release-please が PR を作るのに必要である。
3. コミット規約
   - v1.0.2 以降の既存のコミットは、Conventional Commits ではない。release-please は `feat:` か `fix:` のコミットが入るまで release PR を作らない。
   - このタスク群のコミットは、オーケストレーターが Conventional Commits で作る(例: Task 1 は `ci:`、Task 2〜4 は `feat:`)。
   - GITHUB_TOKEN が作った release PR では、ci.yml が自動では走らない。必要なら手動で再実行する。
4. CI のビルド job の手動確認: Task 1 の「オーケストレーターの手動確認」を参照。build が失敗すると、Release が asset なしで残る。その場合は、修正した後に `workflow_dispatch`(tag 指定)で取り込み直す。
5. v1.0.2 を nyanstall に登録するかどうか: `workflow_dispatch` に `tag: v1.0.2` を渡すと、既存の asset からビルドなしで、versionCode 102 として登録される。基準線として登録するかどうかを決める。
6. stable への昇格: `auto_promote = 0` なので、ingest の後に管理画面で配布バージョンを指定する。
7. 実機での確認: Task 4 の「オーケストレーターとユーザーの手動確認」を参照。
8. 既存の利用者への周知: 現在 v1.0.2 以前を使っている人には自己更新の機能が無い。最初の自己更新対応版は手動で入れてもらう必要がある。
