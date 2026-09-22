package com.js.salesman.utils;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.js.salesman.ui.activities.ConfigActivity;
import com.js.salesman.ui.activities.auth.AuthGateActivity;
import com.js.salesman.ui.activities.auth.LoginActivity;
import com.js.salesman.ui.activities.OnboardingActivity;
import com.js.salesman.utils.managers.PrefsManager;
import com.js.salesman.utils.managers.SessionManager;

public final class AppRouter {

    private AppRouter() {}

    public static final String REASON_NOT_CONFIGURED = "not_configured";

    /** Single source of truth for where the user should go next. */
    @NonNull
    public static Intent route(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        PrefsManager prefs   = new PrefsManager(app);
        SessionManager session = new SessionManager(app);
        Db db = Db.getInstance(app);
        if (prefs.isFirstLaunch()) {
            return new Intent(app, OnboardingActivity.class);
        }
        if (!db.isConfigured()) {
            return ConfigActivity.newIntent(app, REASON_NOT_CONFIGURED);
        }
        if (!session.isUserIdSet()) {
            return new Intent(app, LoginActivity.class);
        }
        return new Intent(app, AuthGateActivity.class);
    }

    public static void go(@NonNull Context ctx) {
        ctx.startActivity(route(ctx));
    }
}