package com.js.salesman.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.js.salesman.R;
import com.js.salesman.ui.activities.auth.LoginActivity;
import com.js.salesman.utils.AppRouter;
import com.js.salesman.utils.database.Db;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import es.dmoral.toasty.Toasty;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ConfigActivity extends BaseActivity {
    TextInputEditText edtUrl;
    MaterialButton test, save;
    Db db;

    public static final String EXTRA_REASON = "extra_reason";

    public static Intent newIntent(@NonNull Context ctx, @NonNull String reason) {
        Intent i = new Intent(ctx, ConfigActivity.class);
        i.putExtra(EXTRA_REASON, reason);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_config);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),
                (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        edtUrl = findViewById(R.id.edtUrl);
        test = findViewById(R.id.btnTest);
        save = findViewById(R.id.btnSubmit);
        save.setVisibility(View.GONE);
        // Re-hide Save whenever the URL is edited
        edtUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                save.setVisibility(View.GONE);
            }
        });
        // Contextual hint
        String reason = getIntent().getStringExtra(EXTRA_REASON);
        if (AppRouter.REASON_NOT_CONFIGURED.equals(reason)) {
            Toasty.info(this,
                    "Server configuration is required to continue",
                    Toasty.LENGTH_LONG).show();
        }
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
            testServerConnection(url, () -> save.setVisibility(View.VISIBLE));
        });
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

    private void testServerConnection(String baseUrl, Runnable onSuccess) {
        String testUrl = baseUrl + "/api/health.php";
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS).build();
        Request request = new Request.Builder().url(testUrl)
                .get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toasty.error(getApplicationContext(),
                        "Server unreachable",
                        Toasty.LENGTH_LONG).show());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toasty.success(getApplicationContext(), "Server reachable ✅",
                                Toasty.LENGTH_LONG).show();
                        if (onSuccess != null) onSuccess.run();
                    } else {
                        Toasty.error(getApplicationContext(),
                                "Server responded with error: " + response.code(),
                                Toasty.LENGTH_LONG).show();
                    }
                });
                response.close();
            }
        });
    }
}