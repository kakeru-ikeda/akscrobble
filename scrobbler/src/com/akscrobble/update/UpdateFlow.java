package com.akscrobble.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.akscrobble.NetThread;
import com.akscrobble.Ui;
import com.akscrobble.core.Strings;
import com.akscrobble.core.update.UpdateManifest;
import java.io.File;
import java.io.IOException;

public final class UpdateFlow {
    private static final String RELEASES_URL = "https://github.com/kakeru-ikeda/akscrobble/releases";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateFlow() {}

    public static void start(final Activity activity, final UpdateManifest manifest) {
        InstallOutcome.setListener(new InstallOutcome.Listener() {
            @Override public void onOutcome(InstallOutcome outcome, String message) {
                if (outcome == InstallOutcome.FAILED) showFallback(activity);
                else if (outcome == InstallOutcome.NEEDS_PERMISSION && !activity.isFinishing())
                    new AlertDialog.Builder(activity)
                            .setMessage(Ui.s(activity, Strings.UPDATE_NEEDS_PERMISSION))
                            .setPositiveButton(android.R.string.ok, null).show();
            }
        });
        Toast.makeText(activity, Ui.s(activity, Strings.UPDATE_DOWNLOADING), Toast.LENGTH_LONG).show();
        NetThread.post(new Runnable() {
            @Override public void run() {
                try {
                    File apk = ApkDownloader.download(activity, manifest);
                    ApkVerifier.verify(activity, apk, manifest.versionCode);
                    InstallOutcome outcome = SelfInstaller.install(activity, apk);
                    if (outcome == InstallOutcome.NEEDS_PERMISSION)
                        InstallOutcome.notify(InstallOutcome.NEEDS_PERMISSION, null);
                } catch (Exception error) {
                    MAIN.post(new Runnable() {
                        @Override public void run() { showFallback(activity); }
                    });
                }
            }
        });
    }

    public static void showFallback(final Activity activity) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity).setTitle(Ui.s(activity, Strings.UPDATE_FALLBACK_TITLE))
                .setMessage(Ui.s(activity, Strings.UPDATE_FALLBACK_MESSAGE, RELEASES_URL))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { }
                }).show();
    }
}
