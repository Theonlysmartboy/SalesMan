package com.js.salesman.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.js.salesman.utils.AppRouter;
import com.js.salesman.utils.LocationCheckUtil;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.managers.GPSManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StartScreen extends BaseActivity {

    private static final int SPLASH_DELAY = 2500;
    private Intent intent;
    private Handler splashHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.js.salesman.R.layout.activity_startscreen);
        splashHandler = new Handler(Looper.getMainLooper());
        splashHandler.postDelayed(() -> {
            // Decide destination via AppRouter (single source of truth)
            intent = AppRouter.route(this);
            if (!NetworkUtil.isNetworkAvailable(this)) {
                checkCachedProductsAndShowStartDialog();
            } else {
                checkLocationAndProceed();
            }
        }, SPLASH_DELAY);
    }

    private void checkCachedProductsAndShowStartDialog() {
        executor.execute(() -> {
            boolean hasProducts = productRepository.hasCachedProducts();
            boolean canGoOffline = hasProducts && session.isUserIdSet();
            runOnUiThread(() -> NetworkUtil.showNoInternetDialog(this, true,
                    canGoOffline ? this::onNavigateToOffline : null,
                    this::launchTargetActivity));
        });
    }

    public void proceedAfterOfflineSelection() {
        launchTargetActivity();
    }

    @Override
    protected boolean shouldCheckNetworkOnResume() {
        return false;
    }

    private void launchTargetActivity() {
        if (intent == null) return;
        startActivity(intent);
        finish();               // <-- this is all you need
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
                    () -> { });
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
        if (splashHandler != null) splashHandler.removeCallbacksAndMessages(null);
    }
}