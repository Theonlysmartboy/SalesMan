package com.js.salesman.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.js.salesman.R;
import com.js.salesman.models.LoginRequest;
import com.js.salesman.models.LoginResponse;
import com.js.salesman.network.RetrofitClient;
import com.js.salesman.session.SessionManager;
import com.js.salesman.ui.MainActivity;
import com.js.salesman.utils.Db;
import com.js.salesman.utils.InputValidator;

import java.util.Objects;

import es.dmoral.toasty.Toasty;

public class LoginActivity extends AppCompatActivity {
    TextInputEditText etUname, etPassword;
    MaterialCheckBox chkRemember;
    MaterialButton btnLogin;
    TextView txtForgot;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        etUname = findViewById(R.id.etUname);
        etPassword = findViewById(R.id.etPassword);
        chkRemember = findViewById(R.id.chkRemember);
        btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> {
            String uname = Objects.requireNonNull(etUname.getText()).toString();
            String password = Objects.requireNonNull(etPassword.getText()).toString();
            boolean rememberMe = chkRemember.isChecked();
            // Validate username and password
            boolean isPasswordValid = InputValidator.validate(
                    InputValidator.InputType.PASSWORD,
                    password,
                    8,
                    null);
            boolean isUsernameValid = InputValidator.validate(
                    InputValidator.InputType.TEXT,
                    uname,
                    3,
                    20);
            if(!isUsernameValid && !isPasswordValid) {
                etUname.setError("Invalid username");
                etPassword.setError("Invalid password");
                Toasty.warning(this, "Invalid username and password", Toasty.LENGTH_SHORT).show();
            } else if (!isUsernameValid) {
                etUname.setError("Invalid username");
                Toasty.warning(this, "Valid username must be at least 3 characters long", Toasty.LENGTH_LONG).show();
            } else if(!isPasswordValid) {
                etPassword.setError("Invalid password");
                Toasty.warning(this,
                    "Valid password must be at least 8 characters long and contain at least one uppercase, lowercase, digit and special character", Toasty.LENGTH_LONG).show();
            }else{
                performLogin(uname, password, rememberMe);
            }
                });
        txtForgot = findViewById(R.id.txtForgot);
        // Set click listener for Forgot Password text
        txtForgot.setOnClickListener(v -> {
            // Navigate to ForgotPasswordActivity (using Intent)
            startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
            finish();
        });
    }

    private void performLogin(String uname, String password, boolean rememberMe) {
        btnLogin.setEnabled(false);
        var api = RetrofitClient.getApi(this);
        var request = new LoginRequest(uname, password);
        api.login("login", request).enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<LoginResponse> call,
                                @NonNull retrofit2.Response<LoginResponse> response) {
                btnLogin.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse body = response.body();
                    if (body.success) {
                        // Save user locally
                        Db db = new Db(LoginActivity.this);
                        db.deleteUser();
                        db.storeUser(
                                String.valueOf(body.data.user.id),
                                body.data.user.username,
                                body.data.user.role,
                                body.data.user.full_name,
                                body.data.token);
                        // Save session
                        SessionManager session = new SessionManager(LoginActivity.this);
                        session.createSession(
                                String.valueOf(body.data.user.id),
                                body.data.user.username,
                                body.data.user.role,
                                body.data.user.full_name,
                                body.data.token,
                                rememberMe
                        );
                        // Success toast
                        Toasty.success(LoginActivity.this,
                                "Login successful",
                                Toasty.LENGTH_SHORT,
                                true).show();
                        // Navigate to dashboard
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toasty.error(LoginActivity.this,
                                body.message,
                                Toasty.LENGTH_LONG).show();
                    }
                } else {
                    try {
                        if (response.errorBody() != null) {
                            String errorJson = response.errorBody().string();
                            Gson gson = new Gson();
                            LoginResponse errorResponse = gson.fromJson(errorJson, LoginResponse.class);
                            if (errorResponse != null && errorResponse.message != null) {
                                Toasty.error(LoginActivity.this, "Login failed: " + errorResponse.message, Toasty.LENGTH_LONG).show();
                            } else {
                                Toasty.error(LoginActivity.this, "Login failed: " + response.code(), Toasty.LENGTH_LONG).show();
                            }
                        } else {
                            Toasty.error(LoginActivity.this, "Login failed: " + response.code(), Toasty.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toasty.error(LoginActivity.this, "Login failed: " + response.code(), Toasty.LENGTH_LONG).show();
                    }
                }
            }
            @Override
            public void onFailure(@NonNull retrofit2.Call<LoginResponse> call,
                                @NonNull Throwable t) {
                btnLogin.setEnabled(true);
                Toasty.error(LoginActivity.this,
                        "Network error: " + t.getMessage(),
                        Toasty.LENGTH_LONG).show();
            }
        });
    }
}