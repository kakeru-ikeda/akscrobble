package com.akscrobble.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SanitizeConfig {
    public static final char SEP = '\u001F';
    private static final char ESC = '\\';

    public final boolean enabled;
    public final Set<String> disabledIds;
    public final List<SanitizeRule> customRules;

    public SanitizeConfig(boolean enabled, Set<String> disabledIds,
            List<SanitizeRule> customRules) {
        this.enabled = enabled;
        this.disabledIds = Collections.unmodifiableSet(new HashSet<String>(disabledIds));
        this.customRules = Collections.unmodifiableList(
                new ArrayList<SanitizeRule>(customRules));
    }

    public static SanitizeConfig defaults() {
        Set<String> disabled = new HashSet<String>();
        for (SanitizeRule rule : Sanitizer.DEFAULT_RULES) {
            if (!rule.defaultOn) {
                disabled.add(rule.id);
            }
        }
        return new SanitizeConfig(true, disabled, Collections.<SanitizeRule>emptyList());
    }

    public String serialize() {
        StringBuilder text = new StringBuilder();
        text.append("enabled").append(SEP).append(enabled ? '1' : '0').append('\n');
        text.append("disabled");
        List<String> ids = new ArrayList<String>(disabledIds);
        Collections.sort(ids);
        for (String id : ids) {
            text.append(SEP);
            escape(text, id);
        }
        for (SanitizeRule rule : customRules) {
            text.append('\n').append("rule").append(SEP);
            escape(text, rule.id);
            text.append(SEP).append(rule.scope.name()).append(SEP);
            escape(text, rule.pattern.pattern());
            text.append(SEP);
            escape(text, rule.replacement);
        }
        return text.toString();
    }

    public static SanitizeConfig parse(String text) {
        if (text == null || text.isEmpty()) {
            return defaults();
        }

        boolean enabled = true;
        Set<String> disabled = new HashSet<String>();
        List<SanitizeRule> custom = new ArrayList<SanitizeRule>();
        Set<String> usedIds = new HashSet<String>();
        for (SanitizeRule rule : Sanitizer.DEFAULT_RULES) {
            usedIds.add(rule.id);
        }

        String separator = String.valueOf(SEP);
        String[] lines = text.split("\\n", -1);
        for (String line : lines) {
            String[] fields = line.split(separator, -1);
            if (fields.length == 2 && "enabled".equals(fields[0])) {
                if ("1".equals(fields[1])) {
                    enabled = true;
                } else if ("0".equals(fields[1])) {
                    enabled = false;
                }
            } else if (fields.length >= 1 && "disabled".equals(fields[0])) {
                for (int i = 1; i < fields.length; i++) {
                    if (!fields[i].isEmpty()) {
                        disabled.add(unescape(fields[i]));
                    }
                }
            } else if (fields.length == 5 && "rule".equals(fields[0])) {
                String id = unescape(fields[1]);
                if (id.isEmpty() || usedIds.contains(id)) {
                    continue;
                }
                try {
                    SanitizeRule.Scope scope = SanitizeRule.Scope.valueOf(fields[2]);
                    SanitizeRule rule = new SanitizeRule(
                            id, scope, unescape(fields[3]), unescape(fields[4]), true, "");
                    custom.add(rule);
                    usedIds.add(id);
                } catch (IllegalArgumentException e) {
                    // Ignore unknown scopes and invalid regular expressions.
                }
            }
        }
        return new SanitizeConfig(enabled, disabled, custom);
    }

    /**
     * 区切り文字と行区切りを本文から追い出す。
     *
     * これが無いと、置換文字列に改行が入ったルールが黙って切り詰められ、
     * 正規表現に区切り文字が入ったルールは別のルールに化けて復元される
     * （どちらも実測済み）。正規表現はバックスラッシュを多用するので
     * エスケープ文字自身の二重化が要る。
     */
    private static void escape(StringBuilder out, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ESC) out.append(ESC).append(ESC);
            else if (c == '\n') out.append(ESC).append('n');
            else if (c == '\r') out.append(ESC).append('r');
            else if (c == SEP) out.append(ESC).append('s');
            else out.append(c);
        }
    }

    private static String unescape(String value) {
        if (value.indexOf(ESC) < 0) return value;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != ESC || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            if (next == ESC) out.append(ESC);
            else if (next == 'n') out.append('\n');
            else if (next == 'r') out.append('\r');
            else if (next == 's') out.append(SEP);
            else out.append(ESC).append(next);
        }
        return out.toString();
    }

    public SanitizeConfig withEnabled(boolean value) {
        return new SanitizeConfig(value, disabledIds, customRules);
    }

    public SanitizeConfig withDisabled(Set<String> ids) {
        return new SanitizeConfig(enabled, ids, customRules);
    }

    public SanitizeConfig withCustomRules(List<SanitizeRule> rules) {
        return new SanitizeConfig(enabled, disabledIds, rules);
    }
}
