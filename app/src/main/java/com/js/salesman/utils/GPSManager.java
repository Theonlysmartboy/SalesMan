package com.js.salesman.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.js.salesman.api.service.GPSService;

import es.dmoral.toasty.Toasty;

public class GPSManager {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    public static void startTracking(Activity activity) {
        if (hasLocationPermission(activity)) {
            startService(activity);
        } else {
            requestLocationPermission(activity);
        }
    }

    private static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static void requestLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    public static void handlePermissionResult(Activity activity, int requestCode,
                                              int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startService(activity);
            } else {
                Toasty.warning(activity, "Location permission denied. This app cannot function without it.", Toasty.LENGTH_SHORT).show();
            }
        }
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
