package com.akscrobble;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import com.akscrobble.core.Const;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.Strings;
import com.akscrobble.core.update.UpdateDecision;
import com.akscrobble.core.update.UpdateManifest;
import com.akscrobble.update.UpdateFlow;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public final class AppActions {
    public static String importConfig(Context context) {
        try {
            Map<String, String> values = new ConfigStore(context).load();
            Prefs prefs = new Prefs(context);
            copy(values, "api_key", Prefs.API_KEY, prefs);
            copy(values, "api_secret", Prefs.API_SECRET, prefs);
            copy(values, "session_key", Prefs.SESSION_KEY, prefs);
            copy(values, "user_name", Prefs.USER_NAME, prefs);
            String sanitize = values.get("sanitize");
            if (sanitize != null && sanitize.trim().length() > 0)
                SanitizeStore.save(context, SanitizeStore.load(context).withEnabled(
                        "on".equalsIgnoreCase(sanitize.trim())));
            return Ui.s(context, values.isEmpty()
                    ? Strings.MAIN_CONFIG_CREATED : Strings.MAIN_CONFIG_LOADED);
        } catch (Throwable error) {
            return failure(context, Strings.MAIN_OP_CONFIG, error);
        }
    }

    public static boolean hasStorage(Context context) {
        return hasReadStorage(context) && hasWriteStorage(context);
    }

    public static boolean hasReadStorage(Context context) {
        return granted(context, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static boolean hasWriteStorage(Context context) {
        return granted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static void requestStorage(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT < 23 || hasStorage(activity)) return;
        try {
            activity.requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE }, requestCode);
        } catch (Throwable error) {
            Log.e(Const.TAG, Ui.s(activity, Strings.MAIN_OP_PERMISSION) + " failed", error);
        }
    }

    public static String storageResult(Context context) {
        return Ui.s(context, Strings.MAIN_STORAGE_RESULT,
                granted(context, Manifest.permission.READ_EXTERNAL_STORAGE),
                granted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE));
    }

    public static String requestBatteryExemption(Activity activity) {
        try {
            PowerManager power = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && power != null
                    && power.isIgnoringBatteryOptimizations(activity.getPackageName()))
                return Ui.s(activity, Strings.MAIN_ALREADY_BATTERY_EXEMPT);
            Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + activity.getPackageName()));
            // AK ROM は「非対応」を表示するだけの Activity をこのアクションに登録している
            // （com.iriver.settings/.warnning.WarnnigUnsupportedActivity）。
            // resolveActivity は非 null を返すので解決可否では判定できない。
            // 遷移先が非対応画面なら行き止まりなので、遷移せず代替手段を案内する。
            ResolveInfo target = activity.getPackageManager().resolveActivity(request, 0);
            if (target == null || isUnsupportedStub(target))
                return Ui.s(activity, Strings.MAIN_BATTERY_UNSUPPORTED, activity.getPackageName());
            activity.startActivity(request);
            return null;
        } catch (Throwable error) {
            return failure(activity, Strings.MAIN_OP_BATTERY, error);
        }
    }

    public static boolean isBatteryExempt(Context context) {
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return Build.VERSION.SDK_INT >= 23 && power != null
                    && power.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Throwable ignored) { return false; }
    }

    public static String saveDiagnosticDump(Context context) {
        ScrobbleDb db = new ScrobbleDb(context);
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), Const.CONFIG_DIR);
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir failed");
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File target = new File(dir, "diag-" + stamp + ".txt");
            StringBuilder text = new StringBuilder("STATUS\n").append(StatusBus.snapshotText())
                    .append("\n\nPREFS\n");
            SharedPreferences all = context.getSharedPreferences("aks", Context.MODE_PRIVATE);
            for (Map.Entry<String, ?> entry : all.getAll().entrySet()) {
                String key = entry.getKey();
                // 診断ダンプは issue に添付させる想定なので、秘密は全て伏せる。
                // api_key もユーザー自身が取得した資格情報なので伏せる
                // （どこから解決されたかは api_key source 行で分かる）。
                Object value = Prefs.SESSION_KEY.equals(key) || Prefs.API_SECRET.equals(key)
                        || Prefs.API_KEY.equals(key) ? "***" : entry.getValue();
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
            return Ui.s(context, Strings.MAIN_DIAGNOSTIC_SAVED, target.getAbsolutePath());
        } catch (Throwable error) {
            return failure(context, Strings.MAIN_OP_DIAGNOSTIC, error);
        } finally { db.close(); }
    }

    public static String sendService(Context context, String action) {
        try {
            if (Const.ACTION_START.equals(action) || Const.ACTION_STOP.equals(action))
                new Prefs(context).putBoolean(Prefs.SERVICE_ENABLED, Const.ACTION_START.equals(action));
            Intent intent = new Intent(context, ScrobbleService.class).setAction(action);
            if (Build.VERSION.SDK_INT >= 26 && Const.ACTION_START.equals(action))
                context.startForegroundService(intent);
            else context.startService(intent);
            return Ui.s(context, Strings.MAIN_REQUEST_SENT, action);
        } catch (Throwable error) {
            return failure(context, Strings.MAIN_OP_SERVICE, error);
        }
    }

    public static void setSourceEnabled(Context context, String pkg, boolean enabled) {
        Prefs prefs = new Prefs(context);
        prefs.putBoolean(Prefs.sourceEnabledKey(pkg), enabled);
        if (prefs.getBoolean(Prefs.SERVICE_ENABLED)) {
            try {
                context.startService(new Intent(context, ScrobbleService.class)
                        .setAction(Const.ACTION_SOURCES));
            } catch (Throwable error) {
                Log.w(Const.TAG, "Unable to apply source preference", error);
            }
        }
    }

    public static String apiKeySource(Context context) {
        return ConfigStore.credentials(context).source("api_key");
    }

    public static void showUpdatePrompt(final Activity activity, UpdateDecision decision,
            final UpdateManifest manifest) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        boolean required = decision == UpdateDecision.REQUIRED;
        AlertDialog.Builder prompt = new AlertDialog.Builder(activity)
                .setTitle(Ui.s(activity, required
                        ? Strings.UPDATE_REQUIRED_TITLE : Strings.UPDATE_AVAILABLE_TITLE))
                .setMessage(Ui.s(activity, Strings.UPDATE_MESSAGE,
                        manifest.version, manifest.notes == null ? "" : manifest.notes))
                .setPositiveButton(Ui.s(activity, Strings.UPDATE_INSTALL),
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                UpdateFlow.start(activity, manifest);
                            }
                        });
        if (required) prompt.setCancelable(false);
        else prompt.setNegativeButton(Ui.s(activity, Strings.UPDATE_LATER), null);
        prompt.show();
    }

    private static boolean granted(Context context, String permission) {
        return Build.VERSION.SDK_INT < 23
                || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isUnsupportedStub(ResolveInfo info) {
        String name = info.activityInfo == null ? "" : String.valueOf(info.activityInfo.name);
        return name.contains("Unsupported") || name.contains("Warnnig") || name.contains("Warning");
    }

    private static void copy(Map<String, String> source, String key, String pref, Prefs prefs) {
        String value = source.get(key);
        if (value != null && value.trim().length() > 0) prefs.putString(pref, value.trim());
    }

    private static String oneLine(Object value) {
        return String.valueOf(value)
                .replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\u001F", "<US>");
    }

    private static String failure(Context context, String operationKey, Throwable error) {
        String operation = Ui.s(context, operationKey);
        Log.e(Const.TAG, operation + " failed", error);
        return Ui.s(context, Strings.MAIN_FAILURE,
                operation, error.getClass().getSimpleName());
    }

    private AppActions() {}
}
