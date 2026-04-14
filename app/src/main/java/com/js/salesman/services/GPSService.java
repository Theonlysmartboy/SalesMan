package com.js.salesman.services;

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
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.session.SessionManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GPSService extends Service {
    private static final String CHANNEL_ID = "gps_tracking_channel";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private final List<Map<String, Object>> locationBuffer = new ArrayList<>();
    private long lastSendTime = 0;

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
                        continue;
                    }
                    Log.d("GPSService", "Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude());
                    Map<String, Object> point = new HashMap<>();
                    point.put("latitude", location.getLatitude());
                    point.put("longitude", location.getLongitude());
                    point.put("timestamp", System.currentTimeMillis());
                    locationBuffer.add(point);
                    long now = System.currentTimeMillis();
                    if (now - lastSendTime >= 360000) { // 6 minutes
                        sendBatchToServer();
                        lastSendTime = now;
                    }
                }
            }
        };
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, 300000) // 5 minutes
                .setMinUpdateIntervalMillis(120000) // fastest 2 minute
                .setMinUpdateDistanceMeters(0)    // only if moved 5 meters
                .setMaxUpdateDelayMillis(360000)   // allow batching (6 minutes)
                .build();
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback,
                    Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e("GPSService", "Location permission missing: " + e.getMessage());
        }
    }

    private void sendBatchToServer() {
        if (locationBuffer.isEmpty()) return;
        SessionManager session = new SessionManager(this);
        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", session.getUserId());
        payload.put("locations", new ArrayList<>(locationBuffer));
        ApiInterface api = ApiClient.getClient(this).create(ApiInterface.class);
        Log.d("GPSService", "Batch size before sending: " + locationBuffer.size());
        api.sendLocation("save-batch", payload).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                Log.d("GPSService", "Batch sent: " + response.code());
                locationBuffer.clear();
            }
            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e("GPSService", "Batch API error: " + t.getMessage());
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