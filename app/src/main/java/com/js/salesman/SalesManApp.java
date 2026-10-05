package com.js.salesman;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.managers.SettingsManager;
import com.js.salesman.workers.ProductSyncWorker;
import com.js.salesman.workers.SyncCoordinatorWorker;
import com.js.salesman.workers.TrackingSyncWorker;

public class SalesManApp extends Application {
    private static final String TAG = "SalesManApp";

    @Override
    public void onCreate() {
        super.onCreate();
        applyDarkMode();
        ProductSyncWorker.schedule(this);
        TrackingSyncWorker.schedulePeriodicSync(this);
        TrackingSyncWorker.enqueueOneTimeSync(this);
        registerNetworkCallback();
    }

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                if (NetworkUtil.isNetworkAvailable(SalesManApp.this)) {
                    Log.d(TAG, "Network connection restored. Triggering automatic background synchronization.");
                    SyncCoordinatorWorker.enqueue(SalesManApp.this);
                    TrackingSyncWorker.enqueueOneTimeSync(SalesManApp.this);
                }
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            Log.w(TAG, "Failed to register default network callback", e);
        }
    }

    public void applyDarkMode() {
        SettingsManager settingsManager = new SettingsManager(this);
        int mode = settingsManager.getDarkMode();
        switch (mode) {
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
