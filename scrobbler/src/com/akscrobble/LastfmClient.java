package com.akscrobble;

import android.util.Log;
import com.akscrobble.core.Const;
import com.akscrobble.core.LastfmRequest;
import com.akscrobble.core.LastfmResponse;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.TrackInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Synchronous Last.fm transport. Every public operation performs blocking I/O and must be
 * invoked from a runnable submitted through {@link NetThread}, never from the main thread.
 */
public final class LastfmClient {
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final String apiKey;
    private final String apiSecret;
    private final String sessionKey;

    public LastfmClient(String apiKey, String apiSecret, String sessionKey) {
        this.apiKey = nonNull(apiKey);
        this.apiSecret = nonNull(apiSecret);
        this.sessionKey = nonNull(sessionKey);
    }

    /** Must be called on {@link NetThread}; performs a synchronous HTTP request. */
    public LastfmResponse post(Map<String, String> params) {
        HttpURLConnection connection = null;
        try {
            String body = LastfmRequest.signedBody(params, apiSecret);
            byte[] requestBytes = body.getBytes("UTF-8");
            Log.d(Const.TAG, "Last.fm POST " + maskedBody(body));

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
            try {
                output.write(requestBytes);
            } finally {
                output.close();
            }

            int responseCode = connection.getResponseCode();
            InputStream input = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String xml = readResponse(input);
            LastfmResponse response = LastfmResponse.parse(xml);
            logResponse(responseCode, response);
            return response;
        } catch (IOException e) {
            Log.d(Const.TAG, "Last.fm transport error: " + safeMessage(e));
            return LastfmResponse.transportError(safeMessage(e));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Must be called on {@link NetThread}. */
    public LastfmResponse getToken() {
        return post(LastfmRequest.getToken(apiKey));
    }

    /** Must be called on {@link NetThread}. */
    public LastfmResponse getSession(String token) {
        return post(LastfmRequest.getSession(apiKey, token));
    }

    /** Must be called on {@link NetThread}. */
    public LastfmResponse updateNowPlaying(TrackInfo track) {
        if (sessionKey.length() == 0) return missingSession();
        return post(LastfmRequest.nowPlaying(track, apiKey, sessionKey));
    }

    /** Must be called on {@link NetThread}. */
    public LastfmResponse scrobble(List<PendingScrobble> batch) {
        if (sessionKey.length() == 0) return missingSession();
        return post(LastfmRequest.scrobbleBatch(batch, apiKey, sessionKey));
    }

    private static String readResponse(InputStream input) throws IOException {
        if (input == null) return "";
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int remaining = MAX_RESPONSE_BYTES;
            while (remaining > 0) {
                int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (count < 0) break;
                output.write(buffer, 0, count);
                remaining -= count;
            }
            return new String(output.toByteArray(), "UTF-8");
        } finally {
            input.close();
        }
    }

    private static LastfmResponse missingSession() {
        Log.d(Const.TAG, "Last.fm request skipped: sk=*** (missing)");
        return LastfmResponse.parse(
                "<lfm status=\"failed\"><error code=\"9\">Missing session key</error></lfm>");
    }

    private static String maskedBody(String body) {
        String[] fields = body.split("&", -1);
        StringBuilder masked = new StringBuilder();
        for (String field : fields) {
            if (masked.length() > 0) masked.append('&');
            int equals = field.indexOf('=');
            String key = equals < 0 ? field : field.substring(0, equals);
            if ("api_sig".equals(key) || "sk".equals(key) || "api_secret".equals(key)
                    || "api_key".equals(key) || "token".equals(key)) {
                masked.append(key).append("=***");
            } else {
                masked.append(field);
            }
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

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
