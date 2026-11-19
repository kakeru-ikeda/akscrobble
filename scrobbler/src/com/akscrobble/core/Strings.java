package com.akscrobble.core;

import java.util.ArrayList;
import java.util.List;

public final class Strings {
    public enum Lang { JA, EN, KO }

    public static final String NOTIF_TITLE = "notif.title";
    public static final String NOTIF_RUNNING = "notif.running";
    public static final String NOTIF_STARTING = "notif.starting";
    public static final String NOTIF_REAUTH = "notif.reauth";
    public static final String MAIN_START = "main.start";
    public static final String MAIN_STOP = "main.stop";
    public static final String MAIN_AUTH = "main.auth";
    public static final String MAIN_IMPORT_CONFIG = "main.import_config";
    public static final String MAIN_QUEUE = "main.queue";
    public static final String MAIN_FLUSH = "main.flush";
    public static final String MAIN_REQUEST_STORAGE = "main.request_storage";
    public static final String MAIN_BATTERY_EXEMPTION = "main.battery_exemption";
    public static final String MAIN_BTN_SANITIZE = "main.btn_sanitize";
    public static final String MAIN_BTN_ADB = "main.btn_adb";
    public static final String MAIN_SAVE_DIAGNOSTIC = "main.save_diagnostic";
    public static final String MAIN_UNAUTHENTICATED = "main.unauthenticated";
    public static final String MAIN_STORAGE_RESULT = "main.storage_result";
    public static final String MAIN_REQUEST_SENT = "main.request_sent";
    public static final String MAIN_ALREADY_BATTERY_EXEMPT = "main.already_battery_exempt";
    public static final String MAIN_BATTERY_UNSUPPORTED = "main.battery_unsupported";
    public static final String MAIN_CONFIG_CREATED = "main.config_created";
    public static final String MAIN_CONFIG_LOADED = "main.config_loaded";
    public static final String MAIN_DIAGNOSTIC_SAVED = "main.diagnostic_saved";
    public static final String MAIN_FAILURE = "main.failure";
    public static final String MAIN_OP_SERVICE = "main.op.service";
    public static final String MAIN_OP_OPEN_SCREEN = "main.op.open_screen";
    public static final String MAIN_OP_PERMISSION = "main.op.permission";
    public static final String MAIN_OP_BATTERY = "main.op.battery";
    public static final String MAIN_OP_CONFIG = "main.op.config";
    public static final String MAIN_OP_DIAGNOSTIC = "main.op.diagnostic";
    public static final String LANG_SYSTEM = "lang.system";
    public static final String LANG_JAPANESE = "lang.japanese";
    public static final String LANG_ENGLISH = "lang.english";
    public static final String LANG_KOREAN = "lang.korean";
    public static final String AUTH_TITLE = "auth.title";
    public static final String QUEUE_TITLE = "queue.title";
    public static final String SAN_TITLE = "sanitize.title";
    public static final String AUTH_OPEN_APPROVAL = "auth.open_approval";
    public static final String AUTH_GET_SESSION = "auth.get_session";
    public static final String AUTH_SHOW_PC_URL = "auth.show_pc_url";
    public static final String AUTH_USE_NEW_UA = "auth.use_new_ua";
    public static final String AUTH_WEBVIEW_UNAVAILABLE = "auth.webview_unavailable";
    public static final String AUTH_GETTING_TOKEN = "auth.getting_token";
    public static final String AUTH_TOKEN_FAILED = "auth.token_failed";
    public static final String AUTH_OPEN_ON_PC = "auth.open_on_pc";
    public static final String AUTH_TOKEN_REQUIRED = "auth.token_required";
    public static final String AUTH_SESSION_FAILED = "auth.session_failed";
    public static final String AUTH_APPROVAL_URL = "auth.approval_url";
    public static final String AUTH_APPROVAL_URL_COPIED = "auth.approval_url_copied";
    public static final String QUEUE_REFRESH = "queue.refresh";
    public static final String QUEUE_SEND_NOW = "queue.send_now";
    public static final String QUEUE_RETRY_DEAD = "queue.retry_dead";
    public static final String QUEUE_DELETE_DEAD = "queue.delete_dead";
    public static final String QUEUE_CONFIRM_RETRY_DEAD = "queue.confirm_retry_dead";
    public static final String QUEUE_CONFIRM_DELETE_DEAD = "queue.confirm_delete_dead";
    public static final String QUEUE_LOADING = "queue.loading";
    public static final String QUEUE_LOAD_FAILED = "queue.load_failed";
    public static final String QUEUE_TOTAL = "queue.total";
    public static final String QUEUE_CANCEL = "queue.cancel";
    public static final String QUEUE_EXECUTE = "queue.execute";
    public static final String SAN_ENABLED = "san.enabled";
    public static final String SAN_DEFAULT_RULES = "san.default_rules";
    public static final String SAN_CUSTOM_RULES = "san.custom_rules";
    public static final String SAN_EDIT = "san.edit";
    public static final String SAN_DELETE = "san.delete";
    public static final String SAN_FORM = "san.form";
    public static final String SAN_ID = "san.id";
    public static final String SAN_REGEX = "san.regex";
    public static final String SAN_REPLACEMENT = "san.replacement";
    public static final String SAN_SAVE = "san.save";
    public static final String SAN_PREVIEW = "san.preview";
    public static final String SAN_PREVIEW_INPUT = "san.preview_input";
    public static final String SAN_PREVIEW_RESULT = "san.preview_result";
    public static final String SAN_ERROR_ID_EMPTY = "san.error.id_empty";
    public static final String SAN_ERROR_ID_DEFAULT = "san.error.id_default";
    public static final String SAN_ERROR_ID_DUPLICATE = "san.error.id_duplicate";
    public static final String SAN_ERROR_REGEX = "san.error.regex";
    public static final String SAN_NOTE_STRIP_ZERO_WIDTH = "san.note.strip_zero_width";
    public static final String SAN_NOTE_FULLWIDTH_SPACE = "san.note.fullwidth_space";
    public static final String SAN_NOTE_ALBUM_EP = "san.note.album_ep";
    public static final String SAN_NOTE_ALBUM_SINGLE = "san.note.album_single";
    public static final String SAN_NOTE_ALBUM_DELUXE = "san.note.album_deluxe";
    public static final String SAN_NOTE_ALBUM_REMASTER = "san.note.album_remaster";
    public static final String SAN_NOTE_ALBUM_BONUS = "san.note.album_bonus";
    public static final String SAN_NOTE_COLLAPSE_SPACES = "san.note.collapse_spaces";
    public static final String SAN_NOTE_TRIM_ENDS = "san.note.trim_ends";
    public static final String ADB_TITLE = "adb.title";
    public static final String ADB_PRIMARY_HELP = "adb.primary_help";
    public static final String ADB_PRIMARY_LABEL = "adb.primary_label";
    public static final String ADB_SECONDARY_HEADER = "adb.secondary_header";
    public static final String ADB_BATTERY_HELP = "adb.battery_help";
    public static final String ADB_LOG_HEADER = "adb.log_header";
    public static final String ADB_LAUNCHED = "adb.launched";
    public static final String ADB_NOT_FOUND = "adb.not_found";
    public static final String ADB_DENIED = "adb.denied";
    public static final String ADB_FAILED = "adb.failed";
    public static final String ADB_NONE_FOUND = "adb.none_found";
    public static final String CRED_AUTH = "cred.auth";
    public static final String UPDATE_AVAILABLE_TITLE = "update.available_title";
    public static final String UPDATE_REQUIRED_TITLE = "update.required_title";
    public static final String UPDATE_MESSAGE = "update.message";
    public static final String UPDATE_INSTALL = "update.install";
    public static final String UPDATE_LATER = "update.later";
    public static final String MAIN_VERSION = "main.version";
    public static final String UPDATE_DOWNLOADING = "update.downloading";
    public static final String UPDATE_NEEDS_PERMISSION = "update.needs_permission";
    public static final String UPDATE_FALLBACK_TITLE = "update.fallback_title";
    public static final String UPDATE_FALLBACK_MESSAGE = "update.fallback_message";
    public static final String UI_BACK = "ui.back";
    public static final String UI_SETTINGS = "ui.settings";
    public static final String SETUP_TITLE = "setup.title";
    public static final String SETUP_STEP_AUTH = "setup.step_auth";
    public static final String SETUP_STEP_SOURCES = "setup.step_sources";
    public static final String SETUP_REAUTH_NOTICE = "setup.reauth_notice";
    public static final String SETUP_SIGNING_NOTE = "setup.signing_note";
    public static final String SETUP_HEADING_AUTH = "setup.heading_auth";
    public static final String SETUP_AUTH_HELP = "setup.auth_help";
    public static final String SETUP_STORAGE_NEEDED = "setup.storage_needed";
    public static final String SETUP_LATER = "setup.later";
    public static final String SETUP_AFTER_KEYS = "setup.after_keys";
    public static final String HOME_SCROBBLING = "home.scrobbling";
    public static final String HOME_STOPPED = "home.stopped";
    public static final String HOME_SENDING_AS = "home.sending_as";
    public static final String HOME_NOW_PLAYING = "home.now_playing";
    public static final String HOME_NOTHING_PLAYING = "home.nothing_playing";
    public static final String HOME_SEC_SOURCES = "home.sec_sources";
    public static final String HOME_SEC_QUEUE = "home.sec_queue";
    public static final String HOME_SEC_DEVICE = "home.sec_device";
    public static final String HOME_QUEUE_PENDING = "home.queue_pending";
    public static final String HOME_QUEUE_DETAIL = "home.queue_detail";
    public static final String HOME_NEVER_SENT = "home.never_sent";
    public static final String HOME_ADB_SUB = "home.adb_sub";
    public static final String HOME_BANNER_AUTH_INVALID = "home.banner_auth_invalid";
    public static final String HOME_BANNER_NOT_AUTHED = "home.banner_not_authed";
    public static final String HOME_BANNER_MIGRATED = "home.banner_migrated";
    public static final String HOME_BANNER_SIGNER_UNAVAILABLE = "home.banner_signer_unavailable";
    public static final String HOME_BANNER_SERVICE_PROBLEM = "home.banner_service_problem";
    public static final String SRC_CONNECTED = "src.connected";
    public static final String SRC_CONNECTING = "src.connecting";
    public static final String SRC_DISCONNECTED = "src.disconnected";
    public static final String SRC_DISABLED = "src.disabled";
    public static final String SRC_NOT_INSTALLED = "src.not_installed";
    public static final String SRC_STOPPED = "src.stopped";
    public static final String SET_SEC_ACCOUNT = "settings.sec_account";
    public static final String SET_SEC_SCROBBLE = "settings.sec_scrobble";
    public static final String SET_SEC_VERSION = "settings.sec_version";
    public static final String SET_AUTHED = "settings.authed";
    public static final String SET_AUTH_INVALID = "settings.auth_invalid";
    public static final String SET_SANITIZE = "settings.sanitize";
    public static final String SET_LANGUAGE = "settings.language";
    public static final String SET_STORAGE = "settings.storage";
    public static final String SET_GRANTED = "settings.granted";
    public static final String SET_NOT_GRANTED = "settings.not_granted";
    public static final String SET_STATUS = "settings.status";
    public static final String SET_CHECK_UPDATE = "settings.check_update";
    public static final String SET_UP_TO_DATE = "settings.up_to_date";
    public static final String SET_UPDATE_FAILED = "settings.update_failed";

    private static final String[][] TABLE = {
        {NOTIF_TITLE, "AKScrobble", "AKScrobble", "AKScrobble"},
        {NOTIF_RUNNING, "動作中", "Running", "실행 중"},
        {NOTIF_STARTING, "起動中", "Starting", "시작 중"},
        {NOTIF_REAUTH, "再認証が必要", "Re-authentication required", "재인증이 필요합니다"},
        {MAIN_START, "サービス開始", "Start service", "서비스 시작"},
        {MAIN_STOP, "サービス停止", "Stop service", "서비스 중지"},
        {MAIN_AUTH, "Last.fm 認証", "Last.fm authentication", "Last.fm 인증"},
        {MAIN_IMPORT_CONFIG, "設定ファイル読込", "Load configuration file", "설정 파일 불러오기"},
        {MAIN_QUEUE, "キューを見る", "View queue", "대기열 보기"},
        {MAIN_FLUSH, "今すぐ送信", "Send now", "지금 보내기"},
        {MAIN_REQUEST_STORAGE, "ストレージ権限を要求", "Request storage permission", "저장소 권한 요청"},
        {MAIN_BATTERY_EXEMPTION, "電池最適化から除外", "Exclude from battery optimization", "배터리 최적화에서 제외"},
        {MAIN_BTN_SANITIZE, "サニタイズ設定", "Sanitize settings", "문자열 정리 설정"},
        {MAIN_BTN_ADB, "ADB を有効化", "Enable ADB", "ADB 활성화"},
        {MAIN_SAVE_DIAGNOSTIC, "診断ダンプを保存", "Save diagnostic dump", "진단 덤프 저장"},
        {MAIN_UNAUTHENTICATED, "未認証", "Not authenticated", "인증되지 않음"},
        {MAIN_STORAGE_RESULT, "ストレージ権限: read={0} write={1}", "Storage permission: read={0} write={1}", "저장소 권한: read={0} write={1}"},
        {MAIN_REQUEST_SENT, "要求を送信しました: {0}", "Request sent: {0}", "요청을 보냈습니다: {0}"},
        {MAIN_ALREADY_BATTERY_EXEMPT, "すでに電池最適化の対象外です。", "Already excluded from battery optimization.", "이미 배터리 최적화 대상에서 제외되었습니다."},
        {MAIN_BATTERY_UNSUPPORTED, "この端末は電池最適化の除外要求に非対応です。\n「ADB を有効化」画面から操作してください。\nPC から次を実行すると除外できます:\nadb shell dumpsys deviceidle whitelist +{0}", "This device does not support battery optimization exemption requests.\nUse the Enable ADB screen, then run this command from a PC:\nadb shell dumpsys deviceidle whitelist +{0}", "이 기기는 배터리 최적화 제외 요청을 지원하지 않습니다.\nADB 활성화 화면에서 설정한 후 PC에서 다음 명령을 실행하세요:\nadb shell dumpsys deviceidle whitelist +{0}"},
        {MAIN_CONFIG_CREATED, "設定ファイルがありません。テンプレートを作成しました。", "No configuration file was found. A template was created.", "설정 파일이 없습니다. 템플릿을 만들었습니다."},
        {MAIN_CONFIG_LOADED, "設定ファイルを読み込みました。", "Configuration file loaded.", "설정 파일을 불러왔습니다."},
        {MAIN_DIAGNOSTIC_SAVED, "診断ダンプを保存しました: {0}", "Diagnostic dump saved: {0}", "진단 덤프를 저장했습니다: {0}"},
        {MAIN_FAILURE, "{0}に失敗: {1}", "{0} failed: {1}", "{0} 실패: {1}"},
        {MAIN_OP_SERVICE, "サービス操作", "Service operation", "서비스 작업"},
        {MAIN_OP_OPEN_SCREEN, "画面起動", "Opening screen", "화면 열기"},
        {MAIN_OP_PERMISSION, "権限要求", "Permission request", "권한 요청"},
        {MAIN_OP_BATTERY, "電池設定", "Battery settings", "배터리 설정"},
        {MAIN_OP_CONFIG, "設定読込", "Loading configuration", "설정 불러오기"},
        {MAIN_OP_DIAGNOSTIC, "診断ダンプ", "Diagnostic dump", "진단 덤프"},
        {LANG_SYSTEM, "システム", "System", "시스템"},
        {LANG_JAPANESE, "日本語", "日本語", "日本語"},
        {LANG_ENGLISH, "English", "English", "English"},
        {LANG_KOREAN, "한국어", "한국어", "한국어"},
        {AUTH_TITLE, "Last.fm 認証", "Last.fm authentication", "Last.fm 인증"},
        {SAN_TITLE, "サニタイズ設定", "Sanitize settings", "문자열 정리 설정"},
        {QUEUE_TITLE, "Scrobble キュー", "Scrobble queue", "Scrobble 대기열"},
        {AUTH_OPEN_APPROVAL, "承認ページを開く", "Open approval page", "승인 페이지 열기"},
        {AUTH_GET_SESSION, "セッションを取得", "Get session", "세션 가져오기"},
        {AUTH_SHOW_PC_URL, "PC 用 URL を表示", "Show PC URL", "PC용 URL 표시"},
        {AUTH_USE_NEW_UA, "新しいブラウザ UA を使用", "Use a newer browser UA", "새 브라우저 UA 사용"},
        {AUTH_WEBVIEW_UNAVAILABLE, "WebView を利用できません。PC 用 URL を表示して PC で承認してください。", "WebView is unavailable. Show the PC URL and approve on a PC.", "WebView를 사용할 수 없습니다. PC용 URL을 표시해 PC에서 승인하세요."},
        {AUTH_GETTING_TOKEN, "トークンを取得しています…", "Getting token…", "토큰을 가져오는 중…"},
        {AUTH_TOKEN_FAILED, "トークン取得失敗: {0}", "Failed to get token: {0}", "토큰 가져오기 실패: {0}"},
        {AUTH_OPEN_ON_PC, "WebView は利用できません。PC で開いてください:\n{0}", "WebView is unavailable. Open this URL on a PC:\n{0}", "WebView를 사용할 수 없습니다. PC에서 다음 URL을 여세요:\n{0}"},
        {AUTH_TOKEN_REQUIRED, "先にトークンを取得してください。", "Get a token first.", "먼저 토큰을 가져오세요."},
        {AUTH_SESSION_FAILED, "セッション取得失敗: {0}", "Failed to get session: {0}", "세션 가져오기 실패: {0}"},
        {AUTH_APPROVAL_URL, "PC でも開ける承認 URL:\n{0}", "Approval URL (can also be opened on a PC):\n{0}", "PC에서도 열 수 있는 승인 URL:\n{0}"},
        {AUTH_APPROVAL_URL_COPIED, "承認 URL をクリップボードにコピーしました:\n{0}", "Approval URL copied to the clipboard:\n{0}", "승인 URL을 클립보드에 복사했습니다:\n{0}"},
        {QUEUE_REFRESH, "更新", "Refresh", "새로고침"},
        {QUEUE_SEND_NOW, "今すぐ送信", "Send now", "지금 보내기"},
        {QUEUE_RETRY_DEAD, "dead を再送", "Retry dead", "dead 다시 보내기"},
        {QUEUE_DELETE_DEAD, "dead を削除", "Delete dead", "dead 삭제"},
        {QUEUE_CONFIRM_RETRY_DEAD, "dead の全項目を pending に戻しますか？", "Return all dead items to pending?", "모든 dead 항목을 pending으로 돌릴까요?"},
        {QUEUE_CONFIRM_DELETE_DEAD, "dead の全項目を削除しますか？", "Delete all dead items?", "모든 dead 항목을 삭제할까요?"},
        {QUEUE_LOADING, "読み込み中...", "Loading...", "불러오는 중..."},
        {QUEUE_LOAD_FAILED, "キューの読み込みに失敗: {0}", "Failed to load queue: {0}", "대기열 불러오기 실패: {0}"},
        {QUEUE_TOTAL, "合計={0}", "total={0}", "합계={0}"},
        {QUEUE_CANCEL, "キャンセル", "Cancel", "취소"},
        {QUEUE_EXECUTE, "実行", "Execute", "실행"},
        {SAN_ENABLED, "サニタイズを有効にする", "Enable sanitizing", "문자열 정리 사용"},
        {SAN_DEFAULT_RULES, "既定ルール", "Default rules", "기본 규칙"},
        {SAN_CUSTOM_RULES, "自作ルール", "Custom rules", "사용자 규칙"},
        {SAN_EDIT, "編集", "Edit", "편집"},
        {SAN_DELETE, "削除", "Delete", "삭제"},
        {SAN_FORM, "ルール追加／編集", "Add or edit rule", "규칙 추가/편집"},
        {SAN_ID, "ルール ID", "Rule ID", "규칙 ID"},
        {SAN_REGEX, "正規表現", "Regular expression", "정규식"},
        {SAN_REPLACEMENT, "置換文字列", "Replacement", "대체 문자열"},
        {SAN_SAVE, "保存", "Save", "저장"},
        {SAN_PREVIEW, "プレビュー", "Preview", "미리보기"},
        {SAN_PREVIEW_INPUT, "プレビュー入力", "Preview input", "미리보기 입력"},
        {SAN_PREVIEW_RESULT, "結果: {0}", "Result: {0}", "결과: {0}"},
        {SAN_ERROR_ID_EMPTY, "ルール ID を入力してください。", "Enter a rule ID.", "규칙 ID를 입력하세요."},
        {SAN_ERROR_ID_DEFAULT, "既定ルールと同じ ID は使えません。", "The ID conflicts with a default rule.", "기본 규칙과 같은 ID는 사용할 수 없습니다."},
        {SAN_ERROR_ID_DUPLICATE, "既存の自作ルールと同じ ID は使えません。", "The ID conflicts with a custom rule.", "기존 사용자 규칙과 같은 ID는 사용할 수 없습니다."},
        {SAN_ERROR_REGEX, "正規表現が不正です: {0}", "Invalid regular expression: {0}", "잘못된 정규식: {0}"},
        {SAN_NOTE_STRIP_ZERO_WIDTH, "ゼロ幅文字を削除", "Remove zero-width characters", "폭 없는 문자 제거"},
        {SAN_NOTE_FULLWIDTH_SPACE, "全角スペースを置換", "Replace fullwidth spaces", "전각 공백 바꾸기"},
        {SAN_NOTE_ALBUM_EP, "EP 接尾辞を削除", "Remove the EP suffix", "EP 접미사 제거"},
        {SAN_NOTE_ALBUM_SINGLE, "Single 接尾辞を削除", "Remove the Single suffix", "Single 접미사 제거"},
        {SAN_NOTE_ALBUM_DELUXE, "Deluxe 版の接尾辞を削除", "Remove deluxe edition suffixes", "Deluxe 버전 접미사 제거"},
        {SAN_NOTE_ALBUM_REMASTER, "リマスター版の接尾辞を削除", "Remove remaster suffixes", "리마스터 접미사 제거"},
        {SAN_NOTE_ALBUM_BONUS, "Bonus Track Version 接尾辞を削除", "Remove the bonus track version suffix", "Bonus Track Version 접미사 제거"},
        {SAN_NOTE_COLLAPSE_SPACES, "連続するスペースをまとめる", "Collapse repeated spaces", "연속 공백 합치기"},
        {SAN_NOTE_TRIM_ENDS, "先頭と末尾の空白を削除", "Trim leading and trailing whitespace", "앞뒤 공백 제거"},
        {ADB_TITLE, "ADB を有効化", "Enable ADB", "ADB 활성화"},
        {ADB_PRIMARY_HELP, "このボタンを押すと ADB が有効になります", "Press this button to enable ADB", "이 버튼을 누르면 ADB가 활성화됩니다"},
        {ADB_PRIMARY_LABEL, "ADB を有効にする\n{0}", "Enable ADB\n{0}", "ADB 활성화\n{0}"},
        {ADB_SECONDARY_HEADER, "その他（上級者向け）", "Other (advanced)", "기타 (고급 사용자용)"},
        {ADB_BATTERY_HELP, "電池最適化から除外するには、ADB を有効化した後 PC で次を実行してください。", "To exclude the app from battery optimization, enable ADB and run this command on a PC.", "배터리 최적화에서 제외하려면 ADB를 활성화한 후 PC에서 다음 명령을 실행하세요."},
        {ADB_LOG_HEADER, "操作ログ", "Operation log", "작업 로그"},
        {ADB_LAUNCHED, "起動しました: {0}", "Launched: {0}", "실행됨: {0}"},
        {ADB_NOT_FOUND, "見つかりません: {0}", "Not found: {0}", "찾을 수 없음: {0}"},
        {ADB_DENIED, "起動を拒否されました: {0}", "Launch denied: {0}", "실행 거부됨: {0}"},
        {ADB_FAILED, "起動に失敗しました: {0} ({1})", "Launch failed: {0} ({1})", "실행 실패: {0} ({1})"},
        {ADB_NONE_FOUND, "ADB 設定画面が見つかりません。", "No ADB settings screen was found.", "ADB 설정 화면을 찾을 수 없습니다."},
        {CRED_AUTH, "Last.fm 認証へ進む", "Continue to Last.fm authentication", "Last.fm 인증으로 계속"},
        {UPDATE_AVAILABLE_TITLE, "更新があります", "Update available", "업데이트가 있습니다"},
        {UPDATE_REQUIRED_TITLE, "更新が必要です", "Update required", "업데이트가 필요합니다"},
        {UPDATE_MESSAGE, "バージョン {0} が利用できます。\n{1}", "Version {0} is available.\n{1}", "버전 {0}을 사용할 수 있습니다.\n{1}"},
        {UPDATE_INSTALL, "更新する", "Install", "설치"},
        {UPDATE_LATER, "後で", "Later", "나중에"},
        {MAIN_VERSION, "バージョン {0}", "Version {0}", "버전 {0}"},
        {UPDATE_DOWNLOADING, "更新をダウンロードしています…", "Downloading update…", "업데이트를 다운로드하는 중…"},
        {UPDATE_NEEDS_PERMISSION, "提供元不明のアプリのインストールを許可してから、もう一度更新してください。", "Allow installs from this source, then try the update again.", "이 출처의 앱 설치를 허용한 다음 업데이트를 다시 시도하세요."},
        {UPDATE_FALLBACK_TITLE, "手動で更新してください", "Update manually", "수동으로 업데이트하세요"},
        {UPDATE_FALLBACK_MESSAGE, "この機種では自動更新できません。xapk を Open APP Service で入れてください。\n{0}", "Automatic updates are not available on this device. Download the xapk from Releases and install it with Open APP Service.\n{0}", "이 기기에서는 자동 업데이트를 사용할 수 없습니다. Releases에서 xapk를 받아 Open APP Service로 설치하세요.\n{0}"},
        {UI_BACK, "‹ 戻る", "‹ Back", "‹ 뒤로"},
        {UI_SETTINGS, "設定", "Settings", "설정"},
        {SETUP_TITLE, "セットアップ", "Setup", "초기 설정"},
        {SETUP_STEP_AUTH, "1 認証", "1 Authorize", "1 인증"},
        {SETUP_STEP_SOURCES, "2 ソース", "2 Sources", "2 소스"},
        {SETUP_HEADING_AUTH, "Last.fm で認証します", "Authorize AKScrobble on Last.fm", "Last.fm에서 인증합니다"},
        {SETUP_AUTH_HELP, "Last.fm の承認ページで AKScrobble を許可してください。API キーの用意は不要です。", "Allow AKScrobble on the Last.fm approval page. You do not need your own API key.", "Last.fm 승인 페이지에서 AKScrobble을 허용하세요. API 키를 따로 준비할 필요가 없습니다."},
        {SETUP_STORAGE_NEEDED, "ファイルを扱うにはストレージ権限が必要です。", "Storage permission is required to use the file.", "파일을 사용하려면 저장소 권한이 필요합니다."},
        {SETUP_LATER, "あとで(ホームへ)", "Later (go to home)", "나중에 (홈으로)"},
        {SETUP_AFTER_KEYS, "認証後", "After authorization", "인증 후"},
        {HOME_SCROBBLING, "スクロブル中", "Scrobbling", "스크로블 중"},
        {HOME_STOPPED, "停止中", "Stopped", "중지됨"},
        {HOME_SENDING_AS, "{0} として送信", "Sending as {0}", "{0}(으)로 전송"},
        {HOME_NOW_PLAYING, "再生中 · {0}", "Now playing · {0}", "재생 중 · {0}"},
        {HOME_NOTHING_PLAYING, "再生中の曲はありません", "Nothing is playing", "재생 중인 곡이 없습니다"},
        {HOME_SEC_SOURCES, "ソース", "Sources", "소스"},
        {HOME_SEC_QUEUE, "送信キュー", "Scrobble queue", "전송 대기열"},
        {HOME_SEC_DEVICE, "端末", "Device", "기기"},
        {HOME_QUEUE_PENDING, "待機 {0} 件", "{0} pending", "대기 {0}건"},
        {HOME_QUEUE_DETAIL, "dead {0} 件 · 最終送信 {1}", "{0} dead · last sent {1}", "dead {0}건 · 마지막 전송 {1}"},
        {HOME_NEVER_SENT, "なし", "never", "없음"},
        {HOME_ADB_SUB, "開発者向け設定を開く", "Open developer settings", "개발자 설정 열기"},
        {HOME_BANNER_AUTH_INVALID, "Last.fm の認証が無効になりました。タップして再認証してください。", "Last.fm authorization is no longer valid. Tap to authorize again.", "Last.fm 인증이 더 이상 유효하지 않습니다. 눌러서 다시 인증하세요."},
        {HOME_BANNER_NOT_AUTHED, "Last.fm の認証がまだです。タップして認証してください。", "Last.fm is not authorized yet. Tap to authorize.", "아직 Last.fm 인증을 하지 않았습니다. 눌러서 인증하세요."},
        {SETUP_REAUTH_NOTICE, "アップデートで Last.fm の認証方式が変わりました。もう一度認証してください。未送信の scrobble は保持され、認証後に送信されます。", "This update changed how AKScrobble signs in to Last.fm. Please authorize again. Unsent scrobbles are kept and will be sent after you authorize.", "업데이트로 Last.fm 인증 방식이 바뀌었습니다. 다시 인증해 주세요. 전송되지 않은 scrobble은 보관되며 인증 후 전송됩니다."},
        {SETUP_SIGNING_NOTE, "送信のたびに署名サービス scrobsign.sukisuki.club が要求に署名します。署名サービスは session key と曲の情報を受け取りますが、保存しません。", "Each request is signed by the signing service scrobsign.sukisuki.club. It receives your session key and track details but does not store them.", "요청마다 서명 서비스 scrobsign.sukisuki.club가 서명합니다. 서명 서비스는 session key와 곡 정보를 받지만 저장하지 않습니다."},
        {HOME_BANNER_MIGRATED, "アップデートにより再認証が必要です。タップして認証してください。未送信の scrobble は保持しています。", "This update requires you to authorize again. Tap to authorize. Unsent scrobbles are kept.", "업데이트로 다시 인증해야 합니다. 탭하여 인증하세요. 전송되지 않은 scrobble은 보관 중입니다."},
        {HOME_BANNER_SIGNER_UNAVAILABLE, "署名サービスを利用できないため送信を止めています。未送信の scrobble は保持しています。", "Sending is paused because the signing service is unavailable. Unsent scrobbles are kept.", "서명 서비스를 사용할 수 없어 전송을 멈췄습니다. 전송되지 않은 scrobble은 보관 중입니다."},
        {HOME_BANNER_SERVICE_PROBLEM, "Last.fm 側の問題で送信できません。未送信の scrobble は保持し、あとで再送します。", "Last.fm is rejecting requests because of a problem on its side. Unsent scrobbles are kept and will be retried.", "Last.fm 측 문제로 전송할 수 없습니다. 전송되지 않은 scrobble은 보관하고 나중에 다시 보냅니다."},
        {SRC_CONNECTED, "接続中", "Connected", "연결됨"},
        {SRC_CONNECTING, "接続しています", "Connecting", "연결하는 중"},
        {SRC_DISCONNECTED, "未接続(再試行待ち)", "Not connected (will retry)", "연결 안 됨 (재시도 대기)"},
        {SRC_DISABLED, "無効", "Disabled", "비활성"},
        {SRC_NOT_INSTALLED, "未インストール", "Not installed", "설치되지 않음"},
        {SRC_STOPPED, "停止中", "Stopped", "중지됨"},
        {SET_SEC_ACCOUNT, "アカウント", "Account", "계정"},
        {SET_SEC_SCROBBLE, "スクロブル", "Scrobbling", "스크로블"},
        {SET_SEC_VERSION, "バージョン", "Version", "버전"},
        {SET_AUTHED, "認証済み ({0})", "Authorized ({0})", "인증됨 ({0})"},
        {SET_AUTH_INVALID, "再認証が必要です", "Authorization needed again", "다시 인증해야 합니다"},
        {SET_SANITIZE, "タイトルの整形(サニタイズ)", "Title cleanup (sanitize)", "제목 정리 (문자열 정리)"},
        {SET_LANGUAGE, "言語", "Language", "언어"},
        {SET_STORAGE, "ストレージ権限", "Storage permission", "저장소 권한"},
        {SET_GRANTED, "許可済み", "Granted", "허용됨"},
        {SET_NOT_GRANTED, "未許可(タップで要求)", "Not granted (tap to request)", "허용 안 됨 (눌러서 요청)"},
        {SET_STATUS, "詳細ステータス", "Detailed status", "상세 상태"},
        {SET_CHECK_UPDATE, "アップデートを確認", "Check for updates", "업데이트 확인"},
        {SET_UP_TO_DATE, "最新版です。", "You have the latest version.", "최신 버전입니다."},
        {SET_UPDATE_FAILED, "更新を確認できませんでした。", "Could not check for updates.", "업데이트를 확인할 수 없습니다."},
    };

    public static String get(Lang lang, String key, Object... args) {
        if (key == null) return null;
        String value = null;
        for (String[] row : TABLE) {
            if (key.equals(row[0])) {
                int column = lang == Lang.JA ? 1 : lang == Lang.KO ? 3 : 2;
                value = row[column];
                break;
            }
        }
        if (value == null) return key;
        return format(value, args);
    }

    /**
     * {0} 形式のプレースホルダを 1 パスで置換する。
     *
     * 逐次 replace にすると、置換した実引数の中に "{1}" のような文字列が
     * 含まれていた場合に次の周回でそれ自体が置換されてしまう。曲名や
     * アルバム名をそのまま埋め込むため、実引数は必ず素通しにする。
     * 対応する実引数が無いプレースホルダは書かれたまま残す。
     */
    private static String format(String value, Object[] args) {
        if (args == null || args.length == 0 || value.indexOf('{') < 0) return value;
        StringBuilder out = new StringBuilder(value.length() + 16);
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c != '{') { out.append(c); i++; continue; }
            int j = i + 1;
            while (j < value.length() && value.charAt(j) >= '0' && value.charAt(j) <= '9') j++;
            if (j == i + 1 || j >= value.length() || value.charAt(j) != '}') {
                out.append(c); i++; continue;
            }
            int index = Integer.parseInt(value.substring(i + 1, j));
            if (index < args.length) out.append(String.valueOf(args[index]));
            else out.append(value, i, j + 1);
            i = j + 1;
        }
        return out.toString();
    }

    public static Lang pick(String localeLanguage) {
        if ("ja".equalsIgnoreCase(localeLanguage)) return Lang.JA;
        if ("ko".equalsIgnoreCase(localeLanguage)) return Lang.KO;
        return Lang.EN;
    }

    public static String[] keys() {
        List<String> result = new ArrayList<String>(TABLE.length);
        for (String[] row : TABLE) result.add(row[0]);
        return result.toArray(new String[result.size()]);
    }

    private Strings() {}
}
