package com.js.salesman.ui.activities.auth;

import static com.js.salesman.utils.Cryptography.hashPin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.js.salesman.R;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.repository.UserRepository;
import com.js.salesman.ui.activities.BaseActivity;
import com.js.salesman.ui.activities.MainActivity;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.managers.SessionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PinActivity extends BaseActivity {
    private static final String TAG = "PinActivity";
    private SessionManager session;
    private UserRepository userRepository;

    private EditText pin1;
    private EditText[] pinFields;
    private TextView tvTitle, tvSubtitle;
    private Button btnSave;
    private String[] pinValues = {"", "", "", ""};
    private String firstPin = "";
    private boolean isConfirming = false;
    private boolean isSaving = false;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private volatile Call<Map<String, Object>> pinCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pin);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),
                (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;});
        session = new SessionManager(this);
        userRepository = UserRepository.getInstance(this);
        // If the session expired, go back to log in.
        if (!session.isSessionValid()) {
            goToLogin();
            return;
        }
        pin1 = findViewById(R.id.pin1);
        EditText pin2 = findViewById(R.id.pin2);
        EditText pin3 = findViewById(R.id.pin3);
        EditText pin4 = findViewById(R.id.pin4);
        pinFields = new EditText[]{pin1, pin2, pin3, pin4};
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        btnSave = findViewById(R.id.btnSave);
        setupPinInputs();
        btnSave.setOnClickListener(v -> onSaveClicked());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Intentionally blocked: the user must set a PIN.
            }
        });
    }

    @Override
    protected void onDestroy() {
        Call<Map<String, Object>> call = pinCall;
        if (call != null) call.cancel();
        ioExecutor.shutdown();
        super.onDestroy();
    }

    private void onSaveClicked() {
        if (isSaving) return;
        String pin = pinValues[0] + pinValues[1] + pinValues[2] + pinValues[3];
        if (pin.length() < 4) {
            Toasty.error(this, "Enter full PIN", Toasty.LENGTH_SHORT).show();
            clearPin();
            return;
        }
        if (!isConfirming) {
            firstPin = pin;
            isConfirming = true;
            clearPin();
            tvTitle.setText(R.string.confirm_pin);
            tvSubtitle.setText(R.string.please_re_enter_your_pin_to_confirm);
            btnSave.setText(R.string.confirm);
            return;
        }
        if (pin.equals(firstPin)) {
            savePin(pin, session.getUserId());
        } else {
            Toasty.error(this, "PINs do not match",
                    Toasty.LENGTH_SHORT, true).show();
            isConfirming = false;
            firstPin = "";
            clearPin();
            tvTitle.setText(R.string.user_pin);
            tvSubtitle.setText(R.string.set_a_pin_for_fast_access);
            btnSave.setText(R.string.save);
        }
    }

    private void savePin(String inputPin, String userId) {
        setSaving(true);
        ioExecutor.execute(() -> {
            final String hashedPin;
            try {
                hashedPin = hashPin(inputPin);
            } catch (Exception e) {
                Log.e(TAG, "Failed to hash PIN", e);
                runOnUiThread(() -> failSave("Couldn't secure your PIN. " +
                        "Please try again."));
                return;
            }
            userRepository.saveUserPin(userId, hashedPin, saved -> {
                if (isFinishing() || isDestroyed()) return;
                if (!Boolean.TRUE.equals(saved)) {
                    Log.e(TAG, "Local DB save failed");
                    failSave("Couldn't save your PIN on this device. Please try again.");
                    return;
                }
                notifyServer(userId);
            });
        });
    }

    private void notifyServer(String userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("has_pin", 1);
        ioExecutor.execute(() -> {
            try {
                Call<Map<String, Object>> call = ApiClient.getApi(getApplicationContext())
                        .setHasPin("set-has-pin-status", body);
                pinCall = call;
                if (isDestroyed()) {
                    call.cancel();
                    return;
                }
                call.enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<Map<String, Object>> call,
                                            @NonNull Response<Map<String, Object>> response) {
                        if (isFinishing() || isDestroyed()) return;
                        Map<String, Object> res = response.body();
                        if (!response.isSuccessful() || res == null) {
                            Log.e(TAG, "Invalid server response: " + response.code());
                            failSave("The server couldn't process your request. " +
                                    "Please try again.");
                        } else if (!Boolean.TRUE.equals(res.get("success"))) {
                            Log.e(TAG, "Server rejected request");
                            failSave("The server rejected your request. Please try again.");
                        } else {
                            markPinActiveLocally(userId);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Map<String, Object>> call,
                                            @NonNull Throwable t) {
                        if (call.isCanceled() || isFinishing() || isDestroyed()) return;
                        Log.e(TAG, "setHasPin failed", t);
                        failSave(NetworkUtil.getFriendlyNetError(PinActivity.this, t,
                                false));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Unable to start PIN status request", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    failSave(NetworkUtil.getFriendlyNetError(PinActivity.this,
                            e, false));
                });
            }
        });
    }

    private void markPinActiveLocally(String userId) {
        userRepository.updatePinLocal(userId, 1, updated -> {
            if (isFinishing() || isDestroyed()) return;
            if (!Boolean.TRUE.equals(updated)) {
                Log.e(TAG, "Local PIN status update failed");
                failSave("Your PIN was saved but couldn't be activated. " +
                        "Please try again.");
                return;
            }
            firstPin = "";
            Toasty.success(this, "PIN saved successfully",
                    Toasty.LENGTH_SHORT, true).show();
            session.updateLastActivity();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void failSave(String userMessage) {
        setSaving(false);
        Toasty.error(this, userMessage, Toasty.LENGTH_LONG).show();
    }

    private void setSaving(boolean saving) {
        isSaving = saving;
        btnSave.setEnabled(!saving);
        for (EditText field : pinFields) {
            field.setEnabled(!saving);
        }
    }

    private void setupPinInputs() {
        for (int i = 0; i < pinFields.length; i++) {
            final int index = i;
            EditText field = pinFields[i];
            field.setInputType(InputType.TYPE_CLASS_NUMBER);
            field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
            // Disable paste / selection
            field.setLongClickable(false);
            field.setTextIsSelectable(false);
            field.addTextChangedListener(new TextWatcher() {
                boolean isUpdating = false;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (isUpdating || isSaving) return;
                    if (s.length() == 1) {
                        isUpdating = true;
                        pinValues[index] = s.toString();   // keep the real digit
                        field.setText("•");                // show a dot
                        isUpdating = false;
                        if (index < pinFields.length - 1) {
                            pinFields[index + 1].requestFocus();
                        } else {
                            btnSave.performClick();
                        }
                    }
                }
            });
            field.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL
                        && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (!pinValues[index].isEmpty()) {
                        pinValues[index] = "";
                        field.setText("");
                        return true;
                    } else if (index > 0) {
                        pinFields[index - 1].requestFocus();
                        pinValues[index - 1] = "";
                        pinFields[index - 1].setText("");
                        return true;
                    }
                }
                return false;
            });
            field.setOnFocusChangeListener((v, hasFocus) -> {
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
        for (EditText field : pinFields) {
            field.setText("");
        }
        pin1.requestFocus();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}