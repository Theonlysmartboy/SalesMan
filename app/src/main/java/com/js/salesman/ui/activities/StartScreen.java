package com.js.salesman.ui.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.js.salesman.R;
import com.js.salesman.utils.managers.SessionManager;
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
                if (session.isSessionValid()) {
                    if (session.isIdleTimeout(settingsManager.getAutoLockTimeMillis())) {
                        intent = new Intent(this, LockActivity.class);
                    } else {
                        intent = new Intent(this, MainActivity.class);
                    }
                } else {
                    intent = new Intent(this, LoginActivity.class);
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, SPLASH_DELAY);
    }
}