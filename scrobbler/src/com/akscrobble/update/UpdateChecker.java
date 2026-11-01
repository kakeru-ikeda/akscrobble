package com.akscrobble.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.akscrobble.NetThread;
import com.akscrobble.core.Const;
import com.akscrobble.core.update.UpdateDecision;
import com.akscrobble.core.update.UpdateManifest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateChecker {
    private static final String MANIFEST_URL = "https://nyanstall.sukisuki.club/v1/akscrobble/stable/latest?current_code=";
    private static final String ALLOWED_URL_PREFIX = "https://nyanstall.sukisuki.club/";
    private static boolean checked;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback { void onResult(UpdateDecision decision, UpdateManifest manifest); }

    private UpdateChecker() {}

    public static void check(final Context context, final Callback callback) {
        synchronized (UpdateChecker.class) {
            if (checked) return;
            checked = true;
        }
        fetch(context.getApplicationContext(), callback, false);
    }

    public static void checkManually(Context context, Callback callback) {
        fetch(context.getApplicationContext(), callback, true);
    }

    private static void fetch(final Context app, final Callback callback,
            final boolean reportAll) {
        NetThread.post(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
                    final long installed = android.os.Build.VERSION.SDK_INT >= 28
                            ? info.getLongVersionCode() : info.versionCode;
                    connection = (HttpURLConnection) new URL(MANIFEST_URL + installed).openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);
                    connection.setRequestProperty("User-Agent", Const.USER_AGENT);
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                        throw new java.io.IOException("manifest HTTP " + connection.getResponseCode());
                    InputStream input = connection.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    try {
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    } finally { input.close(); }
                    final UpdateManifest manifest = UpdateManifest.parse(output.toString("UTF-8"));
                    final UpdateDecision decision = UpdateDecision.decide(installed, manifest, ALLOWED_URL_PREFIX);
                    if (decision == UpdateDecision.REJECTED) {
                        Log.w(Const.TAG, "Rejected update manifest URL");
                        if (reportAll) postResult(callback, null, null);
                        return;
                    }
                    if (decision == UpdateDecision.NONE && !reportAll) return;
                    postResult(callback, decision, manifest);
                } catch (Exception error) {
                    Log.w(Const.TAG, "Update check failed", error);
                    if (reportAll) postResult(callback, null, null);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static void postResult(final Callback callback, final UpdateDecision decision,
            final UpdateManifest manifest) {
        MAIN.post(new Runnable() {
            @Override public void run() {
                if (callback != null) callback.onResult(decision, manifest);
            }
        });
    }
}
