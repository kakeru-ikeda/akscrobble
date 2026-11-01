package com.akscrobble.update;

import android.content.Context;
import com.akscrobble.core.update.Sha256Sink;
import com.akscrobble.core.update.UpdateManifest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ApkDownloader {
    private ApkDownloader() {}

    public static File download(Context context, UpdateManifest m) throws IOException {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create update cache");
        File[] old = dir.listFiles();
        if (old != null) for (File file : old) if (file.isFile()) file.delete();
        File target = new File(dir, m.versionCode + ".apk");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(m.url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new IOException("update server returned HTTP " + connection.getResponseCode());
            InputStream in = connection.getInputStream();
            FileOutputStream raw = new FileOutputStream(target);
            Sha256Sink sink = new Sha256Sink(raw);
            try {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (sink.bytesWritten() + n > m.size) throw new IOException("APK exceeds declared size");
                    sink.write(buffer, 0, n);
                }
                sink.flush();
                if (sink.bytesWritten() != m.size || !sink.hexDigest().equals(m.sha256))
                    throw new IOException("APK size or SHA-256 mismatch");
            } finally {
                try { in.close(); } finally { sink.close(); }
            }
            return target;
        } catch (IOException | RuntimeException ex) {
            target.delete();
            if (ex instanceof IOException) throw (IOException) ex;
            throw new IOException("APK download failed", ex);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
