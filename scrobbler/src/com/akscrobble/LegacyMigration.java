package com.akscrobble;

import android.content.Context;
import android.util.Log;

import com.akscrobble.core.Const;
import com.akscrobble.core.LegacyConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Removes credentials and config entries left by pre-scrobsign versions. */
public final class LegacyMigration {
    public static synchronized void run(Context context) {
        try {
            migratePrefs(context);
        } catch (Throwable error) {
            Log.w(Const.TAG, "legacy migration failed: " + error.getClass().getSimpleName());
        }
        try {
            scrubConfig(context);
        } catch (Throwable error) {
            Log.w(Const.TAG, "legacy migration failed: " + error.getClass().getSimpleName());
        }
    }

    private static void migratePrefs(Context context) {
        Prefs prefs = new Prefs(context);
        if (prefs.getBoolean(Prefs.MIGRATED_SCROBSIGN)) return;

        boolean hadLegacy = !prefs.getString(Prefs.LEGACY_API_KEY).isEmpty()
                || !prefs.getString(Prefs.LEGACY_API_SECRET).isEmpty()
                || !prefs.getString(Prefs.SESSION_KEY).isEmpty();
        prefs.remove(Prefs.LEGACY_API_KEY, Prefs.LEGACY_API_SECRET, Prefs.SESSION_KEY,
                Prefs.SESSION_API_KEY, Prefs.USER_NAME, Prefs.AUTH_INVALID,
                Prefs.AUTH_INVALID_FOR);
        if (hadLegacy) prefs.putBoolean(Prefs.MIGRATION_NOTICE, true);
        prefs.putBoolean(Prefs.MIGRATED_SCROBSIGN, true);
    }

    private static void scrubConfig(Context context) {
        Prefs prefs = new Prefs(context);
        if (prefs.getBoolean(Prefs.CONFIG_SCRUBBED) || !AppActions.hasStorage(context)) return;

        boolean complete = true;
        for (File file : new ConfigStore(context).candidateFiles()) {
            try {
                if (!file.isFile()) continue;
                String original = readUtf8(file);
                LegacyConfig.Result result = LegacyConfig.strip(original);
                if (result.changed && result.meaningful) {
                    writeUtf8(file, result.text);
                    Log.i(Const.TAG, "scrubbed " + file.getName());
                } else if (result.changed) {
                    if (!file.delete()) throw new IllegalStateException("config delete failed");
                    Log.i(Const.TAG, "deleted " + file.getName());
                }
            } catch (Throwable error) {
                complete = false;
                Log.w(Const.TAG, "legacy config scrub failed: "
                        + error.getClass().getSimpleName());
            }
        }
        if (complete) prefs.putBoolean(Prefs.CONFIG_SCRUBBED, true);
    }

    private static String readUtf8(File file) throws Exception {
        InputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), "UTF-8");
        } finally {
            input.close();
        }
    }

    private static void writeUtf8(File file, String text) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(text.getBytes("UTF-8"));
        } finally {
            output.close();
        }
    }

    private LegacyMigration() {}
}
