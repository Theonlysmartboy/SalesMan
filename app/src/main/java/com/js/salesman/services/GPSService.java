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
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.location.*;
import com.js.salesman.R;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.workers.RestartGPSServiceWorker;

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
        if (!isWithinWorkingHours()) {
            // Schedule restart and stop service
            scheduleRestart();
            stopSelf();
            return;
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                // First, check working hours
                if (!isWithinWorkingHours()) {
                    // Stop location updates
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                    // Schedule restart at next start time
                    scheduleRestart();
                    // Optionally stop the service entirely (or keep it idle)
                    // stopSelf(); // Uncomment if you want to stop the service completely
                    return;
                }

                // Process locations
                for (Location location : locationResult.getLocations()) {
                    if (!isWithinWorkingHours()) {
                        continue;
                    }
                    Map<String, Object> point = new HashMap<>();
                    point.put("latitude", location.getLatitude());
                    point.put("longitude", location.getLongitude());
                    point.put("timestamp", System.currentTimeMillis());
                    locationBuffer.add(point);
                    if (locationBuffer.size() > 500) {
                        sendBatchToServer(); // force send
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastSendTime >= 300000) { // 6 minutes
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
            Log.d("GPSService", "startLocationUpdates: "+e.getMessage());
            LogManager.logError(this, "GPSService", "Location permission missing", e);
        }
    }

    private void sendBatchToServer() {
        if (locationBuffer.isEmpty()) return;
        SessionManager session = new SessionManager(this);
        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", session.getUserId());
        payload.put("locations", new ArrayList<>(locationBuffer));
        ApiInterface api = ApiClient.getClient(this).create(ApiInterface.class);
        api.sendLocation("save-batch", payload).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.d("GPSService", "onResponse: "+response.message());
                        LogManager.logError(GPSService.this, "GPSService",
                            "Batch API error", new Exception(response.message()));
                }
                locationBuffer.clear();
            }
            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.d("GPSService", "onFailure: "+t.getMessage());
                LogManager.logError(GPSService.this, "GPSService",
                        "Batch API error", t);
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
        int minute = now.get(Calendar.MINUTE);
        // Working days: Monday–Friday
        boolean workingDay = (day != Calendar.SATURDAY && day != Calendar.SUNDAY);
        // Convert current time to minutes since midnight
        int currentMinutes = hour * 60 + minute;
        // Start: 8:30 → 8*60+30 = 510
        // End: 17:30 → 17*60+30 = 1050
        boolean workingHour = (currentMinutes >= 510 && currentMinutes < 1050);
        return workingDay && workingHour;
    }

    private void scheduleRestart() {
        long nextStart = getNextStartTime();
        long delay = nextStart - System.currentTimeMillis();
        // Create a one-time work request
        OneTimeWorkRequest restartWork = new OneTimeWorkRequest.Builder(RestartGPSServiceWorker.class)
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("gps_restart")
                .build();
        WorkManager.getInstance(this).enqueue(restartWork);
    }

    private long getNextStartTime() {
        Calendar now = Calendar.getInstance();
        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int day = now.get(Calendar.DAY_OF_WEEK);
        // Start time = 8:30 (510 minutes)
        final int START_MINUTES = 510;
        // If it's a working day and current time < 8:30, schedule for today 8:30
        if (isWorkingDay(day) && currentMinutes < START_MINUTES) {
            now.set(Calendar.HOUR_OF_DAY, 8);
            now.set(Calendar.MINUTE, 30);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            return now.getTimeInMillis();
        }
        // Otherwise, find the next working day at 8:30
        int daysToAdd = 1;
        while (true) {
            now.add(Calendar.DAY_OF_YEAR, 1);
            int newDay = now.get(Calendar.DAY_OF_WEEK);
            if (isWorkingDay(newDay)) {
                now.set(Calendar.HOUR_OF_DAY, 8);
                now.set(Calendar.MINUTE, 30);
                now.set(Calendar.SECOND, 0);
                now.set(Calendar.MILLISECOND, 0);
                return now.getTimeInMillis();
            }
        }
    }

    private boolean isWorkingDay(int dayOfWeek) {
        return dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY;
    }
}