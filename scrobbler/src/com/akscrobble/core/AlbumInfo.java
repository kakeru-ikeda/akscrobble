package com.akscrobble.core;

public final class AlbumInfo {
    public final String album;
    public final String albumArtist;
    public final int trackNumber;
    public final int disc;
    public final int year;

    public AlbumInfo(String album, String albumArtist, int trackNumber, int disc, int year) {
        this.album = album;
        this.albumArtist = albumArtist;
        this.trackNumber = trackNumber;
        this.disc = disc;
        this.year = year;
    }

    /**
     * MediaStore はタグ欠損時にリテラル "&lt;unknown&gt;" を入れる
     * (MediaStore.UNKNOWN_STRING)。これをそのまま Last.fm へ送ると
     * ignoredMessage code=1 (Artist was ignored) で scrobble ごと捨てられる。
     * 実機で albumArtist=&lt;unknown&gt; の曲が 1 件 dead 化した。
     * 空文字と併せて「値なし」に倒す。
     */
    public static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.length() == 0 || "<unknown>".equalsIgnoreCase(trimmed)) return null;
        return trimmed;
    }

    public static int trackFromMediaStore(int raw) {
        return raw <= 0 ? 0 : raw % 1000;
    }

    public static int discFromMediaStore(int raw) {
        return raw <= 0 ? 0 : raw / 1000;
    }

    private AlbumInfo() {
        this(null, null, 0, 0, 0);
    }
}
