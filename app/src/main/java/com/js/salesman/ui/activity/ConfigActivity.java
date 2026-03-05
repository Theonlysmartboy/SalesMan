package com.js.salesman.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
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
import com.js.salesman.ui.activity.auth.LoginActivity;
import com.js.salesman.utils.Db;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import es.dmoral.toasty.Toasty;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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
            if (db.getConfig().containsKey("url")) {
                db.deleteConfig();
            }
            if(db.storeConfig(link)) {
                Toasty.success(getApplicationContext(),
                        "Server URL saved successfully",
                        Toasty.LENGTH_LONG).show();
                Intent login = new Intent(ConfigActivity.this, LoginActivity.class);
                startActivity(login);
                finish();
            }else{
                Toasty.error(getApplicationContext(),
                        "Unable to save server URL",
                        Toasty.LENGTH_LONG).show();
            }
        });
    }

    private void testServerConnection(String baseUrl) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(baseUrl)
                .get()
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(getApplicationContext(),
                                "Server unreachable",
                                Toast.LENGTH_LONG).show());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(getApplicationContext(),"Server reachable ✅",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "Server responded with error: " + response.code(),
                                Toast.LENGTH_LONG).show();
                    }
                });
                response.close();
            }
        });
    }
}