package com.akscrobble;

import android.content.Context;
import android.util.Log;
import com.akscrobble.core.Const;
import com.akscrobble.core.LastfmCall;
import com.akscrobble.core.LastfmRequest;
import com.akscrobble.core.LastfmResponse;
import com.akscrobble.core.LastfmTransport;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.TrackInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** Synchronous Last.fm client. All operations must run through {@link NetThread}. */
public final class LastfmClient {
    private final LastfmCall call;

    public LastfmClient(Context context, String sessionKey, String sessionApiKey) {
        call = new LastfmCall(SignerClient.forContext(context), new HttpTransport(),
                sessionKey, sessionApiKey);
    }

    /** Reuses the auth.getSession signature for a token across calls on this instance. */
    public LastfmResponse getToken() { return call.getToken(); }

    /** Reuses the auth.getSession signature for a token across calls on this instance. */
    public LastfmResponse getSession(String token) { return call.getSession(token); }

    public LastfmResponse updateNowPlaying(TrackInfo track) { return call.updateNowPlaying(track); }

    public LastfmResponse scrobble(List<PendingScrobble> batch) { return call.scrobble(batch); }

    private static final class HttpTransport implements LastfmTransport {
        @Override public LastfmResponse post(String formBody) {
            HttpURLConnection connection = null;
            try {
                byte[] requestBytes = formBody.getBytes("UTF-8");
                Log.d(Const.TAG, "Last.fm POST " + maskedBody(formBody));
                connection = (HttpURLConnection) new URL(Const.API_ROOT).openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setRequestProperty("User-Agent", Const.USER_AGENT);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(true);

                OutputStream output = connection.getOutputStream();
                try { output.write(requestBytes); }
                finally { output.close(); }

                int responseCode = connection.getResponseCode();
                InputStream input = responseCode >= 200 && responseCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                LastfmResponse response = LastfmResponse.parse(readResponse(input));
                logResponse(responseCode, response);
                return response;
            } catch (IOException error) {
                Log.d(Const.TAG, "Last.fm transport error: " + safeMessage(error));
                return LastfmResponse.transportError(safeMessage(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
    }

    private static String readResponse(InputStream input) throws IOException {
        if (input == null) return "";
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int remaining = 64 * 1024;
            while (remaining > 0) {
                int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (count < 0) break;
                output.write(buffer, 0, count);
                remaining -= count;
            }
            return new String(output.toByteArray(), "UTF-8");
        } finally { input.close(); }
    }

    private static String maskedBody(String body) {
        String[] fields = body.split("&", -1);
        StringBuilder masked = new StringBuilder();
        for (String field : fields) {
            if (masked.length() > 0) masked.append('&');
            int equals = field.indexOf('=');
            String key = equals < 0 ? field : field.substring(0, equals);
            if ("api_sig".equals(key) || "sk".equals(key) || "api_key".equals(key)
                    || "token".equals(key)) masked.append(key).append("=***");
            else masked.append(field);
        }
        return masked.toString();
    }

    private static void logResponse(int httpCode, LastfmResponse response) {
        Log.d(Const.TAG, "Last.fm response http=" + httpCode + " status=" + response.status
                + " error=" + response.errorCode + " accepted=" + response.accepted
                + " ignored=" + response.ignored + " ignoredCodes=" + response.ignoredCodes
                + " msg=" + response.errorMessage + " sk=***");
    }

    private static String safeMessage(IOException error) {
        String message = error.getMessage();
        return message == null || message.length() == 0
                ? error.getClass().getSimpleName() : message;
    }
}
