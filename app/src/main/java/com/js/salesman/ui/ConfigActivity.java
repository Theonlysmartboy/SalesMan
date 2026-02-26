package com.js.salesman.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.js.salesman.R;
import com.js.salesman.ui.auth.LoginActivity;
import com.js.salesman.utils.Db;

import java.util.Objects;

public class ConfigActivity extends AppCompatActivity {
    TextInputEditText edtUrl;
    MaterialButton test, save;
    Db db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_config);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        edtUrl = findViewById(R.id.edtUrl);
        test = findViewById(R.id.btnTest);
        test.setOnClickListener(view -> {
            String url = Objects.requireNonNull(edtUrl.getText()).toString().trim();
            if (url.isEmpty()) {
                edtUrl.setError("Enter server URL");
                return;
            }

            if (!Patterns.WEB_URL.matcher(url).matches()) {
                edtUrl.setError("Invalid URL format");
                return;
            }

            testServerConnection(url);
                });
        save = findViewById(R.id.btnSubmit);
        save.setOnClickListener(view -> {
            String link = Objects.requireNonNull(edtUrl.getText()).toString().trim();
            if (link.isEmpty()) {
                edtUrl.setError("Enter server URL");
                return;
            }
            if (!Patterns.WEB_URL.matcher(link).matches()) {
                edtUrl.setError("Invalid URL format");
                return;
            }
            db = new Db(getApplication());
            db.storeConfig(link);
            Intent login = new Intent(ConfigActivity.this, LoginActivity.class);
            startActivity(login);
            finish();
        });
    }
}