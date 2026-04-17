package com.js.salesman.ui.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.js.salesman.R;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.ui.activities.auth.AuthGateActivity;
import com.js.salesman.ui.activities.auth.LockActivity;
import com.js.salesman.ui.activities.auth.LoginActivity;
import com.js.salesman.utils.managers.PrefsManager;
import com.js.salesman.utils.managers.SettingsManager;

public class StartScreen extends AppCompatActivity {
    private static final int SPLASH_DELAY = 2500; // 2.5 seconds
    private PrefsManager prefManager;
    private SettingsManager settingsManager;
    Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startscreen);
        prefManager = new PrefsManager(this);
        settingsManager = new SettingsManager(this);
        new Handler().postDelayed(() -> {
            if (prefManager.isFirstLaunch()) {
                // First-time user → show onboarding
                intent = new Intent(this, OnboardingActivity.class);
            } else {
                SessionManager session = new SessionManager(this);
                if (session.isUserIdSet()) { // User is "logged in" by presence of ID, even if token expired for API
                    if (session.isSessionValid()) {
                        // Token valid, but might be idle
                        if (session.isIdleTimeout(settingsManager.getAutoLockTimeMillis())) {
                            intent = new Intent(this, LockActivity.class);
                        } else {
                            // Valid and not idle, but for launch we always want AuthGate per requirements 
                            // to ensure Biometric/PIN at start
                            intent = new Intent(this, AuthGateActivity.class);
                        }
                    } else {
                        // Token expired, but user is known. Route to AuthGate (which routes to Login if PIN missing, but usually just Auth)
                        // Per requirement: "Email/password login should NOT be shown again unless user explicitly logs out"
                        intent = new Intent(this, AuthGateActivity.class);
                    }
                } else {
                    // No user ID -> show login
                    intent = new Intent(this, LoginActivity.class);
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, SPLASH_DELAY);
    }
}