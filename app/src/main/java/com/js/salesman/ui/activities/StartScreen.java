package com.js.salesman.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.js.salesman.R;
import com.js.salesman.utils.AppRouter;
import com.js.salesman.utils.LocationCheckUtil;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.managers.GPSManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StartScreen extends BaseActivity {

    private static final int SPLASH_DELAY = 2500;
    private Intent intent;
    private boolean launched = false;
    private Handler splashHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startscreen);
        splashHandler = new Handler(Looper.getMainLooper());
        splashHandler.postDelayed(this::resolveDestination, SPLASH_DELAY);
    }

    private void resolveDestination() {
        if (isFinishing() || isDestroyed()) return;
        AppRouter.route(this, resolved -> {
            if (isFinishing() || isDestroyed()) return;
            intent = resolved;
            if (!NetworkUtil.isNetworkAvailable(this)) {
                checkCachedProductsAndShowStartDialog();
            } else {
                checkLocationAndProceed();
            }
        });
    }

    private void checkCachedProductsAndShowStartDialog() {
        executor.execute(() -> {
            boolean hasProducts = false;
            try {
                hasProducts = productRepository.hasCachedProducts();
            } catch (Exception ignored) {
            }
            final boolean canGoOffline = hasProducts && session.isUserIdSet();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                NetworkUtil.showNoInternetDialog(this, true,
                        canGoOffline ? this::onNavigateToOffline : null,
                        this::launchTargetActivity);
            });
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
        if (launched || intent == null || isFinishing() || isDestroyed()) return;
        launched = true;
        startActivity(intent);
        finish();
    }

    private void checkLocationAndProceed() {
        if (LocationCheckUtil.hasLocationPermission(this)
                && LocationCheckUtil.isLocationEnabled(this)) {
            startTrackingIfNeeded();
            launchTargetActivity();
        } else {
            LocationCheckUtil.showLocationDialog(this,
                    () -> {
                        startTrackingIfNeeded();
                        launchTargetActivity();
                    }, this::finish, () -> { });
        }
    }

    private void startTrackingIfNeeded() {
        if (session.isUserIdSet()) {
            GPSManager.startTracking(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 || requestCode == 1002) {
            LocationCheckUtil.dismissDialog();
            startTrackingIfNeeded();
            launchTargetActivity();
        }
    }

    @Override
    protected void onDestroy() {
        if (splashHandler != null) splashHandler.removeCallbacksAndMessages(null);
        executor.shutdown();
        super.onDestroy();
    }
}