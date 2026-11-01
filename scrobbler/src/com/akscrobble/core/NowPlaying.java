package com.akscrobble.core;

import java.util.Map;

public final class NowPlaying {
    public static String pick(String[] pkgs, Map<String, String> status) {
        if (pkgs == null || status == null) return null;
        String playing = String.valueOf(Const.STATE_PLAYING);
        for (String pkg : pkgs) {
            String key = Sources.statusKey(pkg);
            if (playing.equals(status.get("playback_" + key))) return pkg;
        }
        for (String pkg : pkgs) {
            String title = status.get("np_title_" + Sources.statusKey(pkg));
            if (title != null && !title.isEmpty()) return pkg;
        }
        return null;
    }

    private NowPlaying() {}
}
