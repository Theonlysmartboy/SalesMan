package com.js.salesman;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.js.salesman.utils.managers.SettingsManager;
import com.js.salesman.workers.ProductSyncWorker;
import com.js.salesman.workers.TrackingSyncWorker;

public class SalesManApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        applyDarkMode();
        ProductSyncWorker.schedule(this);
        TrackingSyncWorker.schedulePeriodicSync(this);
        TrackingSyncWorker.enqueueOneTimeSync(this);
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
