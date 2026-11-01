package com.akscrobble;

import android.content.Context;
import android.os.Environment;
import com.akscrobble.core.ConfigFile;
import com.akscrobble.core.Credentials;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ConfigStore {
    private final Context context;

    public ConfigStore(Context context) { this.context = context.getApplicationContext(); }

    /**
     * 認証情報を config > prefs > builtin の優先順位でフィールド単位に解決する。
     *
     * 呼び出し元ごとに prefs だけを直接読む実装が混ざると、UI では動くのに
     * ScrobbleService/Sender だけ api_key が空になる（実機で発生）。
     * 解決経路はこの 1 箇所に集約すること。
     */
    public static Credentials credentials(Context context) {
        Map<String, String> file = new ConfigStore(context).load();
        Prefs prefs = new Prefs(context);
        Map<String, String> saved = new HashMap<String, String>();
        saved.put("api_key", prefs.getString(Prefs.API_KEY));
        saved.put("api_secret", prefs.getString(Prefs.API_SECRET));
        saved.put("session_key", prefs.getString(Prefs.SESSION_KEY));
        return Credentials.resolve(file, saved, builtin("API_KEY"), builtin("API_SECRET"));
    }

    private static String builtin(String field) {
        try {
            return (String) Class.forName("com.akscrobble.core.BuildSecrets")
                    .getField(field).get(null);
        } catch (Throwable ignored) {
            return "";
        }
    }

    public Map<String, String> load() {
        File primary = primary();
        File secondary = secondary();
        File source = primary.isFile() ? primary : secondary;
        if (source == null || !source.isFile()) {
            writeTemplateIfAbsent();
            return Collections.emptyMap();
        }
        try {
            InputStream input = new FileInputStream(source);
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                return ConfigFile.parse(new String(output.toByteArray(), "UTF-8"));
            } finally { input.close(); }
        } catch (Exception ignored) { return Collections.emptyMap(); }
    }

    public void writeTemplateIfAbsent() {
        File target = primary();
        if (target.exists()) return;
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return;
            FileOutputStream output = new FileOutputStream(target);
            try { output.write(ConfigFile.template().getBytes("UTF-8")); }
            finally { output.close(); }
        } catch (Exception ignored) {}
    }

    private File primary() {
        return new File(Environment.getExternalStorageDirectory(), "AKScrobble/config.properties");
    }

    public File configFile() { return primary(); }

    private File secondary() {
        File directory = context.getExternalFilesDir(null);
        return directory == null ? null : new File(directory, "config.properties");
    }
}
