package com.akscrobble.core;

public final class PendingScrobble {
    public final long id;
    public final String sourcePkg;
    public final String artist;
    public final String track;
    public final String album;
    public final String albumArtist;
    public final long durationMs;
    public final int trackNumber;
    public final long startedAtSec;
    public final int attempts;
    public final String state;
    public final String lastError;

    public PendingScrobble(long id, String sourcePkg, String artist, String track,
            String album, String albumArtist, long durationMs, int trackNumber,
            long startedAtSec, int attempts, String state, String lastError) {
        this.id = id;
        this.sourcePkg = sourcePkg;
        this.artist = artist;
        this.track = track;
        this.album = album;
        this.albumArtist = albumArtist;
        this.durationMs = durationMs;
        this.trackNumber = trackNumber;
        this.startedAtSec = startedAtSec;
        this.attempts = attempts;
        this.state = state;
        this.lastError = lastError;
    }

    public String dedupKey() {
        return sourcePkg + "|" + nz(artist) + "|" + nz(track) + "|"
                + durationMs + "|" + startedAtSec;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
