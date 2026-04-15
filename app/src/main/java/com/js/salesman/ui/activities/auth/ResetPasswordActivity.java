package com.js.salesman.ui.activities.auth;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.js.salesman.R;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.ui.activities.BaseActivity;
import com.js.salesman.utils.AppConstants;
import com.js.salesman.utils.InputValidator;
import com.js.salesman.utils.TrailingDotsLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordActivity extends BaseActivity {
    private TextInputEditText etUname, etOtp, etPassword, etConfirm;
    private MaterialButton btnReset;
    private FrameLayout loaderOverlay;
    private TrailingDotsLoader trailingCircularDotsLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reset_password);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        handleIntent();
    }

    private void initViews() {
        etUname = findViewById(R.id.etUname);
        etOtp = findViewById(R.id.etOtp);
        etPassword = findViewById(R.id.etPassword);
        etConfirm = findViewById(R.id.etConfirm);
        btnReset = findViewById(R.id.btnReset);
        TextView txtLogin = findViewById(R.id.txtLogin);
        loaderOverlay = findViewById(R.id.loaderOverlay);

        trailingCircularDotsLoader = new TrailingDotsLoader(this);
        trailingCircularDotsLoader.setPrimaryColor(Color.parseColor(AppConstants.loaderPrimaryColor));
        trailingCircularDotsLoader.setSecondaryColor(Color.parseColor(AppConstants.loaderSecondaryColor));
        trailingCircularDotsLoader.setDotCount(AppConstants.loaderDotsCount);
        trailingCircularDotsLoader.setDotRadius(AppConstants.loaderDotsRadius);
        trailingCircularDotsLoader.setAnimationDuration(AppConstants.loaderAnimationDuration);

        btnReset.setOnClickListener(v -> handleResetPassword());
        txtLogin.setOnClickListener(v -> {
            startActivity(new Intent(ResetPasswordActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void handleIntent() {
        if (getIntent().hasExtra("username")) {
            String username = getIntent().getStringExtra("username");
            etUname.setText(username);
        }
    }

    private void handleResetPassword() {
        String username = Objects.requireNonNull(etUname.getText()).toString().trim();
        String otp = Objects.requireNonNull(etOtp.getText()).toString().trim();
        String password = Objects.requireNonNull(etPassword.getText()).toString().trim();
        String confirmPassword = Objects.requireNonNull(etConfirm.getText()).toString().trim();
        // Validate username and password
        boolean isPasswordValid = InputValidator.validate(
                InputValidator.InputType.PASSWORD,
                password, 6, null);
        boolean isUsernameValid = InputValidator.validate(
                InputValidator.InputType.TEXT, username, 3, 20);
        if(!isUsernameValid && !isPasswordValid) {
            etUname.setError("Invalid username");
            etPassword.setError("Invalid password");
            Toasty.warning(this, "Invalid username and password",
                    Toasty.LENGTH_SHORT).show();
            return;
        } else if (!isUsernameValid) {
            etUname.setError("Invalid username");
            Toasty.warning(this, "Valid username must be at least 3 characters long",
                    Toasty.LENGTH_LONG).show();
            return;
        } else if(!isPasswordValid) {
            etPassword.setError("Invalid password");
            Toasty.warning(this,
                "Password must be at least 6 characters long and contain at least one uppercase," +
                        " one lowercase and  one special character",
                    Toasty.LENGTH_LONG).show();
            return;
        }else if (otp.isEmpty()) {
            etOtp.setError("OTP is required");
            return;
        }else if (!password.equals(confirmPassword)) {
            etConfirm.setError("Passwords do not match");
            return;
        }

        performReset(username, otp, password);
    }

    private void performReset(String username, String otp, String password) {
        btnReset.setEnabled(false);
        showLoader();

        ApiInterface api = ApiClient.getApi(this);
        Map<String, Object> body = new HashMap<>();
        body.put("userName", username);
        body.put("otp", otp);
        body.put("newPassword", password);

        api.resetPassword("reset-password-otp", body).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                btnReset.setEnabled(true);
                hideLoader();

                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> result = response.body();
                    boolean success = result.containsKey("success") && Boolean.TRUE.equals(result.get("success"));
                    String message = result.containsKey("message") ? String.valueOf(result.get("message")) : "Process completed";

                    if (success) {
                        Toasty.success(ResetPasswordActivity.this, "Password updated successfully", Toasty.LENGTH_LONG).show();
                        Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toasty.error(ResetPasswordActivity.this, message, Toasty.LENGTH_LONG).show();
                    }
                } else {
                    Toasty.error(ResetPasswordActivity.this, "Reset failed: " + response.message(), Toasty.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                btnReset.setEnabled(true);
                hideLoader();
                Toasty.error(ResetPasswordActivity.this, "Network error: " + t.getMessage(), Toasty.LENGTH_LONG).show();
            }
        });
    }

    private void showLoader() {
        if (trailingCircularDotsLoader.getParent() != null) return;
        int size = getResources().getDimensionPixelSize(R.dimen.loader_size);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.gravity = android.view.Gravity.CENTER;
        trailingCircularDotsLoader.setLayoutParams(params);
        loaderOverlay.removeAllViews();
        loaderOverlay.addView(trailingCircularDotsLoader);
        loaderOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoader() {
        loaderOverlay.setVisibility(View.GONE);
        loaderOverlay.removeAllViews();
    }
}
