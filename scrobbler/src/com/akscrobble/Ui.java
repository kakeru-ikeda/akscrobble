package com.akscrobble;

import android.content.Context;

import com.akscrobble.core.Strings;

import java.util.Locale;

public final class Ui {
    public static Strings.Lang lang(Context context) {
        String language = context == null ? "" : new Prefs(context).getString(Prefs.LANGUAGE);
        if (language.length() == 0) language = Locale.getDefault().getLanguage();
        return Strings.pick(language);
    }

    public static String s(Context context, String key, Object... args) {
        return Strings.get(lang(context), key, args);
    }

    private Ui() {}
}
