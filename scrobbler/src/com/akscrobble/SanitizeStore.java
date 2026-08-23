package com.akscrobble;

import android.content.Context;

import com.akscrobble.core.SanitizeConfig;
import com.akscrobble.core.SanitizeRule;
import com.akscrobble.core.Sanitizer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class SanitizeStore {
    public static SanitizeConfig load(Context context) {
        Prefs prefs = new Prefs(context);
        String serialized = prefs.getString(Prefs.SANITIZE_CONFIG);
        if (!serialized.isEmpty()) {
            return SanitizeConfig.parse(serialized);
        }

        Set<String> disabled = new HashSet<String>();
        String configured = prefs.getString(Prefs.DISABLED_RULES);
        if (configured.isEmpty()) {
            for (SanitizeRule rule : Sanitizer.DEFAULT_RULES) {
                if (!rule.defaultOn) disabled.add(rule.id);
            }
        } else {
            for (String id : configured.split(",")) {
                if (!id.trim().isEmpty()) disabled.add(id.trim());
            }
        }
        return new SanitizeConfig(prefs.getBoolean(Prefs.SANITIZE_ENABLED), disabled,
                Collections.<SanitizeRule>emptyList());
    }

    public static void save(Context context, SanitizeConfig config) {
        new Prefs(context).putString(Prefs.SANITIZE_CONFIG, config.serialize());
    }

    public static Sanitizer sanitizer(Context context) {
        return Sanitizer.from(load(context));
    }

    private SanitizeStore() {}
}
