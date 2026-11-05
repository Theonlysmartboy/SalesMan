package com.js.salesman.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.js.salesman.repository.ConfigRepository;
import com.js.salesman.ui.activities.ConfigActivity;
import com.js.salesman.ui.activities.OnboardingActivity;
import com.js.salesman.ui.activities.auth.AuthGateActivity;
import com.js.salesman.ui.activities.auth.LoginActivity;
import com.js.salesman.utils.managers.PrefsManager;
import com.js.salesman.utils.managers.SessionManager;

public final class AppRouter {

    public static final String REASON_NOT_CONFIGURED = "not_configured";

    private AppRouter() {}

    public interface RouteCallback {
        void onRoute(@NonNull Intent intent);
    }

    public static void route(@NonNull Context ctx, @NonNull RouteCallback callback) {
        final Context app = ctx.getApplicationContext();
        if (new PrefsManager(app).isFirstLaunch()) {
            callback.onRoute(new Intent(app, OnboardingActivity.class));
            return;
        }
        ConfigRepository.getInstance(app).isConfigured(configured ->
                callback.onRoute(resolve(app, Boolean.TRUE.equals(configured))));
    }

    public static void go(@NonNull Context ctx) {
        route(ctx, intent -> {
            if (ctx instanceof Activity) {
                Activity activity = (Activity) ctx;
                if (activity.isFinishing() || activity.isDestroyed()) return;
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            ctx.startActivity(intent);
        });
    }

    @WorkerThread
    @NonNull
    public static Intent routeBlocking(@NonNull Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (new PrefsManager(app).isFirstLaunch()) {
            return new Intent(app, OnboardingActivity.class);
        }
        return resolve(app, ConfigRepository.getInstance(app).isConfiguredSync());
    }

    @NonNull
    private static Intent resolve(@NonNull Context app, boolean configured) {
        if (!configured) {
            return ConfigActivity.newIntent(app, REASON_NOT_CONFIGURED);
        }
        if (!new SessionManager(app).isUserIdSet()) {
            return new Intent(app, LoginActivity.class);
        }
        return new Intent(app, AuthGateActivity.class);
    }
}