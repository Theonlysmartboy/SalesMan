package com.js.salesman.session;

import android.content.Context;
import android.content.SharedPreferences;
import com.js.salesman.utils.AppConstants;
import java.util.Calendar;

public class SessionManager {
    private static final String PREF_NAME = "salesman_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_LAST_ACTIVITY = "last_activity";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;
    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }
    public void createSession(String userId, String username, String role,
                              String fullName, String token, boolean rememberMe) {
        long expiryTime;
        Calendar c = Calendar.getInstance();
        if (rememberMe) {
            // long expiry 12hrs
            c.add(Calendar.HOUR, AppConstants.longSessionDuration);
        } else {
            // 6 hours expiry
            c.add(Calendar.HOUR, AppConstants.shortSessionDuration);
        }
        expiryTime = c.getTimeInMillis();
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_ROLE, role);
        editor.putString(KEY_FULL_NAME, fullName);
        editor.putString(KEY_TOKEN, token);
        editor.putLong(KEY_EXPIRES_AT, expiryTime);
        editor.putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis());
        editor.apply();
    }
    public boolean isSessionValid() {
        long expiry = prefs.getLong(KEY_EXPIRES_AT, 0);
        return System.currentTimeMillis() < expiry;
    }
    public void clearSession() {
        editor.clear().apply();
    }
    // Optionally expose other user info
    public String getFullName() {
        return prefs.getString(KEY_FULL_NAME, null);
    }
    public String getRole() {
        return prefs.getString(KEY_ROLE, null);
    }
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }
    public void updateLastActivity() {
        editor.putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply();
    }
    public long getLastActivity() {
        return prefs.getLong(KEY_LAST_ACTIVITY, 0);
    }
    public boolean isIdleTimeout() {
        long lastActivity = getLastActivity();
        if (lastActivity == 0) return false;
        return (System.currentTimeMillis() - lastActivity) > AppConstants.IDLE_TIMEOUT;
    }
}
