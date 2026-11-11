package com.js.salesman.ui.activities.auth;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.js.salesman.R;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.models.results.DeviceAuthResult;
import com.js.salesman.models.requests.LoginRequest;
import com.js.salesman.models.responses.LoginResponse;
import com.js.salesman.repository.UserRepository;
import com.js.salesman.ui.activities.BaseActivity;
import com.js.salesman.ui.activities.MainActivity;
import com.js.salesman.utils.AppConstants;
import com.js.salesman.utils.InputValidator;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.TrailingDotsLoader;
import com.js.salesman.utils.managers.DeviceAuthManager;
import com.js.salesman.utils.managers.DeviceGuard;
import com.js.salesman.utils.managers.DeviceIdentityManager;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.utils.managers.SessionManager;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends BaseActivity {
    private static final String TAG = "LoginActivity";
    private TextInputEditText etUname, etPassword;
    private MaterialCheckBox chkRemember;
    private MaterialButton btnLogin;
    private FrameLayout loaderOverlay;
    private TrailingDotsLoader trailingCircularDotsLoader;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private volatile Call<LoginResponse> loginCall;
    private DeviceAuthManager deviceAuthManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });
        deviceAuthManager = DeviceAuthManager.getInstance(getApplicationContext());
        etUname = findViewById(R.id.etUname);
        etPassword = findViewById(R.id.etPassword);
        chkRemember = findViewById(R.id.chkRemember);
        loaderOverlay = findViewById(R.id.loaderOverlay);
        TextView txtForgot = findViewById(R.id.txtForgot);
        btnLogin = findViewById(R.id.btnLogin);
        trailingCircularDotsLoader = new TrailingDotsLoader(this);
        trailingCircularDotsLoader.setPrimaryColor(Color.parseColor(
                AppConstants.loaderPrimaryColor));
        trailingCircularDotsLoader.setSecondaryColor(Color.parseColor(
                AppConstants.loaderSecondaryColor));
        trailingCircularDotsLoader.setDotCount(AppConstants.loaderDotsCount);
        trailingCircularDotsLoader.setDotRadius(AppConstants.loaderDotsRadius);
        trailingCircularDotsLoader.setAnimationDuration(AppConstants.loaderAnimationDuration);
        btnLogin.setOnClickListener(v -> attemptLogin());
        txtForgot.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this,
                    ForgotPasswordActivity.class));
            finish();
        });
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        finishAffinity();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        Call<LoginResponse> call = loginCall;
        if (call != null) call.cancel();
        ioExecutor.shutdown();
        super.onDestroy();
    }

    private void attemptLogin() {
        String uname = Objects.requireNonNull(etUname.getText()).toString();
        String password = Objects.requireNonNull(etPassword.getText()).toString();
        boolean rememberMe = chkRemember.isChecked();
        boolean isPasswordValid = InputValidator.validate(
                InputValidator.InputType.PASSWORD, password, 6, null);
        boolean isUsernameValid = InputValidator.validate(
                InputValidator.InputType.TEXT, uname, 3, 20);
        if (!isUsernameValid && !isPasswordValid) {
            etUname.setError("Invalid username");
            etPassword.setError("Invalid password");
            Toasty.warning(this, "Invalid username and password",
                    Toasty.LENGTH_SHORT, true).show();
        } else if (!isUsernameValid) {
            etUname.setError("Invalid username");
            Toasty.warning(this, "Valid username must be 3+ characters long",
                    Toasty.LENGTH_LONG, true).show();
        } else if (!isPasswordValid) {
            etPassword.setError("Invalid password");
            Toasty.warning(this,
                    "Password must be 6+ characters and contain at least 1 uppercase,\n"
                            + " 1 lowercase and  1 special",
                    Toasty.LENGTH_LONG, true).show();
        } else {
            performLogin(uname, password, rememberMe);
        }
    }

    private void performLogin(String uname, String password, boolean rememberMe) {
        btnLogin.setEnabled(false);
        showLoader();
        ioExecutor.execute(() -> {
            try {
                // Step 1: device binding (register if needed, challenge, sign). Blocking.
                final DeviceAuthResult device = deviceAuthManager.prepareProof(uname, password);
                if (isFinishing() || isDestroyed()) return;
                if (device.getState() != DeviceAuthResult.State.READY
                        || device.getProof() == null) {
                    runOnUiThread(() -> handleDeviceNotReady(device));
                    return;
                }
                // Step 2: normal login, now carrying the proof of device possession.
                Call<LoginResponse> call = ApiClient.getApi(getApplicationContext())
                        .login("login", new LoginRequest(uname, password, device.getProof()));
                loginCall = call;
                if (isDestroyed()) {
                    call.cancel();
                    return;
                }
                call.enqueue(new LoginCallback(rememberMe));
            } catch (Exception e) {
                Log.e(TAG, "Unable to start login request", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    resetLoginUi();
                    Toasty.error(LoginActivity.this,
                            NetworkUtil.getFriendlyNetError(LoginActivity.this, e,
                                    false),
                            Toasty.LENGTH_LONG, true).show();
                });
            }
        });
    }

    /** Shows why the device check stopped the login (runs on the UI thread). */
    private void handleDeviceNotReady(@NonNull DeviceAuthResult result) {
        if (isFinishing() || isDestroyed()) return;
        resetLoginUi();
        LogManager.log(this, "DEVICE", "Login blocked by device check: " + result.getState());
        switch (result.getState()) {
            case PENDING_APPROVAL:
                showDeviceDialog(R.string.device_pending_title,
                        getString(R.string.device_pending_message));
                break;
            case SUSPENDED:
                showDeviceDialog(R.string.device_suspended_title,
                        getString(R.string.device_suspended_message));
                break;
            case REJECTED:
                showDeviceDialog(R.string.device_rejected_title,
                        orDefault(result.getMessage(),
                                getString(R.string.device_rejected_message)));
                break;
            case NETWORK_ERROR:
                Throwable error = result.getError();
                Toasty.error(this,
                        NetworkUtil.getFriendlyNetError(this,
                                error != null ? error : new Exception(result.getMessage()),
                                false),
                        Toasty.LENGTH_LONG, true).show();
                break;
            case DEVICE_ERROR:
                showDeviceDialog(R.string.device_error_title,
                        getString(R.string.device_error_message));
                break;
            case READY:
            default:
                break;
        }
    }

    private void showDeviceDialog(@StringRes int titleRes, @NonNull String message) {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * If the server's login error is about the device, forget the local "registered" mark so
     * the next attempt re-registers instead of repeating the same failing request.
     */
    private void resetDeviceRegistrationIfDeviceError(@Nullable String serverMessage) {
        if (serverMessage != null
                && serverMessage.toLowerCase(Locale.ROOT).contains("device")) {
            DeviceIdentityManager.getInstance(getApplicationContext()).clearRegistrationMark();
        }
    }

    private class LoginCallback implements Callback<LoginResponse> {
        private final boolean rememberMe;
        LoginCallback(boolean rememberMe) {
            this.rememberMe = rememberMe;
        }

        @Override
        public void onResponse(@NonNull Call<LoginResponse> call,
                               @NonNull Response<LoginResponse> response) {
            if (isFinishing() || isDestroyed()) return;
            LoginResponse body = response.body();
            if (response.isSuccessful() && body != null) {
                if (body.success) {
                    handleLoginSuccess(body, rememberMe);
                    return;
                }
                Log.e(TAG, "Login failed: " + body.message);
                resetDeviceRegistrationIfDeviceError(body.message);
                Toasty.error(LoginActivity.this, orDefault(body.message,
                                "Login failed"),
                        Toasty.LENGTH_LONG, true).show();
                LogManager.logError(LoginActivity.this, "LOGIN",
                        "Login failed",
                        new Exception(body.message));
            } else {
                String message = parseErrorMessage(response);
                resetDeviceRegistrationIfDeviceError(message);
                Toasty.error(LoginActivity.this, message,
                        Toasty.LENGTH_LONG, true).show();
            }
            resetLoginUi();
        }

        @Override
        public void onFailure(@NonNull Call<LoginResponse> call, @NonNull Throwable t) {
            if (call.isCanceled() || isFinishing() || isDestroyed()) return;
            resetLoginUi();
            Toasty.error(LoginActivity.this,
                    NetworkUtil.getFriendlyNetError(LoginActivity.this, t,
                            false),
                    Toasty.LENGTH_LONG, true).show();
            LogManager.logError(LoginActivity.this, "LOGIN",
                    "Login failed", t);
        }
    }

    private void handleLoginSuccess(@NonNull LoginResponse body, boolean rememberMe) {
        if (body.data == null || body.data.user == null || body.data.token == null) {
            Log.e(TAG, "Login response was missing user data or token");
            Toasty.error(this, "Login failed. Please try again.",
                    Toasty.LENGTH_LONG, true).show();
            resetLoginUi();
            return;
        }
        final var user = body.data.user;
        final String token = body.data.token;
        final String userId = String.valueOf(user.id);
        final UserRepository userRepo = UserRepository.getInstance(this);
        userRepo.storeUser(userId, user.username, user.has_pin, user.role,
                user.full_name, token, saved -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!Boolean.TRUE.equals(saved)) {
                        Log.e(TAG, "Unable to save user locally");
                        Toasty.error(LoginActivity.this,
                                "Unable to save user locally",
                                Toasty.LENGTH_LONG, true).show();
                        resetLoginUi();
                        return;
                    }
                    LogManager.log(LoginActivity.this, "LOGIN",
                            "User logged in: " + user.username);
                    SessionManager session = new SessionManager(LoginActivity.this);
                    session.createSession(userId, user.username, user.role,
                            user.full_name, token, rememberMe);
                    session.updateLastActivity();
                    // The device was just proven during login: reset the post-login device checks.
                    DeviceGuard.getInstance(getApplicationContext()).onLoginSucceeded();
                    Toasty.success(LoginActivity.this, "Login successful",
                            Toasty.LENGTH_SHORT, true).show();
                    userRepo.userHasPin(userId, hasPin -> {
                        if (isFinishing() || isDestroyed()) return;
                        Class<?> next = Boolean.TRUE.equals(hasPin)
                                ? MainActivity.class : PinActivity.class;
                        startActivity(new Intent(LoginActivity.this, next));
                        finish();
                    });
                });
    }

    @NonNull
    private String parseErrorMessage(@NonNull Response<LoginResponse> response) {
        String fallback = "Login failed: " + response.code();
        try (var errorBody = response.errorBody()) {
            if (errorBody == null) return fallback;
            LoginResponse parsed = new Gson().fromJson(errorBody.string(), LoginResponse.class);
            return parsed != null ? orDefault(parsed.message, fallback) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String orDefault(@Nullable String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }

    private void resetLoginUi() {
        btnLogin.setEnabled(true);
        hideLoader();
    }

    private void showLoader() {
        if (trailingCircularDotsLoader.getParent() != null) return;
        int size = getResources().getDimensionPixelSize(R.dimen._80dp);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.CENTER;
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