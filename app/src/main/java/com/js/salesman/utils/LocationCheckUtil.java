package com.js.salesman.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.js.salesman.R;

import es.dmoral.toasty.Toasty;

public class LocationCheckUtil {

    private static AlertDialog currentDialog;

    public static boolean isLocationEnabled(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps = false;
        boolean network = false;
        try {
            gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            network = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {}
        return gps || network;
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void showLocationDialog(final Activity activity, Runnable onSuccess,
                                          Runnable onCancel, Runnable onDismiss) {
        if (currentDialog != null && currentDialog.isShowing()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_location_check, null);
        builder.setView(view);
        builder.setCancelable(false);
        currentDialog = builder.create();
        currentDialog.show();
        // Buttons
        view.findViewById(R.id.btnEnableGps).setOnClickListener(v -> activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        view.findViewById(R.id.btnGrantPermission).setOnClickListener(v -> {
            if (hasLocationPermission(activity)) {
                Toasty.info(activity, "Permission already granted", Toasty.LENGTH_SHORT).show();
                checkAndDismiss(activity, onSuccess);
            } else {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        1001);
            }
        });
        view.findViewById(R.id.btnRetryLocation).setOnClickListener(v -> {
            if (hasLocationPermission(activity) && isLocationEnabled(activity)) {
                dismissDialog();
                if (onSuccess != null) onSuccess.run();
            } else {
                Toasty.warning(activity, "Still missing location permission or GPS", Toasty.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.btnExitLocation).setOnClickListener(v -> {
            dismissDialog();
            if (onCancel != null) onCancel.run();
            else activity.finish();
        });

        currentDialog.setOnDismissListener(dialog -> {
            if (onDismiss != null) onDismiss.run();
        });

        // Auto-dismiss when location becomes available (polling, or via callback)
        // For simplicity, we don't auto-dismiss; user must press Retry.
        // You can add a broadcast receiver for location mode change if needed.
    }

    private static void checkAndDismiss(Activity activity, Runnable onSuccess) {
        if (hasLocationPermission(activity) && isLocationEnabled(activity)) {
            dismissDialog();
            if (onSuccess != null) onSuccess.run();
        } else {
            Toasty.warning(activity, "Location still not available", Toasty.LENGTH_SHORT).show();
        }
    }

    private static void dismissDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }
    }
}