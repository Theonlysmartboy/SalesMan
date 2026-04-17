package com.js.salesman.ui.activities.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.js.salesman.R;
import com.js.salesman.ui.activities.MainActivity;
import com.js.salesman.utils.Db;
import com.js.salesman.utils.managers.SessionManager;

import java.util.concurrent.Executor;

public class AuthGateActivity extends AppCompatActivity {

    private SessionManager session;
    private Db db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This activity doesn't need a layout, it's a router
        session = new SessionManager(this);
        db = new Db(this);

        checkAuthStatus();
    }

    private void checkAuthStatus() {
        String userId = session.getUserId();

        // If no user ID, session is completely gone (shouldn't happen if routed correctly from Splash, 
        // but for safety redirect to Login)
        if (userId == null) {
            goToLogin();
            return;
        }

        // Check if user has PIN set in local DB
        if (!db.userHasPin(userId)) {
            // Force PIN setup if not set
            goToPinSetup();
            return;
        }

        // Try Biometric if available
        if (isBiometricAvailable()) {
            showBiometricPrompt();
        } else {
            // Fallback to PIN
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
                // On error or user cancel, fallback to PIN
                goToLockScreen();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                onAuthSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                // Just a shake/vibration usually, user can try again or cancel to fallback via Error
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
        session.setLocked(false);
        session.updateLastActivity();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToLockScreen() {
        Intent intent = new Intent(this, LockActivity.class);
        // We tell LockActivity it's a launch auth, not an idle lock
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
