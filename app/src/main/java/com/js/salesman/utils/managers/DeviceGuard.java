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
    private static final String PREFS_NAME = "device_guard";
    private static final String PREF_LAST_SUCCESS_AT = "last_success_at";
    private static final String PREF_BLOCKED = "blocked";
    private static final String PREF_BLOCKED_MESSAGE = "blocked_message";

    /** Receives only decisions that need the UI to act. Always called on the main thread. */
    public interface Callback {
        void onDecision(@NonNull DeviceGuardDecision decision);
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

    /**
     * Call when the app comes to the foreground. Cheap and safe to call often: it throttles itself,
     * never blocks the caller, and delivers a result only when the UI must act.
     */
    public void evaluate(@NonNull Callback callback) {
        if (prefs.getBoolean(PREF_BLOCKED, false)) {
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

    /** Call after a successful login: the device was just proven, so start from a clean slate. */
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
            DeviceGuardDecision decision = null;
            try {
                decision = runNetworkCheck();
            } catch (RuntimeException e) {
                LogManager.logError(appContext, LOG_TAG,
                        "Device check failed unexpectedly", e);
                if (!isWithinGrace()) decision = DeviceGuardDecision.verificationRequired();
            } finally {
                inFlight.set(false);
            }
            if (decision != null) {
                final DeviceGuardDecision result = decision;
                mainHandler.post(() -> callback.onDecision(result));
            }
        });
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

    /** Returns True while the last successful verification is recent enough to work offline. */
    private boolean isWithinGrace() {
        long last = prefs.getLong(PREF_LAST_SUCCESS_AT, 0L);
        long now = System.currentTimeMillis();
        if (last == 0L) {
            // First run after this feature was installed: start the grace period now.
            prefs.edit().putLong(PREF_LAST_SUCCESS_AT, now).apply();
            return true;
        }
        if (now < last) return false; // the clock was moved backwards: do not extend the grace
        return now - last <= OFFLINE_GRACE_MS;
    }
}