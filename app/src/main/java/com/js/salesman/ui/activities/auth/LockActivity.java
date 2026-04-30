package com.js.salesman.ui.activities.auth;

import static com.js.salesman.utils.Cryptography.hashPin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.js.salesman.R;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.ui.activities.BaseActivity;
import com.js.salesman.utils.Db;

import es.dmoral.toasty.Toasty;

public class LockActivity extends BaseActivity {
    private EditText pin1, pin2, pin3, pin4;
    Button btnFingerprint, btnUnlock;
    private String[] pinValues = {"", "", "", ""};
    private Db db;
    private boolean isAuthForAction = false;
    private boolean isLaunchAuth = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isAuthForAction = getIntent().getBooleanExtra("is_auth_for_action", false);
        isLaunchAuth = getIntent().getBooleanExtra("is_launch_auth", false);
        db = new Db(this);
        if (!isAuthForAction) {
            BaseActivity.setLockScreenOpen(true);
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lock);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        session = new SessionManager(this);
        // If no user ID -> go to log in (session cleared)
        if (!session.isUserIdSet()) {
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
            String pin = pinValues[0] + pinValues[1] + pinValues[2] + pinValues[3];
            if (pin.length() < 4) {
                Toasty.error(this, "Enter full PIN", Toasty.LENGTH_SHORT).show();
                clearPin();
                return;
            }
            boolean isValid = validatePin(pin);
            if (!isValid) {
                clearPin();
                pin1.requestFocus();
            }
        });
        // Trigger biometric first
        showBiometricPrompt();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isAuthForAction) {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        });
        }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isAuthForAction) {
            BaseActivity.setLockScreenOpen(false);
        }
    }

    private void unlockSuccess() {
        if (isAuthForAction) {
            setResult(RESULT_OK);
        } else {
            session.setLocked(false);
            session.updateLastActivity();
            // If the session was invalid (e.g. after manual logout), 
            // give it a temporary offline extension so MainActivity doesn't loop back.
            if (!session.isSessionValid()) {
                session.extendSessionOffline();
            }
            if (isLaunchAuth) {
                Intent intent = new Intent(this, com.js.salesman.ui.activities.MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        }
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
            pins[i].setInputType(InputType.TYPE_CLASS_NUMBER);
            pins[i].setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
            pins[i].setLongClickable(false);
            pins[i].setTextIsSelectable(false);
            pins[i].addTextChangedListener(new TextWatcher() {
                boolean isUpdating = false;
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (isUpdating) return;
                    if (s.length() == 1) {
                        isUpdating = true;
                        // Save actual value
                        pinValues[index] = s.toString();
                        // Replace with dot
                        pins[index].setText("•");
                        isUpdating = false;
                        // Move forward AFTER masking
                        if (index < pins.length - 1) {
                            pins[index + 1].requestFocus();
                        } else {
                            btnUnlock.performClick();
                        }
                    }
                }
            });
            pins[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL &&
                        event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (!pinValues[index].isEmpty()) {
                        // Clear current
                        pinValues[index] = "";
                        pins[index].setText("");
                        return true;
                    } else if (index > 0) {
                        // Move back
                        pins[index - 1].requestFocus();
                        pinValues[index - 1] = "";
                        pins[index - 1].setText("");
                        return true;
                    }
                }
                return false;
            });
            pins[i].setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.setBackgroundResource(R.drawable.pin_box_active);
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start();
                } else {
                    v.setBackgroundResource(R.drawable.pin_box_bg);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
            });
        }
        pin1.setBackgroundResource(R.drawable.pin_box_active);
        pin1.requestFocus();
    }

    private void clearPin() {
        pinValues = new String[]{"", "", "", ""};
        pin1.setText("");
        pin2.setText("");
        pin3.setText("");
        pin4.setText("");
        pin1.requestFocus();
    }

    private boolean validatePin(String inputPin) {
        // get hashed PIN from local DB (NOT session plain PIN)
        String storedHash = db.getUserPinHash(session.getUserId());
        if (storedHash == null || storedHash.isEmpty()) {
            Toasty.error(this, "PIN not set for this user", Toasty.LENGTH_SHORT).show();
            return false;
        }
        String inputHash = hashPin(inputPin);
        if (storedHash.equals(inputHash)) {
            unlockSuccess();
            return true;
        } else {
            Toasty.error(this, "Invalid PIN", Toasty.LENGTH_SHORT).show();
            return false;
        }
    }
}