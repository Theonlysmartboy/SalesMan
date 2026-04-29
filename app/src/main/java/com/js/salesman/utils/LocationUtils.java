package com.js.salesman.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationUtils {

    private static final String TAG = "LocationUtils";
    public static final String ERROR_SERVICES_DISABLED = "Location services are disabled. Please enable GPS.";
    public static final String ERROR_PERMISSION_DENIED = "Location permission required.";

    public interface LocationResultCallback {
        void onSuccess(double lat, double lng);
        void onFailure(String error);
    }

    public static void getUserLocation(Context context, Activity activity, LocationResultCallback callback) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            
            Log.d(TAG, "Permissions not granted. Requesting...");
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 1001);
            callback.onFailure(ERROR_PERMISSION_DENIED);
            return;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = false;
        boolean isNetworkEnabled = false;

        try {
            isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            Log.e(TAG, "Error checking location availability", e);
        }

        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.d(TAG, "Location services are disabled.");
            callback.onFailure(ERROR_SERVICES_DISABLED);
            return;
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                Log.d(TAG, "Last location found: " + location.getLatitude() + ", " + location.getLongitude());
                callback.onSuccess(location.getLatitude(), location.getLongitude());
            } else {
                Log.d(TAG, "Last location is null. Requesting new location...");
                requestNewLocationData(fusedLocationClient, callback);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error getting last location", e);
            callback.onFailure(ERROR_SERVICES_DISABLED);
        });
    }

    private static void requestNewLocationData(FusedLocationProviderClient fusedLocationClient, LocationResultCallback callback) {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdates(1)
                .build();

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location lastLocation = locationResult.getLastLocation();
                if (lastLocation != null) {
                    Log.d(TAG, "New location received: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
                    callback.onSuccess(lastLocation.getLatitude(), lastLocation.getLongitude());
                } else {
                    Log.d(TAG, "New location is still null.");
                    callback.onFailure(ERROR_SERVICES_DISABLED);
                }
                fusedLocationClient.removeLocationUpdates(this);
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error requesting location updates", e);
                    callback.onFailure(ERROR_SERVICES_DISABLED);
                });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while requesting updates", e);
            callback.onFailure(ERROR_PERMISSION_DENIED);
        }
    }
}
