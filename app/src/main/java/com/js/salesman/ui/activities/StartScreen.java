package com.js.salesman.ui.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.js.salesman.R;
import com.js.salesman.utils.LocationCheckUtil;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.managers.GPSManager;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.ui.activities.auth.AuthGateActivity;
import com.js.salesman.ui.activities.auth.LoginActivity;
import com.js.salesman.utils.managers.PrefsManager;

public class StartScreen extends AppCompatActivity {
    private static final int SPLASH_DELAY = 2500; // 2.5 seconds
    private PrefsManager prefManager;
    Intent intent;
    private Handler splashHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startscreen);
        prefManager = new PrefsManager(this);
        splashHandler = new Handler(Looper.getMainLooper());
        splashHandler.postDelayed(() -> {
            if (prefManager.isFirstLaunch()) {
                // First-time user → show onboarding
                intent = new Intent(this, OnboardingActivity.class);
            } else {
                SessionManager session = new SessionManager(this);
                if (session.isUserIdSet()) {
                    // User is "logged in" by presence of ID. Route to AuthGate to handle security.
                    intent = new Intent(this, AuthGateActivity.class);
                } else {
                    // No user ID -> show login
                    intent = new Intent(this, LoginActivity.class);
                }
            }
            // --- CHECK NETWORK BEFORE LAUNCHING ---
            if (!NetworkUtil.isNetworkAvailable(this)) {
                // Show no-internet dialog – it will auto-dismiss when network returns
                // This callback runs when the dialog is dismissed (network restored)
                NetworkUtil.showNoInternetDialog(this, true, this::launchTargetActivity);
            } else {
                // Network is available – launch immediately only if location is turned on and permissions granted
                checkLocationAndProceed();
            }
        }, SPLASH_DELAY);
    }

    private void launchTargetActivity() {
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void checkLocationAndProceed() {
        // If user is logged in, we need location to start tracking.
        // If not logged in, we can skip.
        // But we'll enforce location only if user is logged in or will use it.
        // For simplicity, we always check location.
        if (LocationCheckUtil.hasLocationPermission(this) && LocationCheckUtil.isLocationEnabled(this)) {
            // Location is ready
            startTrackingIfNeeded();
            launchTargetActivity();
        } else {
            // Show dialog
            // On cancel / exit
            LocationCheckUtil.showLocationDialog(this,
                    () -> {
                        // On success (user fixed it)
                        startTrackingIfNeeded();
                        launchTargetActivity();
                    },
                    this::finish,
                    () -> { } //Do nothing
                );
        }
    }

    private void startTrackingIfNeeded() {
        // Only start tracking if the user is already logged in
        SessionManager session = new SessionManager(this);
        if (session.isUserIdSet()) {
            GPSManager.startTracking(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (splashHandler != null) {
            splashHandler.removeCallbacksAndMessages(null);
        }
    }
}