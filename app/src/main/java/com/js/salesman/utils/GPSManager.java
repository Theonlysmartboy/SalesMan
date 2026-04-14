package com.js.salesman.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.js.salesman.services.GPSService;

public class GPSManager {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    public static void startTracking(Activity activity) {
        //Foreground location
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        //Background location only for Android 10+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE + 1);
                return;
            }
        }
        startService(activity);
    }

    private static void startService(Context context) {
        Intent intent = new Intent(context, GPSService.class);
        context.startForegroundService(intent);
    }

    public static void stopTracking(Context context) {
        Intent intent = new Intent(context, GPSService.class);
        context.stopService(intent);
    }
}
