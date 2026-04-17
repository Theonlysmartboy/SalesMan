package com.js.salesman.utils.managers;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class SettingsManager {
    private static final String PREF_NAME = "app_settings";
    
    // Security
    public static final String KEY_AUTO_LOCK_TIME = "auto_lock_time"; // in minutes, 0 for OFF
    public static final String KEY_REQUIRE_AUTH_FOR_ORDER = "require_auth_for_order";
    
    // System
    public static final String KEY_API_BASE_URL = "api_base_url";
    
    // Appearance
    public static final String KEY_DARK_MODE = "dark_mode"; // 0: System, 1: Light, 2: Dark

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public void setAutoLockTime(int minutes) {
        editor.putInt(KEY_AUTO_LOCK_TIME, minutes).apply();
    }

    public int getAutoLockTime() {
        return prefs.getInt(KEY_AUTO_LOCK_TIME, 3); // Default 3 mins
    }

    public long getAutoLockTimeMillis() {
        int mins = getAutoLockTime();
        if (mins <= 0) return Long.MAX_VALUE; // Effectively OFF
        return (long) mins * 60 * 1000;
    }

    public void setRequireAuthForOrder(boolean require) {
        editor.putBoolean(KEY_REQUIRE_AUTH_FOR_ORDER, require).apply();
    }

    public boolean isAuthRequiredForOrder() {
        return prefs.getBoolean(KEY_REQUIRE_AUTH_FOR_ORDER, false);
    }

    public void setApiBaseUrl(String url) {
        editor.putString(KEY_API_BASE_URL, url).apply();
    }

    public String getApiBaseUrl(String defaultUrl) {
        return prefs.getString(KEY_API_BASE_URL, defaultUrl);
    }

    public void setDarkMode(int mode) {
        editor.putInt(KEY_DARK_MODE, mode).apply();
        applyDarkMode(mode);
    }

    public int getDarkMode() {
        return prefs.getInt(KEY_DARK_MODE, 0);
    }

    public void applyDarkMode(int mode) {
        switch (mode) {
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
