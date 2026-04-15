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
import com.js.salesman.utils.TrailingDotsLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends BaseActivity {
    private TextInputEditText etEmail;
    private MaterialButton btnSend;
    private FrameLayout loaderOverlay;
    private TrailingDotsLoader trailingCircularDotsLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_forgot_password);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        btnSend = findViewById(R.id.btnSend);
        TextView txtLogin = findViewById(R.id.txtLogin);
        loaderOverlay = findViewById(R.id.loaderOverlay);

        trailingCircularDotsLoader = new TrailingDotsLoader(this);
        trailingCircularDotsLoader.setPrimaryColor(Color.parseColor(AppConstants.loaderPrimaryColor));
        trailingCircularDotsLoader.setSecondaryColor(Color.parseColor(AppConstants.loaderSecondaryColor));
        trailingCircularDotsLoader.setDotCount(AppConstants.loaderDotsCount);
        trailingCircularDotsLoader.setDotRadius(AppConstants.loaderDotsRadius);
        trailingCircularDotsLoader.setAnimationDuration(AppConstants.loaderAnimationDuration);

        btnSend.setOnClickListener(v -> handleRequestReset());
        txtLogin.setOnClickListener(v -> {
            startActivity(new Intent(ForgotPasswordActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void handleRequestReset() {
        String username = Objects.requireNonNull(etEmail.getText()).toString().trim();

        if (username.isEmpty()) {
            etEmail.setError("Username/Email is required");
            return;
        }

        if (username.length() < 3) {
            etEmail.setError("Enter a valid username or email");
            return;
        }

        performRequestReset(username);
    }

    private void performRequestReset(String username) {
        btnSend.setEnabled(false);
        showLoader();

        ApiInterface api = ApiClient.getApi(this);
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);

        api.requestPasswordReset("request-reset", body).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                btnSend.setEnabled(true);
                hideLoader();

                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> result = response.body();
                    boolean success = result.containsKey("success") && Boolean.TRUE.equals(result.get("success"));
                    String message = result.containsKey("message") ? String.valueOf(result.get("message")) : "Request processed";

                    if (success) {
                        Toasty.success(ForgotPasswordActivity.this, message, Toasty.LENGTH_LONG).show();
                        Intent intent = new Intent(ForgotPasswordActivity.this, ResetPasswordActivity.class);
                        intent.putExtra("username", username);
                        startActivity(intent);
                        finish();
                    } else {
                        Toasty.error(ForgotPasswordActivity.this, message, Toasty.LENGTH_LONG).show();
                    }
                } else {
                    Toasty.error(ForgotPasswordActivity.this, "Request failed: " + response.code(), Toasty.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                btnSend.setEnabled(true);
                hideLoader();
                Toasty.error(ForgotPasswordActivity.this, "Network error: " + t.getMessage(), Toasty.LENGTH_LONG).show();
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
