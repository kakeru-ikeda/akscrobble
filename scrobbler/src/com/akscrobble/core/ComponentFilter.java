package com.akscrobble.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComponentFilter {
    public static final class Split {
        public final List<String> primary;
        public final List<String> secondary;

        Split(List<String> primary, List<String> secondary) {
            this.primary = Collections.unmodifiableList(primary);
            this.secondary = Collections.unmodifiableList(secondary);
        }
    }

    public static Split classify(List<String> activityNames) {
        List<String> names = new ArrayList<String>();
        if (activityNames != null) {
            for (String name : activityNames) {
                if (name != null && !name.trim().isEmpty()) {
                    names.add(name);
                }
            }
        }

        String preferred = contains(names, "DebugSetting") ? "DebugSetting" : "UserDebug";
        List<String> primary = new ArrayList<String>();
        List<String> secondary = new ArrayList<String>();
        for (String name : names) {
            if (name.contains(preferred)) {
                primary.add(name);
            } else {
                secondary.add(name);
            }
        }
        return new Split(primary, secondary);
    }

    private static boolean contains(List<String> names, String text) {
        for (String name : names) {
            if (name.contains(text)) {
                return true;
            }
        }
        return false;
    }

    private ComponentFilter() {}
}
