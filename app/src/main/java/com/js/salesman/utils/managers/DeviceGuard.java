package com.js.salesman.utils.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.js.salesman.R;
import com.js.salesman.models.DeviceGuardDecision;
import com.js.salesman.models.results.DeviceVerificationResult;
import com.js.salesman.utils.NetworkUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DeviceGuard {

    private static final String LOG_TAG = "DEVICE";

    /** Minimum time between two online checks while the app is simply being used. */
    public static final long RECHECK_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    /** How long a phone may stay unverified (offline) before it is locked out. */
    public static final long OFFLINE_GRACE_MS = TimeUnit.HOURS.toMillis(72);
    /** Longest an unlock waits for the server before falling back to the offline grace rule. */
    public static final long UNLOCK_CHECK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(8);

    private static final String PREFS_NAME = "device_guard";
    private static final String PREF_LAST_SUCCESS_AT = "last_success_at";
    private static final String PREF_BLOCKED = "blocked";
    private static final String PREF_BLOCKED_MESSAGE = "blocked_message";

    /** Receives only decisions that need the UI to act. Always called on the main thread. */
    public interface Callback {
        void onDecision(@NonNull DeviceGuardDecision decision);
    }

    /** Result of an unlock check; {@code null} means the user may proceed. Main thread. */
    public interface UnlockCallback {
        void onResult(@Nullable DeviceGuardDecision decision);
    }

    private static volatile DeviceGuard INSTANCE;
    private final Context appContext;
    private final SharedPreferences prefs;
    private final DeviceAuthManager authManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private volatile long lastAttemptElapsed = -1L;
    private volatile boolean forceNextCheck = false;

    private DeviceGuard(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.authManager = DeviceAuthManager.getInstance(appContext);
    }

    @NonNull
    public static DeviceGuard getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (DeviceGuard.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DeviceGuard(context);
                }
            }
        }
        return INSTANCE;
    }

    /** True once the server has said this device is no longer authorised (until the next login). */
    public boolean isBlocked() {
        return prefs.getBoolean(PREF_BLOCKED, false);
    }

    /**
     * Confirms the device with the server right after a valid PIN / biometric unlock.
     * Offline, it applies the grace rule instead. Never blocks the caller.
     */
    public void verifyForUnlock(@NonNull UnlockCallback callback) {
        if (isBlocked()) {
            callback.onResult(DeviceGuardDecision.block(
                    prefs.getString(PREF_BLOCKED_MESSAGE, null)));
            return;
        }
        if (!NetworkUtil.isNetworkAvailable(appContext)) {
            callback.onResult(isWithinGrace() ? null : DeviceGuardDecision.verificationRequired());
            return;
        }
        final AtomicBoolean delivered = new AtomicBoolean(false);
        final Runnable onTimeout = () -> {
            if (delivered.compareAndSet(false, true)) {
                // The server is slow: let the user in under the grace rule, and make the screen
                // that opens next re-check as soon as it can.
                forceNextCheck = true;
                callback.onResult(
                        isWithinGrace() ? null : DeviceGuardDecision.verificationRequired());
            }
        };
        mainHandler.postDelayed(onTimeout, UNLOCK_CHECK_TIMEOUT_MS);
        executor.execute(() -> {
            final DeviceGuardDecision decision = runNetworkCheckSafely();
            mainHandler.post(() -> {
                if (!delivered.compareAndSet(false, true)) return; // timeout already answered
                mainHandler.removeCallbacks(onTimeout);
                lastAttemptElapsed = SystemClock.elapsedRealtime();
                forceNextCheck = false;
                callback.onResult(decision);
            });
        });
    }

    /**
     * Call when the app comes to the foreground. Cheap and safe to call often: it throttles itself,
     * never blocks the caller, and delivers a result only when the UI must act.
     */
    public void evaluate(@NonNull Callback callback) {
        if (isBlocked()) {
            callback.onDecision(DeviceGuardDecision.block(
                    prefs.getString(PREF_BLOCKED_MESSAGE, null)));
            return;
        }
        final boolean online = NetworkUtil.isNetworkAvailable(appContext);
        if (online && isCheckDue()) {
            startNetworkCheck(callback);
            return;
        }
        if (!isWithinGrace()) {
            callback.onDecision(DeviceGuardDecision.verificationRequired());
        }
    }

    /** Makes the next {@link #evaluate} run an online check regardless of the throttle. */
    public void requestImmediateCheck() {
        forceNextCheck = true;
    }

    /**
     * Call after a successful login: the device was just proven, so any earlier verdict is
     * obsolete (for example, the administrator approved the device again).
     */
    public void onLoginSucceeded() {
        prefs.edit()
                .putBoolean(PREF_BLOCKED, false)
                .remove(PREF_BLOCKED_MESSAGE)
                .putLong(PREF_LAST_SUCCESS_AT, System.currentTimeMillis())
                .apply();
        lastAttemptElapsed = SystemClock.elapsedRealtime();
        forceNextCheck = false;
    }

    private boolean isCheckDue() {
        if (forceNextCheck || lastAttemptElapsed < 0) return true;
        return SystemClock.elapsedRealtime() - lastAttemptElapsed >= RECHECK_INTERVAL_MS;
    }

    private void startNetworkCheck(@NonNull Callback callback) {
        if (!inFlight.compareAndSet(false, true)) return; // a check is already running
        lastAttemptElapsed = SystemClock.elapsedRealtime();
        forceNextCheck = false;
        executor.execute(() -> {
            final DeviceGuardDecision decision;
            try {
                decision = runNetworkCheckSafely();
            } finally {
                inFlight.set(false);
            }
            if (decision != null) {
                mainHandler.post(() -> callback.onDecision(decision));
            }
        });
    }

    @Nullable
    private DeviceGuardDecision runNetworkCheckSafely() {
        try {
            return runNetworkCheck();
        } catch (RuntimeException e) {
            LogManager.logError(appContext, LOG_TAG, "Device check failed unexpectedly", e);
            return isWithinGrace() ? null : DeviceGuardDecision.verificationRequired();
        }
    }

    @Nullable
    private DeviceGuardDecision runNetworkCheck() {
        String username = new SessionManager(appContext).getUsername();
        if (username == null || username.trim().isEmpty()) {
            return null; // nobody is signed in, nothing to verify
        }

        DeviceVerificationResult result = authManager.verifyDevice(username);
        switch (result.getState()) {
            case APPROVED:
                prefs.edit().putLong(PREF_LAST_SUCCESS_AT, System.currentTimeMillis()).apply();
                return null;
            case NOT_AUTHORISED:
                LogManager.log(appContext, LOG_TAG, "Device is no longer authorised");
                return persistBlock(appContext.getString(R.string.device_blocked_message));
            case KEY_MISSING:
                LogManager.log(appContext, LOG_TAG, "Device key is missing");
                return persistBlock(appContext.getString(R.string.device_key_missing_message));
            case INCONCLUSIVE:
            default:
                return isWithinGrace() ? null : DeviceGuardDecision.verificationRequired();
        }
    }

    @NonNull
    private DeviceGuardDecision persistBlock(@NonNull String message) {
        prefs.edit()
                .putBoolean(PREF_BLOCKED, true)
                .putString(PREF_BLOCKED_MESSAGE, message)
                .apply();
        return DeviceGuardDecision.block(message);
    }

    /** True while the last successful verification is recent enough to work offline. */
    private boolean isWithinGrace() {
        long last = prefs.getLong(PREF_LAST_SUCCESS_AT, 0L);
        long now = System.currentTimeMillis();
        if (last == 0L) {
            prefs.edit().putLong(PREF_LAST_SUCCESS_AT, now).apply();
            return true;
        }
        if (now < last) return false; // Do not extend the grace if the clock was moved backwards
        return now - last <= OFFLINE_GRACE_MS;
    }
}