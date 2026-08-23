package com.akscrobble.core;

import java.util.regex.Pattern;

public final class SanitizeRule {
    public enum Scope { ALL, TRACK, ARTIST, ALBUM, ALBUM_ARTIST }

    public final String id;
    public final Scope scope;
    public final Pattern pattern;
    public final String replacement;
    public final boolean defaultOn;
    public final String note;

    public SanitizeRule(String id, Scope scope, String regex, String replacement,
            boolean defaultOn, String note) {
        this.id = id;
        this.scope = scope;
        this.pattern = Pattern.compile(regex);
        this.replacement = replacement;
        this.defaultOn = defaultOn;
        this.note = note;
    }

    @Override
    public String toString() {
        return id + " " + scope + " /" + pattern.pattern() + "/";
    }
}
