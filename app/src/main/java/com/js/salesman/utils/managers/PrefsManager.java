package com.js.salesman.utils.managers;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {
    private static final String PREF_NAME = "salesman_app_pref";
    private static final String KEY_FIRST_LAUNCH = "is_first_launch";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public PrefsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    // check if first launch
    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    // set first launch status
    public void setFirstLaunch(boolean isFirst) {
        editor.putBoolean(KEY_FIRST_LAUNCH, isFirst);
        editor.apply();
    }
}
