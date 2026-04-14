package com.js.salesman.ui.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.js.salesman.R;
import com.js.salesman.session.SessionManager;
import com.js.salesman.ui.activities.auth.LockActivity;
import com.js.salesman.ui.activities.auth.LoginActivity;
import com.js.salesman.utils.PrefsManager;

public class StartScreen extends AppCompatActivity {
    private static final int SPLASH_DELAY = 2500; // 2.5 seconds
    private PrefsManager prefManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startscreen);
        prefManager = new PrefsManager(this);
        new Handler().postDelayed(() -> {
            if (prefManager.isFirstLaunch()) {
                // First-time user → show onboarding
                startActivity(new Intent(StartScreen.this, OnboardingActivity.class));
            } else {
                SessionManager session = new SessionManager(this);
                if (session.isSessionValid()) {
                    long last = session.getLastActivity();
                    long now = System.currentTimeMillis();
                    if (now - last > 3 * 60 * 1000) {
                        startActivity(new Intent(this, LockActivity.class));
                    } else {
                        startActivity(new Intent(this, MainActivity.class));
                    }
                } else {
                    startActivity(new Intent(this, LoginActivity.class));
                }
            }
            finish();
        }, SPLASH_DELAY);
    }
}