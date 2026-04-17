package com.js.salesman.utils.managers;

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
        // Foreground location check
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        // If foreground location is granted, start the service.
        // A foreground service with 'location' type works fine without 
        // the background location permission on most Android versions.
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
