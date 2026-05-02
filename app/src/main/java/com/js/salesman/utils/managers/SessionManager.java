package com.js.salesman.utils.managers;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.js.salesman.models.Customer;
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
    private static final String KEY_IS_LOCKED = "is_locked";
    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LNG = "last_lng";
    private static final String KEY_SELECTED_CUSTOMER = "selected_customer";

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
    public boolean isUserIdSet() {
        return getUserId() != null;
    }
    public void clearSession() {
        String userId = getUserId();
        String username = getUsername();
        String fullName = getFullName();
        String role = getRole();
        
        editor.clear();
        
        // Preserve user identification but clear security tokens and session state
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_FULL_NAME, fullName);
        editor.putString(KEY_ROLE, role);
        editor.apply();
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
    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }
    public void updateLastActivity() {
        editor.putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply();
    }
    public long getLastActivity() {
        return prefs.getLong(KEY_LAST_ACTIVITY, 0);
    }

    public boolean isIdleTimeout(long timeoutMillis) {
        if (prefs.getBoolean(KEY_IS_LOCKED, false)) return true;
        long lastActivity = getLastActivity();
        if (lastActivity == 0) return false;
        boolean timedOut = (System.currentTimeMillis() - lastActivity) > timeoutMillis;
        if (timedOut) {
            setLocked(true);
        }
        return timedOut;
    }
    public void setLocked(boolean locked) {
        editor.putBoolean(KEY_IS_LOCKED, locked).apply();
    }

    public void saveLastLocation(double lat, double lng) {
        editor.putString(KEY_LAST_LAT, String.valueOf(lat));
        editor.putString(KEY_LAST_LNG, String.valueOf(lng));
        editor.apply();
    }

    public Double getCachedLat() {
        String lat = prefs.getString(KEY_LAST_LAT, null);
        return lat != null ? Double.parseDouble(lat) : null;
    }

    public Double getCachedLng() {
        String lng = prefs.getString(KEY_LAST_LNG, null);
        return lng != null ? Double.parseDouble(lng) : null;
    }

    public void extendSessionOffline() {
        // This is used for "Fast Access" to bypass the isSessionValid check 
        // until the app can sync with the server.
        Calendar c = Calendar.getInstance();
        c.add(Calendar.HOUR, AppConstants.shortSessionDuration);
        editor.putLong(KEY_EXPIRES_AT, c.getTimeInMillis());
        editor.apply();
    }

    public void setSelectedCustomer(Customer customer) {
        if (customer == null) {
            editor.remove(KEY_SELECTED_CUSTOMER).apply();
        } else {
            String json = new Gson().toJson(customer);
            editor.putString(KEY_SELECTED_CUSTOMER, json).apply();
        }
    }

    public Customer getSelectedCustomer() {
        String json = prefs.getString(KEY_SELECTED_CUSTOMER, null);
        if (json == null) return null;
        return new Gson().fromJson(json, Customer.class);
    }
}
