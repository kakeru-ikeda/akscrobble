package com.akscrobble.core;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class LastfmRequest {
    public static TreeMap<String, String> nowPlaying(TrackInfo track, String apiKey, String sk) {
        TreeMap<String, String> params = base("track.updateNowPlaying", apiKey);
        params.put("sk", sk);
        params.put("artist", track.artist);
        params.put("track", track.title);
        putOptional(params, "album", track.album);
        putOptional(params, "albumArtist", track.albumArtist);
        if (track.durationMs > 0L) params.put("duration", String.valueOf(track.durationMs / 1000L));
        if (track.trackNumber > 0) params.put("trackNumber", String.valueOf(track.trackNumber));
        return params;
    }

    public static TreeMap<String, String> scrobbleBatch(List<PendingScrobble> items,
            String apiKey, String sk) {
        TreeMap<String, String> params = base("track.scrobble", apiKey);
        params.put("sk", sk);
        for (int i = 0; i < items.size(); i++) {
            PendingScrobble item = items.get(i);
            String suffix = "[" + i + "]";
            params.put("artist" + suffix, item.artist);
            params.put("track" + suffix, item.track);
            params.put("timestamp" + suffix, String.valueOf(item.startedAtSec));
            putOptional(params, "album" + suffix, item.album);
            putOptional(params, "albumArtist" + suffix, item.albumArtist);
            if (item.durationMs > 0L) {
                params.put("duration" + suffix, String.valueOf(item.durationMs / 1000L));
            }
            if (item.trackNumber > 0) {
                params.put("trackNumber" + suffix, String.valueOf(item.trackNumber));
            }
        }
        return params;
    }

    public static TreeMap<String, String> getToken(String apiKey) {
        return base("auth.getToken", apiKey);
    }

    public static TreeMap<String, String> getSession(String apiKey, String token) {
        TreeMap<String, String> params = base("auth.getSession", apiKey);
        params.put("token", token);
        return params;
    }

    public static String signedBody(Map<String, String> params, String secret) {
        TreeMap<String, String> signed = new TreeMap<String, String>(params);
        signed.put("api_sig", LastfmSig.sign(signed, secret));
        StringBuilder body = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : signed.entrySet()) {
                if (body.length() > 0) body.append('&');
                body.append(entry.getKey()).append('=')
                        .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is unavailable", e);
        }
        return body.toString();
    }

    private static TreeMap<String, String> base(String method, String apiKey) {
        TreeMap<String, String> params = new TreeMap<String, String>();
        params.put("method", method);
        params.put("api_key", apiKey);
        return params;
    }

    private static void putOptional(Map<String, String> params, String key, String value) {
        // 既にキューへ入っている行が "<unknown>" を抱えている場合があるので、
        // 発生源(AlbumResolver)だけでなく送信時にも弾く。
        String normalized = AlbumInfo.normalize(value);
        if (normalized != null) params.put(key, normalized);
    }

    private LastfmRequest() {}
}
