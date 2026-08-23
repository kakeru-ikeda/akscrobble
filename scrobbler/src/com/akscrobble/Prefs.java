package com.akscrobble;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    public static final String SESSION_KEY = "session_key";
    public static final String USER_NAME = "user_name";
    public static final String API_KEY = "api_key";
    public static final String API_SECRET = "api_secret";
    public static final String SANITIZE_CONFIG = "sanitize_config";
    public static final String SANITIZE_ENABLED = "sanitize_enabled";
    public static final String DISABLED_RULES = "disabled_rules";
    public static final String AUTH_INVALID = "auth_invalid";
    /** AUTH_INVALID を立てた時点の認証情報の指紋。異なれば再挑戦してよい。 */
    public static final String AUTH_INVALID_FOR = "auth_invalid_for";
    public static final String SERVICE_ENABLED = "service_enabled";
    public static final String LANGUAGE = "language";

    private final SharedPreferences preferences;

    public Prefs(Context context) {
        preferences = context.getSharedPreferences("aks", Context.MODE_PRIVATE);
    }

    public String getString(String key) {
        return preferences.getString(key, "");
    }

    public void putString(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    public boolean getBoolean(String key) {
        return preferences.getBoolean(key, SANITIZE_ENABLED.equals(key));
    }

    public void putBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    private Prefs() {
        throw new AssertionError();
    }
}
