package com.js.salesman.ui.activities.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.js.salesman.R;
import com.js.salesman.ui.activities.BaseActivity;
import com.js.salesman.ui.activities.MainActivity;
import com.js.salesman.utils.Db;
import com.js.salesman.utils.managers.SessionManager;

import java.util.concurrent.Executor;

public class AuthGateActivity extends BaseActivity {

    private Db db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ensure session is initialized. BaseActivity.onCreate should handle this,
        // but we check here to prevent NPEs during the routing logic.
        if (session == null) {
            session = new SessionManager(this);
        }
        
        db = new Db(this);
        checkAuthStatus();
    }

    private void checkAuthStatus() {
        // Double-check session before use
        if (session == null) {
            session = new SessionManager(this);
        }

        String userId = session.getUserId();

        // If no user ID, user must log in from scratch
        if (userId == null) {
            goToLogin();
            return;
        }

        // Check if user has PIN set in local DB
        if (!db.userHasPin(userId)) {
            goToPinSetup();
            return;
        }

        // Use Biometric if available, otherwise fallback to PIN
        if (isBiometricAvailable()) {
            showBiometricPrompt();
        } else {
            goToLockScreen();
        }
    }

    private boolean isBiometricAvailable() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                goToLockScreen();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                
                // If the session was cleared (e.g. after logout), give it a temporary local extension
                if (session != null && !session.isSessionValid()) {
                    session.extendSessionOffline();
                }

                onAuthSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_name))
                .setSubtitle("Authenticate to continue")
                .setNegativeButtonText("Use PIN")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void onAuthSuccess() {
        if (session != null) {
            session.setLocked(false);
            session.updateLastActivity();
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToLockScreen() {
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra("is_launch_auth", true);
        startActivity(intent);
        finish();
    }

    private void goToPinSetup() {
        Intent intent = new Intent(this, PinActivity.class);
        startActivity(intent);
        finish();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
