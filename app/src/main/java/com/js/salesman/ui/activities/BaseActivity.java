package com.js.salesman.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.js.salesman.session.SessionManager;
import com.js.salesman.ui.activities.auth.LockActivity;
import com.js.salesman.ui.activities.auth.LoginActivity;
import com.js.salesman.utils.GPSManager;

public abstract class BaseActivity extends AppCompatActivity {
    protected SessionManager session;
    private static boolean isLockScreenOpen = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkSessionAndIdle();
    }

    protected void checkSessionAndIdle() {
        if (this instanceof LoginActivity || this instanceof LockActivity) {
            return;
        }

        if (!session.isSessionValid()) {
            logoutUser();
            return;
        }

        if (session.isIdleTimeout()) {
            openLockScreen();
        } else {
            session.updateLastActivity();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        session.updateLastActivity();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        session.updateLastActivity();
        return super.dispatchTouchEvent(ev);
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
