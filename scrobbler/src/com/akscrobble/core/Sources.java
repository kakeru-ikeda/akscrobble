package com.akscrobble.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Sources {
    public static final String PREF_PREFIX = "source_enabled_";

    public static String prefKey(String pkg) {
        return PREF_PREFIX + pkg;
    }

    public static String statusKey(String pkg) {
        if (Const.PKG_APPLE.equals(pkg)) return "apple";
        if (Const.PKG_AKMUSIC.equals(pkg)) return "akmusic";
        if (Const.PKG_QOBUZ.equals(pkg)) return "qobuz";
        return pkg;
    }

    public static String[] displayOrder() {
        return new String[] {Const.PKG_AKMUSIC, Const.PKG_APPLE, Const.PKG_QOBUZ};
    }

    public static String label(String pkg) {
        if (Const.PKG_AKMUSIC.equals(pkg)) return "AK Music";
        if (Const.PKG_APPLE.equals(pkg)) return "Apple Music";
        if (Const.PKG_QOBUZ.equals(pkg)) return "Qobuz";
        return pkg;
    }

    public static String tag(String pkg) {
        if (Const.PKG_AKMUSIC.equals(pkg)) return "AK";
        if (Const.PKG_APPLE.equals(pkg)) return "Ap";
        if (Const.PKG_QOBUZ.equals(pkg)) return "Qo";
        return "";
    }

    public static int tagColor(String pkg) {
        if (Const.PKG_AKMUSIC.equals(pkg)) return 0xFF2F6FB3;
        if (Const.PKG_APPLE.equals(pkg)) return 0xFFC23A58;
        if (Const.PKG_QOBUZ.equals(pkg)) return 0xFF3C3C44;
        return 0xFF4A4A4F;
    }

    public static String serviceOf(String pkg) {
        for (String[] target : Const.TARGETS) {
            if (target[0].equals(pkg)) return target[1];
        }
        return null;
    }

    public static final class Plan {
        public final List<String> start;
        public final List<String> stop;

        private Plan(List<String> start, List<String> stop) {
            this.start = Collections.unmodifiableList(start);
            this.stop = Collections.unmodifiableList(stop);
        }
    }

    public static Plan reconcile(Collection<String> active, Map<String, Boolean> enabled) {
        Collection<String> current = active == null
                ? Collections.<String>emptyList() : active;
        List<String> start = new ArrayList<String>();
        List<String> stop = new ArrayList<String>();
        Set<String> targets = new HashSet<String>();
        for (String[] target : Const.TARGETS) {
            String pkg = target[0];
            targets.add(pkg);
            Boolean configured = enabled == null ? null : enabled.get(pkg);
            boolean isEnabled = configured == null || configured.booleanValue();
            if (isEnabled && !current.contains(pkg)) {
                start.add(pkg);
            } else if (!isEnabled && current.contains(pkg)) {
                stop.add(pkg);
            }
        }
        for (String pkg : current) {
            if (!targets.contains(pkg)) stop.add(pkg);
        }
        return new Plan(start, stop);
    }

    private Sources() {}
}
