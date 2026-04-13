package com.js.salesman.ui.activity.auth;

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.js.salesman.R;
import com.js.salesman.api.client.ApiClient;
import com.js.salesman.interfaces.SavePinCallBack;
import com.js.salesman.session.SessionManager;
import com.js.salesman.ui.activity.MainActivity;
import com.js.salesman.utils.Db;

import java.util.HashMap;
import java.util.Map;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PinActivity extends AppCompatActivity {
    private SessionManager session;
    private EditText pin1, pin2, pin3, pin4;
    Button  btnSave;
    private String[] pinValues = {"", "", "", ""};
    private Db db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pin);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        session = new SessionManager(this);
        db = new Db(this);
        // If session expired → go to log in
        if (!session.isSessionValid()) {
            goToLogin();
            return;
        }
        pin1 = findViewById(R.id.pin1);
        pin2 = findViewById(R.id.pin2);
        pin3 = findViewById(R.id.pin3);
        pin4 = findViewById(R.id.pin4);
        setupPinInputs();

        btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> {
            String pin = pinValues[0] + pinValues[1] + pinValues[2] + pinValues[3];
            if (pin.length() < 4) {
                Toasty.error(this, "Enter full PIN", Toasty.LENGTH_SHORT).show();
                clearPin();
                pin1.requestFocus();
                return;
            }
            String userId = session.getUserId();
            savePin(pin, userId, new SavePinCallBack() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toasty.success(PinActivity.this, "PIN saved successfully", Toasty.LENGTH_SHORT).show();
                        session.updateLastActivity();
                        startActivity(new Intent(PinActivity.this, MainActivity.class));
                        finish();
                    });
                }
                @Override
                public void onFailure(String error) {
                    runOnUiThread(() ->
                            Toasty.error(PinActivity.this, error, Toasty.LENGTH_SHORT).show()
                    );
                }
            });
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Do nothing
            }
        });
    }

    private void setupPinInputs() {
        EditText[] pins = {pin1, pin2, pin3, pin4};
        for (int i = 0; i < pins.length; i++) {
            final int index = i;
            pins[i].setInputType(InputType.TYPE_CLASS_NUMBER);
            pins[i].setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
            // Disable paste / selection
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
                            btnSave.performClick();
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

    private void savePin(String inputPin, String userId, SavePinCallBack callback) {
        String hashedPin = hashPin(inputPin);
        if (!db.saveUserPin(userId, hashedPin)) {
            callback.onFailure("Local DB save failed");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("has_pin", 1);
        ApiClient.getApi(this)
                .setHasPin("set-has-pin-status", body)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<Map<String, Object>> call,
                                           @NonNull Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> res = response.body();
                            boolean success = Boolean.TRUE.equals(res.get("success"));
                            if (success) {
                                boolean updated = db.updatePinLocal(userId, 1);
                                if (updated) {
                                    callback.onSuccess();
                                } else {
                                    callback.onFailure("Local update failed");
                                }
                            } else {
                                callback.onFailure("Server rejected request");
                            }
                        } else {
                            callback.onFailure("Invalid server response");
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<Map<String, Object>> call,
                                          @NonNull Throwable t) {
                        callback.onFailure(t.getMessage());
                    }
                });
    }
    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}