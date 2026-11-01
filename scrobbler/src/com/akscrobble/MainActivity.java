package com.akscrobble;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.akscrobble.core.Const;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.Strings;
import com.akscrobble.core.update.UpdateDecision;
import com.akscrobble.core.update.UpdateManifest;
import com.akscrobble.update.UpdateChecker;
import com.akscrobble.update.UpdateFlow;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int REQ_STORAGE = 1201;
    private final Handler handler = new Handler();
    private TextView status;
    private String notice = "";
    private boolean refreshing;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { updateStatus(); if (refreshing) handler.postDelayed(this, 1000L); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        installCrashHandler();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(12, 12, 12, 12);
        addLanguageButtons(content);
        addButton(content, Ui.s(this, Strings.MAIN_START), new View.OnClickListener() {
            @Override public void onClick(View v) { sendService(Const.ACTION_START, true); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_STOP), new View.OnClickListener() {
            @Override public void onClick(View v) { sendService(Const.ACTION_STOP, false); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_BTN_CREDENTIALS), new View.OnClickListener() {
            @Override public void onClick(View v) { open(CredentialsActivity.class); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_AUTH), new View.OnClickListener() {
            @Override public void onClick(View v) { open(AuthActivity.class); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_IMPORT_CONFIG), new View.OnClickListener() {
            @Override public void onClick(View v) { importConfig(); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_QUEUE), new View.OnClickListener() {
            @Override public void onClick(View v) { open(QueueActivity.class); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_FLUSH), new View.OnClickListener() {
            @Override public void onClick(View v) { sendService(Const.ACTION_FLUSH, false); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_REQUEST_STORAGE), new View.OnClickListener() {
            @Override public void onClick(View v) { requestStorage(); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_BATTERY_EXEMPTION), new View.OnClickListener() {
            @Override public void onClick(View v) { requestBatteryExemption(); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_BTN_SANITIZE), new View.OnClickListener() {
            @Override public void onClick(View v) { open(SanitizeActivity.class); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_BTN_ADB), new View.OnClickListener() {
            @Override public void onClick(View v) { open(AdbActivity.class); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_SAVE_DIAGNOSTIC), new View.OnClickListener() {
            @Override public void onClick(View v) { saveDiagnosticDump(); }
        });
        TextView version = new TextView(this);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        version.setPadding(8, 12, 8, 4);
        try {
            version.setText(Ui.s(this, Strings.MAIN_VERSION,
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
        } catch (Exception ignored) { }
        content.addView(version);
        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        status.setTypeface(android.graphics.Typeface.MONOSPACE);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(Color.BLACK);
        status.setTextIsSelectable(true);
        status.setPadding(8, 12, 8, 12);
        content.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        requestStorage();
        UpdateChecker.check(this, new UpdateChecker.Callback() {
            @Override public void onResult(UpdateDecision decision, final UpdateManifest manifest) {
                if (isFinishing() || isDestroyed()) return;
                boolean required = decision == UpdateDecision.REQUIRED;
                AlertDialog.Builder prompt = new AlertDialog.Builder(MainActivity.this)
                        .setTitle(Ui.s(MainActivity.this, required
                                ? Strings.UPDATE_REQUIRED_TITLE : Strings.UPDATE_AVAILABLE_TITLE))
                        .setMessage(Ui.s(MainActivity.this, Strings.UPDATE_MESSAGE,
                                manifest.version, manifest.notes == null ? "" : manifest.notes))
                        .setPositiveButton(Ui.s(MainActivity.this, Strings.UPDATE_INSTALL),
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        UpdateFlow.start(MainActivity.this, manifest);
                                    }
                                });
                if (required) prompt.setCancelable(false);
                else prompt.setNegativeButton(Ui.s(MainActivity.this, Strings.UPDATE_LATER), null);
                prompt.show();
            }
        });
    }

    @Override protected void onResume() {
        super.onResume(); refreshing = true; handler.removeCallbacks(refresh); handler.post(refresh);
        UpdateFlow.resumeIfPermitted(this);
    }
    @Override protected void onPause() {
        refreshing = false; handler.removeCallbacks(refresh); super.onPause();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_STORAGE) showNotice(Ui.s(this, Strings.MAIN_STORAGE_RESULT,
                granted(Manifest.permission.READ_EXTERNAL_STORAGE),
                granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)));
    }

    private void installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, final Throwable error) {
                Log.e(Const.TAG, "Uncaught exception on " + thread.getName(), error);
                try { runOnUiThread(new Runnable() { @Override public void run() {
                    if (status != null) status.setText("UNCAUGHT\n" + Log.getStackTraceString(error));
                }}); } catch (Throwable ignored) {}
            }
        });
    }

    private void sendService(String action, boolean enabled) {
        try {
            if (Const.ACTION_START.equals(action) || Const.ACTION_STOP.equals(action))
                new Prefs(this).putBoolean(Prefs.SERVICE_ENABLED, enabled);
            Intent intent = new Intent(this, ScrobbleService.class).setAction(action);
            if (Build.VERSION.SDK_INT >= 26 && Const.ACTION_START.equals(action))
                startForegroundService(intent); else startService(intent);
            showNotice(Ui.s(this, Strings.MAIN_REQUEST_SENT, action));
        } catch (Throwable e) { showFailure(Strings.MAIN_OP_SERVICE, e); }
    }
    private void open(Class<?> activity) {
        try { startActivity(new Intent(this, activity)); }
        catch (Throwable e) { showFailure(Strings.MAIN_OP_OPEN_SCREEN, e); }
    }
    private void requestStorage() {
        if (Build.VERSION.SDK_INT < 23 || (granted(Manifest.permission.READ_EXTERNAL_STORAGE)
                && granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))) return;
        try { requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQ_STORAGE); }
        catch (Throwable e) { showFailure(Strings.MAIN_OP_PERMISSION, e); }
    }
    private boolean granted(String permission) {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestBatteryExemption() {
        try {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && power != null
                    && power.isIgnoringBatteryOptimizations(getPackageName())) {
                showNotice(Ui.s(this, Strings.MAIN_ALREADY_BATTERY_EXEMPT));
                return;
            }
            Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            // AK ROM は「非対応」を表示するだけの Activity をこのアクションに登録している
            // （com.iriver.settings/.warnning.WarnnigUnsupportedActivity）。
            // resolveActivity は非 null を返すので解決可否では判定できない。
            // 遷移先が非対応画面なら行き止まりなので、遷移せず代替手段を案内する。
            ResolveInfo target = getPackageManager().resolveActivity(request, 0);
            if (target == null || isUnsupportedStub(target)) {
                showNotice(Ui.s(this, Strings.MAIN_BATTERY_UNSUPPORTED, getPackageName()));
                return;
            }
            startActivity(request);
        } catch (Throwable e) {
            showFailure(Strings.MAIN_OP_BATTERY, e);
        }
    }

    private static boolean isUnsupportedStub(ResolveInfo info) {
        String name = info.activityInfo == null ? "" : String.valueOf(info.activityInfo.name);
        return name.contains("Unsupported") || name.contains("Warnnig") || name.contains("Warning");
    }

    private void importConfig() {
        try {
            Map<String, String> values = new ConfigStore(this).load();
            Prefs prefs = new Prefs(this);
            copy(values, "api_key", Prefs.API_KEY, prefs); copy(values, "api_secret", Prefs.API_SECRET, prefs);
            copy(values, "session_key", Prefs.SESSION_KEY, prefs); copy(values, "user_name", Prefs.USER_NAME, prefs);
            String sanitize = values.get("sanitize");
            if (sanitize != null && sanitize.trim().length() > 0)
                SanitizeStore.save(this, SanitizeStore.load(this).withEnabled(
                        "on".equalsIgnoreCase(sanitize.trim())));
            showNotice(Ui.s(this, values.isEmpty()
                    ? Strings.MAIN_CONFIG_CREATED : Strings.MAIN_CONFIG_LOADED));
        } catch (Throwable e) { showFailure(Strings.MAIN_OP_CONFIG, e); }
    }
    private static void copy(Map<String, String> source, String key, String pref, Prefs prefs) {
        String value = source.get(key);
        if (value != null && value.trim().length() > 0) prefs.putString(pref, value.trim());
    }

    private void updateStatus() {
        if (status == null) return;
        Prefs prefs = new Prefs(this);
        com.akscrobble.core.SanitizeConfig sanitize = SanitizeStore.load(this);
        ScrobbleDb db = new ScrobbleDb(this);
        String counts;
        try { counts = "pending=" + db.countPending() + " dead=" + db.countDead(); }
        catch (Throwable e) { counts = "unavailable: " + e.getClass().getSimpleName(); }
        finally { db.close(); }
        String user = prefs.getString(Prefs.USER_NAME);
        String snapshot = StatusBus.snapshotText();
        if (snapshot.length() == 0) snapshot = "service: stopped/no status";
        status.setText((notice.length() == 0 ? "" : notice + "\n\n") + snapshot
                + "\nqueue: " + counts + "\nauth: "
                + (user.length() == 0 ? Ui.s(this, Strings.MAIN_UNAUTHENTICATED) : user)
                + "\napi_key source: " + apiKeySource()
                + "\nsanitize: " + (sanitize.enabled ? "on" : "off")
                + " (custom=" + sanitize.customRules.size() + ")"
                + "\nstorage: read=" + granted(Manifest.permission.READ_EXTERNAL_STORAGE)
                + " write=" + granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                + "\nbattery exemption: " + batteryExempt());
    }
    private String apiKeySource() {
        try {
            String config = new ConfigStore(this).load().get("api_key");
            if (config != null && config.trim().length() > 0) return "config";
        } catch (Throwable ignored) {}
        if (new Prefs(this).getString(Prefs.API_KEY).length() > 0) return "prefs";
        try {
            Object key = Class.forName("com.akscrobble.core.BuildSecrets").getField("API_KEY").get(null);
            if (key instanceof String && ((String) key).length() > 0) return "builtin";
        } catch (Throwable ignored) {}
        return "unset";
    }
    private boolean batteryExempt() {
        try {
            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable ignored) { return false; }
    }

    private void saveDiagnosticDump() {
        // run-as cannot be used on the production player; this is the only internal-state export path.
        ScrobbleDb db = new ScrobbleDb(this);
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), Const.CONFIG_DIR);
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir failed");
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File target = new File(dir, "diag-" + stamp + ".txt");
            StringBuilder text = new StringBuilder("STATUS\n").append(StatusBus.snapshotText())
                    .append("\n\nPREFS\n");
            SharedPreferences all = getSharedPreferences("aks", Context.MODE_PRIVATE);
            for (Map.Entry<String, ?> entry : all.getAll().entrySet()) {
                String key = entry.getKey();
                // 診断ダンプは issue に添付させる想定なので、秘密は全て伏せる。
                // api_key もユーザー自身が取得した資格情報なので伏せる
                // （どこから解決されたかは api_key source 行で分かる）。
                Object value = Prefs.SESSION_KEY.equals(key) || Prefs.API_SECRET.equals(key)
                        || Prefs.API_KEY.equals(key)
                        ? "***" : entry.getValue();
                // sanitize_config は改行と区切り文字を含む。1 pref = 1 行を保って
                // 診断ダンプを読める形に留める。
                text.append(key).append('=').append(oneLine(value)).append('\n');
            }
            text.append("\nQUEUE\n");
            for (PendingScrobble item : db.recent(200)) text.append(item.id).append(' ')
                    .append(item.state).append(" attempts=").append(item.attempts).append(' ')
                    .append(item.startedAtSec).append(' ').append(item.artist).append(" - ")
                    .append(item.track).append(" album=").append(item.album)
                    .append(" error=").append(item.lastError).append('\n');
            text.append("\nDEVICE\nmanufacturer=").append(Build.MANUFACTURER)
                    .append("\nmodel=").append(Build.MODEL).append("\nsdk=").append(Build.VERSION.SDK_INT)
                    .append("\nrelease=").append(Build.VERSION.RELEASE).append('\n');
            FileOutputStream output = new FileOutputStream(target);
            try { output.write(text.toString().getBytes("UTF-8")); } finally { output.close(); }
            showNotice(Ui.s(this, Strings.MAIN_DIAGNOSTIC_SAVED, target.getAbsolutePath()));
        } catch (Throwable e) { showFailure(Strings.MAIN_OP_DIAGNOSTIC, e); }
        finally { db.close(); }
    }
    private static String oneLine(Object value) {
        return String.valueOf(value)
                .replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\u001F", "<US>");
    }

    private void showNotice(String message) { notice = message; updateStatus(); }
    private void showFailure(String operationKey, Throwable error) {
        String operation = Ui.s(this, operationKey);
        Log.e(Const.TAG, operation + " failed", error);
        showNotice(Ui.s(this, Strings.MAIN_FAILURE,
                operation, error.getClass().getSimpleName()));
    }
    private void addLanguageButtons(LinearLayout parent) {
        LinearLayout languages = new LinearLayout(this);
        languages.setOrientation(LinearLayout.HORIZONTAL);
        String selected = new Prefs(this).getString(Prefs.LANGUAGE);
        addLanguageButton(languages, Strings.LANG_SYSTEM, "", selected);
        addLanguageButton(languages, Strings.LANG_JAPANESE, "ja", selected);
        addLanguageButton(languages, Strings.LANG_ENGLISH, "en", selected);
        addLanguageButton(languages, Strings.LANG_KOREAN, "ko", selected);
        parent.addView(languages, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private void addLanguageButton(LinearLayout parent, String labelKey,
            final String value, String selected) {
        Button button = addButton(parent, Ui.s(this, labelKey),
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        new Prefs(MainActivity.this).putString(Prefs.LANGUAGE, value);
                        recreate();
                    }
                });
        // 選択中の印は文字を増やさずに付ける。"* " のような接頭辞を足すと
        // 日本語の「システム」が 2 行に折り返し、その行だけボタンが縦に伸びる（実機で確認）。
        if (value.equals(selected)) {
            button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            button.getPaint().setUnderlineText(true);
        }
        button.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }
    private Button addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        button.setOnClickListener(listener); parent.addView(button); return button;
    }
}
