package com.akscrobble.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import java.io.File;
import java.security.MessageDigest;

public final class ApkVerifier {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private ApkVerifier() {}

    public static void verify(Context context, File apk, long expectedMinCode) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info;
            SigningInfo signing = null;
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info != null) signing = info.signingInfo;
                if (signing == null) {
                    info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
                    signatures = info == null ? null : info.signatures;
                } else {
                    if (signing.hasMultipleSigners()) throw new SecurityException("multiple APK signers");
                    signatures = signing.getApkContentsSigners();
                }
            } else {
                info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
                signatures = info == null ? null : info.signatures;
            }
            if (info == null) throw new SecurityException("invalid APK");
            if (!context.getPackageName().equals(info.packageName)) throw new SecurityException("unexpected APK package");
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            if (code < expectedMinCode) throw new SecurityException("APK version is too old");
            if (signatures == null || signatures.length != 1) throw new SecurityException("unexpected APK signer count");
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) { int v = b & 255; hex.append(HEX[v >>> 4]).append(HEX[v & 15]); }
            if (!ReleasePin.SIGNER_SHA256.equals(hex.toString())) throw new SecurityException("untrusted APK signer");
        } catch (SecurityException ex) { throw ex; }
        catch (Exception ex) { throw new SecurityException("cannot verify APK", ex); }
    }
}
