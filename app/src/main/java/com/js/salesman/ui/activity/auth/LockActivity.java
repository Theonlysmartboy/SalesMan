package com.js.salesman.ui.activity.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.js.salesman.R;
import com.js.salesman.session.SessionManager;

import es.dmoral.toasty.Toasty;

public class LockActivity extends AppCompatActivity {
    private SessionManager session;
    private EditText pin1, pin2, pin3, pin4;
    Button btnFingerprint, btnUnlock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lock);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        session = new SessionManager(this);
        // If session expired completely → go to log in
        if (!session.isSessionValid()) {
            goToLogin();
            return;
        }
        pin1 = findViewById(R.id.pin1);
        pin2 = findViewById(R.id.pin2);
        pin3 = findViewById(R.id.pin3);
        pin4 = findViewById(R.id.pin4);
        setupPinInputs();
        btnFingerprint = findViewById(R.id.btnFingerprint);
        btnFingerprint.setOnClickListener(v -> showBiometricPrompt());
        btnUnlock = findViewById(R.id.btnUnlock);
        btnUnlock.setOnClickListener(v -> {
            String pin =
                    pin1.getText().toString() +
                            pin2.getText().toString() +
                            pin3.getText().toString() +
                            pin4.getText().toString();
            if (pin.length() < 4) {
                Toasty.error(this, "Enter full PIN", Toasty.LENGTH_SHORT).show();
                clearPin();
                return;
            }
            boolean isValid = validatePin(pin);
            if (!isValid) {
                clearPin();
                pin1.requestFocus();
                return;
            }
        });
        // Trigger biometric first
        showBiometricPrompt();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Do nothing
            }
        });
        }

    private void unlockSuccess() {
        SessionManager session = new SessionManager(this);
        session.updateLastActivity();
        finish();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showBiometricPrompt() {
        BiometricPrompt biometricPrompt = new BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        unlockSuccess();
                    }
                    @Override
                    public void onAuthenticationFailed() {
                        Toasty.error(LockActivity.this,
                                "Authentication failed",
                                Toasty.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        // This is triggered when user taps "Use PIN instead"
                        // Do nothing → allow manual PIN input
                    }
                });
        BiometricPrompt.PromptInfo promptInfo =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock App")
                        .setSubtitle("Use fingerprint to continue")
                        .setNegativeButtonText("Use PIN instead")
                        .build();
        biometricPrompt.authenticate(promptInfo);
    }
    private void setupPinInputs() {
        EditText[] pins = {pin1, pin2, pin3, pin4};
        for (int i = 0; i < pins.length; i++) {
            final int index = i;
            pins[i].addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && index < pins.length - 1) {
                        pins[index + 1].requestFocus();
                    }
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
            pins[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && pins[index].getText().toString().isEmpty()
                        && index > 0) {
                    pins[index - 1].requestFocus();
                    pins[index - 1].setText("");
                }
                return false;
            });
        }
    }
    private void clearPin() {
        pin1.setText("");
        pin2.setText("");
        pin3.setText("");
        pin4.setText("");
        pin1.requestFocus();
    }
    private boolean validatePin(String inputPin) {
        String savedPin = session.getPin();
        boolean isValid = false;
        if (savedPin != null && savedPin.equals(inputPin)) {
            unlockSuccess();
            isValid = true;
        } else {
            Toasty.error(this, "Invalid PIN", Toasty.LENGTH_SHORT).show();
        }
        return isValid;
    }
}