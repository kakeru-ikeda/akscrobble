package com.akscrobble;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;
import com.akscrobble.core.Const;
import com.akscrobble.core.Scrobsign;
import com.akscrobble.core.SignResult;
import com.akscrobble.core.Signer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

/** Synchronous signing client. Call only from a runnable submitted through {@link NetThread}. */
public final class SignerClient implements Signer {
    private final String userAgent;

    public SignerClient(String userAgent) {
        this.userAgent = userAgent == null ? "" : userAgent;
    }

    public static SignerClient forContext(Context context) {
        String version = "0";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            if (info.versionName != null && info.versionName.length() > 0) version = info.versionName;
        } catch (Exception ignored) {}
        return new SignerClient("AKScrobble/" + version + " (Android " + Build.VERSION.RELEASE + ")");
    }

    @Override public SignResult sign(String method, Map<String, String> params) {
        HttpURLConnection connection = null;
        try {
            byte[] requestBytes = Scrobsign.requestJson(method, params).getBytes("UTF-8");
            connection = (HttpURLConnection) new URL(Scrobsign.SIGN_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setConnectTimeout(Scrobsign.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(Scrobsign.READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            OutputStream output = connection.getOutputStream();
            try { output.write(requestBytes); }
            finally { output.close(); }

            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            SignResult result = SignResult.parse(status, readResponse(input));
            Log.d(Const.TAG, "sign " + method + " -> " + result);
            return result;
        } catch (IOException error) {
            SignResult result = SignResult.transportError(safeMessage(error));
            Log.d(Const.TAG, "sign " + method + " -> " + result);
            return result;
        } catch (IllegalArgumentException error) {
            SignResult result = SignResult.invalidLocally(Collections.<String>emptyList());
            Log.d(Const.TAG, "sign " + method + " -> " + result);
            return result;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readResponse(InputStream input) throws IOException {
        if (input == null) return "";
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int remaining = Scrobsign.MAX_RESPONSE_BYTES;
            while (remaining > 0) {
                int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (count < 0) break;
                output.write(buffer, 0, count);
                remaining -= count;
            }
            return new String(output.toByteArray(), "UTF-8");
        } finally { input.close(); }
    }

    private static String safeMessage(IOException error) {
        String message = error.getMessage();
        return message == null || message.length() == 0
                ? error.getClass().getSimpleName() : message;
    }
}
