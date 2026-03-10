package com.js.salesman.api.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.*;
import com.js.salesman.R;
import com.js.salesman.api.client.ApiClient;
import com.js.salesman.session.SessionManager;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GPSService extends Service {
    private static final String CHANNEL_ID = "gps_tracking_channel";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Salesman Tracking Active")
                .setContentText("Your location is being tracked")
                .setSmallIcon(R.drawable.ic_location)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (!isWithinWorkingHours()) {
                        Log.d("GPSService", "Outside working hours, skipping location");
                        return;
                    }
                    Log.d("GPSService", "Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude());
                    sendLocationToServer(location);
                }
            }
        };
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000) // interval 5000ms
                .setMinUpdateIntervalMillis(3000) // fastest interval
                .setMaxUpdateDelayMillis(1000) // optional, max delay
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e("GPSService", "Location permission missing: " + e.getMessage());
        }
    }

    private void sendLocationToServer(Location location) {
        SessionManager session = new SessionManager(this);
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", session.getUserId());
        data.put("latitude", location.getLatitude());
        data.put("longitude", location.getLongitude());
        data.put("timestamp", System.currentTimeMillis());
        ApiService api = ApiClient.getClient(this).create(ApiService.class);
        api.sendLocation("save", data).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                Log.d("GPSService", "Server response: " + response.code());
            }
            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e("GPSService", "API error: " + t.getMessage());
            }
        });
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "GPS Tracking Service",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if(manager != null) manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private boolean isWithinWorkingHours() {
        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        // Working days: Monday–Friday
        boolean workingDay =
                day != Calendar.SATURDAY &&
                        day != Calendar.SUNDAY;
        // Working hours: 08:00 - 17:00
        boolean workingHour =
                hour >= 8 && hour < 17;
        return workingDay && workingHour;
    }
}