package com.js.salesman.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.js.salesman.session.SessionManager;
import com.js.salesman.ui.activities.auth.ForgotPasswordActivity;
import com.js.salesman.ui.activities.auth.LockActivity;
import com.js.salesman.ui.activities.auth.LoginActivity;
import com.js.salesman.ui.activities.auth.ResetPasswordActivity;
import com.js.salesman.utils.AppConstants;
import com.js.salesman.utils.GPSManager;
import com.js.salesman.utils.SettingsManager;

public abstract class BaseActivity extends AppCompatActivity {
    protected SessionManager session;
    protected SettingsManager settingsManager;
    private static boolean isLockScreenOpen = false;
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Runnable idleRunnable = this::checkSessionAndIdle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        settingsManager = new SettingsManager(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkSessionAndIdle();
        startIdleTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIdleTimer();
    }

    protected void checkSessionAndIdle() {
        if (this instanceof LoginActivity || this instanceof LockActivity 
                || this instanceof OnboardingActivity || this instanceof ConfigActivity
                || this instanceof ForgotPasswordActivity || this instanceof ResetPasswordActivity) {
            return;
        }

        if (!session.isSessionValid()) {
            logoutUser();
            return;
        }

        if (session.isIdleTimeout(settingsManager.getAutoLockTimeMillis())) {
            openLockScreen();
        } else {
            // No need to update activity here, onUserInteraction/dispatchTouchEvent does it
            // but we should schedule the next check
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
                || this instanceof OnboardingActivity || this instanceof ConfigActivity
                || this instanceof ForgotPasswordActivity || this instanceof ResetPasswordActivity);
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
        session.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    public static void setLockScreenOpen(boolean open) {
        isLockScreenOpen = open;
    }
}
