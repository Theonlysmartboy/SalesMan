package com.js.salesman.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.js.salesman.R;
import com.js.salesman.models.LoginRequest;
import com.js.salesman.models.LoginResponse;
import com.js.salesman.network.RetrofitClient;
import com.js.salesman.ui.MainActivity;
import com.js.salesman.utils.Db;
import com.js.salesman.utils.InputValidator;

import java.util.Objects;

public class LoginActivity extends AppCompatActivity {
    TextInputEditText etUname, etPassword;
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
        btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> {
            String uname = Objects.requireNonNull(etUname.getText()).toString();
            String password = Objects.requireNonNull(etPassword.getText()).toString();
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
                Toast.makeText(this, "Invalid username and password", Toast.LENGTH_SHORT).show();
            } else if (!isUsernameValid) {
                etUname.setError("Invalid username");
                Toast.makeText(this, "Valid username must be at least 3 characters long", Toast.LENGTH_LONG).show();
            } else if(!isPasswordValid) {
                etPassword.setError("Invalid password");
                Toast.makeText(this,
                    "Valid password must be at least 8 characters long and contain at least one uppercase, lowercase, digit and special character", Toast.LENGTH_LONG).show();
            }else{
                performLogin(uname, password);
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

    private void performLogin(String uname, String password) {
        btnLogin.setEnabled(false);
        var api = RetrofitClient.getApi(this);
        var request = new LoginRequest(uname, password);
        api.login("login", request).enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<LoginResponse> call,
                                   @NonNull retrofit2.Response<LoginResponse> response) {
                btnLogin.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    var body = response.body();
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
                        Toast.makeText(LoginActivity.this,
                                "Login successful",
                                Toast.LENGTH_SHORT).show();
                        // Navigate to dashboard
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this,
                                body.message,
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(LoginActivity.this,
                            "Login failed: " + response.message(),
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(@NonNull retrofit2.Call<LoginResponse> call,
                                  @NonNull Throwable t) {
                btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}