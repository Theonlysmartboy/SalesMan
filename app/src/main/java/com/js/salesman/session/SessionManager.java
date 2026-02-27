package com.js.salesman.session;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Calendar;

public class SessionManager {
    private static final String PREF_NAME = "salesman_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_FULLNAME = "full_name";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;
    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }
    public void createSession(String userId, String username, String role,
                              String fullName, String token, boolean rememberMe) {
        long expiryTime;
        if (rememberMe) {
            // very long expiry (10 years)
            Calendar c = Calendar.getInstance();
            c.add(Calendar.YEAR, 10);
            expiryTime = c.getTimeInMillis();
        } else {
            // 24 hours expiry
            Calendar c = Calendar.getInstance();
            c.add(Calendar.HOUR, 24);
            expiryTime = c.getTimeInMillis();
        }
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_ROLE, role);
        editor.putString(KEY_FULLNAME, fullName);
        editor.putString(KEY_TOKEN, token);
        editor.putLong(KEY_EXPIRES_AT, expiryTime);
        editor.apply();
    }
    public boolean isSessionValid() {
        long expiry = prefs.getLong(KEY_EXPIRES_AT, 0);
        return System.currentTimeMillis() < expiry;
    }
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }
    public void clearSession() {
        editor.clear().apply();
    }
    // Optionally expose other user info
    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }
    public String getFullName() {
        return prefs.getString(KEY_FULLNAME, null);
    }
}
