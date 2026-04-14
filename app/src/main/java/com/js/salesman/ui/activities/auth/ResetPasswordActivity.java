package com.js.salesman.ui.activities.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.js.salesman.ui.activities.BaseActivity;
import com.js.salesman.R;

public class ResetPasswordActivity extends BaseActivity {
TextView txtLogin;

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
        txtLogin = findViewById(R.id.txtLogin);
        txtLogin.setOnClickListener(v -> {
            startActivity(new Intent(ResetPasswordActivity.this, LoginActivity.class));
            finish();
        });
    }
}