package com.js.salesman.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.js.salesman.utils.LocationCheckUtil;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.managers.GPSManager;
import com.js.salesman.ui.activities.auth.AuthGateActivity;
import com.js.salesman.ui.activities.auth.LoginActivity;
import com.js.salesman.utils.managers.PrefsManager;

import java.util.concurrent.Executors;

public class StartScreen extends BaseActivity {
    private static final int SPLASH_DELAY = 2500; // 2.5 seconds
    private PrefsManager prefManager;
    Intent intent;
    private Handler splashHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.js.salesman.R.layout.activity_startscreen);
        prefManager = new PrefsManager(this);
        splashHandler = new Handler(Looper.getMainLooper());
        splashHandler.postDelayed(() -> {
            if (prefManager.isFirstLaunch()) {
                intent = new Intent(this, OnboardingActivity.class);
            } else {
                if (session.isUserIdSet()) {
                    intent = new Intent(this, AuthGateActivity.class);
                } else {
                    intent = new Intent(this, LoginActivity.class);
                }
            }
            
            if (!NetworkUtil.isNetworkAvailable(this)) {
                checkCachedProductsAndShowStartDialog();
            } else {
                checkLocationAndProceed();
            }
        }, SPLASH_DELAY);
    }

    private void checkCachedProductsAndShowStartDialog() {
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean hasProducts = productRepository.hasCachedProducts();
            boolean canGoOffline = hasProducts && session.isUserIdSet();
            runOnUiThread(() -> {
                NetworkUtil.showNoInternetDialog(this, true, 
                    canGoOffline ? this::onNavigateToOffline : null, 
                    this::launchTargetActivity);
            });
        });
    }

    public void proceedAfterOfflineSelection() {
        // User wants to go offline from StartScreen.
        // We bypass location checks etc. for offline mode to get them to products as fast as possible.
        launchTargetActivity();
    }

    @Override
    protected boolean shouldCheckNetworkOnResume() {
        return false;
    }

    private void launchTargetActivity() {
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void checkLocationAndProceed() {
        if (LocationCheckUtil.hasLocationPermission(this) &&
                LocationCheckUtil.isLocationEnabled(this)) {
            startTrackingIfNeeded();
            launchTargetActivity();
        } else {
            LocationCheckUtil.showLocationDialog(this,
                    () -> {
                        startTrackingIfNeeded();
                        launchTargetActivity();
                    },
                    this::finish,
                    () -> { }
                );
        }
    }

    private void startTrackingIfNeeded() {
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
