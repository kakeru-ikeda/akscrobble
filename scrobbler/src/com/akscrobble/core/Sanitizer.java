package com.akscrobble.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Sanitizer {
    public static final SanitizeRule[] DEFAULT_RULES = {
        rule("strip-zero-width", SanitizeRule.Scope.ALL, "[\u200B-\u200D\uFEFF]", "", true,
                "Remove zero-width characters"),
        rule("fullwidth-space", SanitizeRule.Scope.ALL, "\u3000", " ", false,
                "Replace fullwidth spaces"),
        rule("album-ep", SanitizeRule.Scope.ALBUM, "\\s+-\\s+EP$", "", true,
                "Remove the EP suffix"),
        rule("album-single", SanitizeRule.Scope.ALBUM, "\\s+-\\s+Single$", "", true,
                "Remove the Single suffix"),
        rule("album-deluxe", SanitizeRule.Scope.ALBUM,
                "\\s+\\((Deluxe|Deluxe Edition|Deluxe Version)\\)$", "", false,
                "Remove deluxe edition suffixes"),
        rule("album-remaster", SanitizeRule.Scope.ALBUM,
                "\\s+\\((\\d{4} )?Remaster(ed)?( Version)?\\)$", "", false,
                "Remove remaster suffixes"),
        rule("album-bonus", SanitizeRule.Scope.ALBUM,
                "\\s+\\(Bonus Track Version\\)$", "", false,
                "Remove the bonus track version suffix"),
        rule("collapse-spaces", SanitizeRule.Scope.ALL, "[ \\t]{2,}", " ", true,
                "Collapse repeated spaces"),
        rule("trim-ends", SanitizeRule.Scope.ALL, "^\\s+|\\s+$", "", true,
                "Trim leading and trailing whitespace")
    };

    private final SanitizeRule[] rules;
    private final Set<String> disabledIds;
    private final boolean enabled;

    public Sanitizer(SanitizeRule[] rules, Set<String> disabledIds, boolean enabled) {
        this.rules = rules.clone();
        this.disabledIds = new HashSet<String>(disabledIds);
        this.enabled = enabled;
    }

    public static Sanitizer defaults() {
        Set<String> disabled = new HashSet<String>();
        for (SanitizeRule rule : DEFAULT_RULES) {
            if (!rule.defaultOn) {
                disabled.add(rule.id);
            }
        }
        return new Sanitizer(DEFAULT_RULES, disabled, true);
    }

    public static Sanitizer from(SanitizeConfig config) {
        SanitizeRule[] rules = new SanitizeRule[
                DEFAULT_RULES.length + config.customRules.size()];
        System.arraycopy(DEFAULT_RULES, 0, rules, 0, DEFAULT_RULES.length);
        for (int i = 0; i < config.customRules.size(); i++) {
            rules[DEFAULT_RULES.length + i] = config.customRules.get(i);
        }
        return new Sanitizer(rules, config.disabledIds, config.enabled);
    }

    public String apply(SanitizeRule.Scope field, String raw) {
        if (raw == null || !enabled) {
            return raw;
        }
        String value = raw;
        for (SanitizeRule rule : rules) {
            value = applyRule(rule, field, value);
        }
        return value;
    }

    public TrackInfo apply(TrackInfo raw) {
        if (raw == null) return null;
        return raw.withText(
                apply(SanitizeRule.Scope.TRACK, raw.title),
                apply(SanitizeRule.Scope.ARTIST, raw.artist),
                apply(SanitizeRule.Scope.ALBUM, raw.album),
                apply(SanitizeRule.Scope.ALBUM_ARTIST, raw.albumArtist));
    }

    public List<String> explain(SanitizeRule.Scope field, String raw) {
        List<String> changes = new ArrayList<String>();
        if (raw == null || !enabled) {
            return changes;
        }
        String value = raw;
        for (SanitizeRule rule : rules) {
            String before = value;
            value = applyRule(rule, field, value);
            if (!before.equals(value)) {
                changes.add(rule.id + ": '" + before + "' -> '" + value + "'");
            }
        }
        return changes;
    }

    private String applyRule(SanitizeRule rule, SanitizeRule.Scope field, String value) {
        if (disabledIds.contains(rule.id)
                || (rule.scope != SanitizeRule.Scope.ALL && rule.scope != field)) {
            return value;
        }
        String changed = rule.pattern.matcher(value).replaceAll(rule.replacement);
        return changed.trim().isEmpty() ? value : changed;
    }

    private static SanitizeRule rule(String id, SanitizeRule.Scope scope, String regex,
            String replacement, boolean defaultOn, String note) {
        return new SanitizeRule(id, scope, regex, replacement, defaultOn, note);
    }
}
