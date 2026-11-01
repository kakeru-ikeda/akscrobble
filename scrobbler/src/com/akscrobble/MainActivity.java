package com.akscrobble;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.akscrobble.core.Const;
import com.akscrobble.core.LaunchRoute;
import com.akscrobble.core.Strings;
import com.akscrobble.core.update.UpdateDecision;
import com.akscrobble.core.update.UpdateManifest;
import com.akscrobble.update.UpdateChecker;
import com.akscrobble.update.UpdateFlow;

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
        if (LaunchRoute.decide(ConfigStore.credentials(this)) == LaunchRoute.SETUP) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(12, 12, 12, 12);
        addLanguageButtons(content);
        addButton(content, Ui.s(this, Strings.MAIN_START), new View.OnClickListener() {
            @Override public void onClick(View v) { showNotice(AppActions.sendService(MainActivity.this, Const.ACTION_START)); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_STOP), new View.OnClickListener() {
            @Override public void onClick(View v) { showNotice(AppActions.sendService(MainActivity.this, Const.ACTION_STOP)); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_BTN_CREDENTIALS), new View.OnClickListener() {
            @Override public void onClick(View v) { open(CredentialsActivity.class); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_AUTH), new View.OnClickListener() {
            @Override public void onClick(View v) { open(AuthActivity.class); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_IMPORT_CONFIG), new View.OnClickListener() {
            @Override public void onClick(View v) { showNotice(AppActions.importConfig(MainActivity.this)); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_QUEUE), new View.OnClickListener() {
            @Override public void onClick(View v) { open(QueueActivity.class); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_FLUSH), new View.OnClickListener() {
            @Override public void onClick(View v) { showNotice(AppActions.sendService(MainActivity.this, Const.ACTION_FLUSH)); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_REQUEST_STORAGE), new View.OnClickListener() {
            @Override public void onClick(View v) { AppActions.requestStorage(MainActivity.this, REQ_STORAGE); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_BATTERY_EXEMPTION), new View.OnClickListener() {
            @Override public void onClick(View v) { String message = AppActions.requestBatteryExemption(MainActivity.this);
                if (message != null) showNotice(message); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_BTN_SANITIZE), new View.OnClickListener() {
            @Override public void onClick(View v) { open(SanitizeActivity.class); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_BTN_ADB), new View.OnClickListener() {
            @Override public void onClick(View v) { open(AdbActivity.class); }
        });
        addButton(content, Ui.s(this, Strings.MAIN_SAVE_DIAGNOSTIC), new View.OnClickListener() {
            @Override public void onClick(View v) { showNotice(AppActions.saveDiagnosticDump(MainActivity.this)); }
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
        AppActions.requestStorage(this, REQ_STORAGE);
        UpdateChecker.check(this, new UpdateChecker.Callback() {
            @Override public void onResult(UpdateDecision decision, final UpdateManifest manifest) {
                AppActions.showUpdatePrompt(MainActivity.this, decision, manifest);
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
        if (code == REQ_STORAGE) showNotice(AppActions.storageResult(this));
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

    private void open(Class<?> activity) {
        try { startActivity(new Intent(this, activity)); }
        catch (Throwable e) { showFailure(Strings.MAIN_OP_OPEN_SCREEN, e); }
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
                + "\napi_key source: " + AppActions.apiKeySource(this)
                + "\nsanitize: " + (sanitize.enabled ? "on" : "off")
                + " (custom=" + sanitize.customRules.size() + ")"
                + "\nstorage: read=" + AppActions.hasReadStorage(this)
                + " write=" + AppActions.hasWriteStorage(this)
                + "\nbattery exemption: " + AppActions.isBatteryExempt(this));
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
