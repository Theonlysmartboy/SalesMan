package com.js.salesman.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.WorkManager;

import com.js.salesman.repository.ProductRepository;
import com.js.salesman.utils.LocationCheckUtil;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.ui.activities.auth.AuthGateActivity;
import com.js.salesman.ui.activities.auth.ForgotPasswordActivity;
import com.js.salesman.ui.activities.auth.LockActivity;
import com.js.salesman.ui.activities.auth.LoginActivity;
import com.js.salesman.ui.activities.auth.ResetPasswordActivity;
import com.js.salesman.utils.managers.GPSManager;
import com.js.salesman.utils.managers.SettingsManager;

import java.util.concurrent.Executors;

public abstract class BaseActivity extends AppCompatActivity {
    protected SessionManager session;
    protected SettingsManager settingsManager;
    protected ProductRepository productRepository;
    private static boolean isLockScreenOpen = false;
    private boolean locationDialogShown = false;
    
    // Global flag to track if user chose to proceed offline.
    // Cleared when internet is restored.
    private static boolean isOfflineProceededGlobally = false;
    
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Runnable idleRunnable = this::checkSessionAndIdle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        settingsManager = new SettingsManager(this);
        productRepository = new ProductRepository(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogManager.logSystem(this, "Resumed activity: " + getClass().getSimpleName());
        checkSessionAndIdle();
        startIdleTimer();
        if (shouldCheckNetworkOnResume() && !NetworkUtil.isNetworkAvailable(this)) {
            if (!isOfflineProceededGlobally) {
                checkCachedProductsAndShowDialog();
            }
        } else if (NetworkUtil.isNetworkAvailable(this)) {
            isOfflineProceededGlobally = false;
            // Only check location if not already showing dialog
            if (!locationDialogShown) {
                checkLocation();
            }
        }
    }

    public static void setOfflineProceeded(boolean proceeded) {
        isOfflineProceededGlobally = proceeded;
    }

    private void checkCachedProductsAndShowDialog() {
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean hasProducts = productRepository.hasCachedProducts();
            boolean canGoOffline = hasProducts && isOfflineAccessAllowed();
            runOnUiThread(() -> {
                NetworkUtil.showNoInternetDialog(this, false, 
                    canGoOffline ? this::onNavigateToOffline : null, 
                    null);
            });
        });
    }

    protected boolean isOfflineAccessAllowed() {
        // Allow offline access if user is identified.
        // This ensures they still have to pass through AuthGate if session is locked/invalid but ID exists.
        return session != null && session.isUserIdSet();
    }

    protected void onNavigateToOffline() {
        isOfflineProceededGlobally = true;
        
        // If we are at StartScreen, we should NOT go directly to MainActivity
        // if authentication is required. We should let StartScreen proceed with its intent.
        if (this instanceof StartScreen) {
            ((StartScreen) this).proceedAfterOfflineSelection();
            return;
        }

        // If we are already on an offline-capable screen (MainActivity or its fragments), just stay.
        // If we are on a blocking screen like LoginActivity (but somehow have a session ID), 
        // we might want to go to MainActivity.
        if (!(this instanceof MainActivity || this instanceof AuthGateActivity || this instanceof LockActivity)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    protected boolean shouldCheckNetworkOnResume() {
        return true;
    }

    protected boolean isOfflineSupported() {
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogManager.logSystem(this, "Paused activity: " + getClass().getSimpleName());
        stopIdleTimer();
    }

    protected void checkSessionAndIdle() {
        if (this instanceof LoginActivity || this instanceof LockActivity 
                || this instanceof AuthGateActivity || this instanceof OnboardingActivity 
                || this instanceof ConfigActivity || this instanceof ForgotPasswordActivity 
                || this instanceof ResetPasswordActivity || this instanceof StartScreen) {
            return;
        }

        if (!session.isSessionValid()) {
            logoutUser();
            return;
        }

        if (session.isIdleTimeout(settingsManager.getAutoLockTimeMillis())) {
            openLockScreen();
        } else {
            startIdleTimer();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (shouldUpdateActivity()) {
            session.updateLastActivity();
            startIdleTimer();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (shouldUpdateActivity()) {
            session.updateLastActivity();
            startIdleTimer();
        }
        return super.dispatchTouchEvent(ev);
    }

    private void startIdleTimer() {
        if (!shouldUpdateActivity()) return;
        idleHandler.removeCallbacks(idleRunnable);
        long timeout = settingsManager.getAutoLockTimeMillis();
        if (timeout < Long.MAX_VALUE) {
            idleHandler.postDelayed(idleRunnable, timeout);
        }
    }

    private void stopIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable);
    }

    private boolean shouldUpdateActivity() {
        return !(this instanceof LoginActivity || this instanceof LockActivity 
                || this instanceof AuthGateActivity || this instanceof OnboardingActivity 
                || this instanceof ConfigActivity || this instanceof ForgotPasswordActivity 
                || this instanceof ResetPasswordActivity || this instanceof StartScreen);
    }

    protected void openLockScreen() {
        if (isLockScreenOpen) return;
        isLockScreenOpen = true;
        Intent intent = new Intent(this, LockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    protected void logoutUser() {
        GPSManager.stopTracking(this);
        WorkManager.getInstance(this).cancelAllWorkByTag("gps_restart");
        session.clearSession();
        Intent intent = new Intent(this, AuthGateActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    public static void setLockScreenOpen(boolean open) {
        isLockScreenOpen = open;
    }

    private void checkLocation() {
        if (!LocationCheckUtil.hasLocationPermission(this) ||
                !LocationCheckUtil.isLocationEnabled(this)) {
            locationDialogShown = true;
            LocationCheckUtil.showLocationDialog(this,
                    () -> {
                        locationDialogShown = false;
                        if (session.isUserIdSet()) {
                            GPSManager.startTracking(this);
                        }
                    }, () -> {
                        locationDialogShown = false;
                    }, () -> locationDialogShown = false);
        }
    }
}
