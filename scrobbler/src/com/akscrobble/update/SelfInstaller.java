package com.akscrobble.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

public final class SelfInstaller {
    private static final int FLAG_MUTABLE = 0x02000000;
    private static final int USER_ACTION_NOT_REQUIRED = 2;
    private SelfInstaller() {}

    public static InstallOutcome install(Context context, File apk) throws Exception {
        if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            return InstallOutcome.NEEDS_PERMISSION;
        }
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        int sessionId = -1;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    Method method = PackageInstaller.SessionParams.class.getMethod("setRequireUserAction", int.class);
                    method.invoke(params, USER_ACTION_NOT_REQUIRED);
                } catch (Exception ignored) { }
            }
            sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            try {
                OutputStream out = session.openWrite("base.apk", 0, apk.length());
                FileInputStream in = new FileInputStream(apk);
                try {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    session.fsync(out);
                } finally { try { in.close(); } finally { out.close(); } }
                Intent result = new Intent(context, InstallResultReceiver.class);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= FLAG_MUTABLE;
                PendingIntent pi = PendingIntent.getBroadcast(context, sessionId, result, flags);
                session.commit(pi.getIntentSender());
            } finally { session.close(); }
            return InstallOutcome.STARTED;
        } catch (Exception ex) {
            if (sessionId >= 0) try { installer.abandonSession(sessionId); } catch (Exception ignored) { }
            throw ex;
        }
    }
}
