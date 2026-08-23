package com.akscrobble.core;

public final class TrackInfo {
    public final String sourcePkg;
    public final String title;
    public final String artist;
    public final String album;
    public final String albumArtist;
    public final long durationMs;
    public final long mediaId;
    public final int trackNumber;

    public TrackInfo(String sourcePkg, String title, String artist, String album,
            String albumArtist, long durationMs, long mediaId, int trackNumber) {
        this.sourcePkg = sourcePkg;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtist = albumArtist;
        this.durationMs = durationMs;
        this.mediaId = mediaId;
        this.trackNumber = trackNumber;
    }

    public String key() {
        return sourcePkg + "|" + nz(title) + "|" + nz(artist) + "|" + durationMs;
    }

    public boolean isUsable() {
        return !isBlank(title) && !isBlank(artist);
    }

    public TrackInfo withAlbum(String newAlbum, String newAlbumArtist, int newTrackNumber) {
        return new TrackInfo(sourcePkg, title, artist, newAlbum, newAlbumArtist,
                durationMs, mediaId, newTrackNumber);
    }

    public TrackInfo withText(String newTitle, String newArtist, String newAlbum,
            String newAlbumArtist) {
        return new TrackInfo(sourcePkg, newTitle, newArtist, newAlbum, newAlbumArtist,
                durationMs, mediaId, trackNumber);
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof TrackInfo)) return false;
        TrackInfo other = (TrackInfo) value;
        return durationMs == other.durationMs
                && mediaId == other.mediaId
                && trackNumber == other.trackNumber
                && eq(sourcePkg, other.sourcePkg)
                && eq(title, other.title)
                && eq(artist, other.artist)
                && eq(album, other.album)
                && eq(albumArtist, other.albumArtist);
    }

    @Override
    public int hashCode() {
        int result = hash(sourcePkg);
        result = 31 * result + hash(title);
        result = 31 * result + hash(artist);
        result = 31 * result + hash(album);
        result = 31 * result + hash(albumArtist);
        result = 31 * result + (int) (durationMs ^ (durationMs >>> 32));
        result = 31 * result + (int) (mediaId ^ (mediaId >>> 32));
        result = 31 * result + trackNumber;
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static boolean eq(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static int hash(Object value) {
        return value == null ? 0 : value.hashCode();
    }
}
