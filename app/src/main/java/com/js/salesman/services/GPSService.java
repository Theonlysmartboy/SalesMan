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
import com.js.salesman.repository.TrackingRepository;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.workers.RestartGPSServiceWorker;
import com.js.salesman.workers.TrackingSyncWorker;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class GPSService extends Service {
    private static final String CHANNEL_ID = "gps_tracking_channel";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private TrackingRepository trackingRepository;
    private long lastSendTime = 0;
    private double lastLat = 0.0;
    private double lastLng = 0.0;
    private boolean hasLastLocation = false;

    @Override
    public void onCreate() {
        super.onCreate();
        trackingRepository = new TrackingRepository(this);
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Salesman Tracking Active")
                .setContentText("Your location is being tracked")
                .setSmallIcon(R.drawable.ic_location)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
        if (isOutsideWorkingHours()) {
            scheduleRestart();
            stopSelf();
            return;
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                // Check working hours
                if (isOutsideWorkingHours()) {
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                    scheduleRestart();
                    return;
                }
                SessionManager session = new SessionManager(GPSService.this);
                String userId = session.getUserId();

                // Process locations
                for (Location location : locationResult.getLocations()) {
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();
                    // Check if user has moved at least 5 meters
                    if (hasLastLocation) {
                        float distance = getDistance(lastLat, lastLng, lat, lng);
                        if (distance < 5.0f) {
                            continue; // Ignore – not enough movement
                        }
                    }
                    // Update last known position
                    lastLat = lat;
                    lastLng = lng;
                    hasLastLocation = true;

                    // PERSIST LOCALLY FIRST (Offline-first Outbox Pattern)
                    trackingRepository.saveLocation(userId, lat, lng, location.getTime(), null);

                    long now = System.currentTimeMillis();
                    if (now - lastSendTime >= 300000) { // 5 minutes
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
                .setMinUpdateDistanceMeters(5)    // only if moved 5 meters
                .setMaxUpdateDelayMillis(300000)   // allow batching (5 minutes)
                .build();
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback,
                    Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.d("GPSService", "startLocationUpdates: " + e.getMessage());
            LogManager.logError(this, "GPSService",
                    "Location permission missing", e);
        }
    }

    private void sendBatchToServer() {
        // Enqueue synchronization via outbox mechanism / WorkManager
        if (trackingRepository != null) {
            trackingRepository.syncPendingRecords(null);
        } else {
            TrackingSyncWorker.enqueueOneTimeSync(this);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "GPS Tracking Service",
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ==================== WORKING HOURS LOGIC ====================
    private boolean isOutsideWorkingHours() {
        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int currentMinutes = hour * 60 + minute;
        final int START_MINUTES = 510; // 8:30
        int END_MINUTES;
        if (day == Calendar.SATURDAY) {
            END_MINUTES = 960; // 16:00
        } else if (day >= Calendar.MONDAY && day <= Calendar.FRIDAY) {
            END_MINUTES = 1050; // 17:30
        } else {
            return true; // Sunday
        }
        return currentMinutes < START_MINUTES || currentMinutes >= END_MINUTES;
    }

    private boolean isWorkingDay(int dayOfWeek) {
        return dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY;
    }

    // ==================== RESTART SCHEDULING ====================
    private void scheduleRestart() {
        long nextStart = getNextStartTime();
        long delay = nextStart - System.currentTimeMillis();
        OneTimeWorkRequest restartWork = new OneTimeWorkRequest.Builder(
                RestartGPSServiceWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("gps_restart")
                .build();
        WorkManager.getInstance(this).enqueue(restartWork);
    }

    private long getNextStartTime() {
        Calendar now = Calendar.getInstance();
        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int day = now.get(Calendar.DAY_OF_WEEK);
        final int START_MINUTES = 510; // 8:30
        // If it's a working day and current time is before 8:30, schedule for today
        if (isWorkingDay(day) && currentMinutes < START_MINUTES) {
            now.set(Calendar.HOUR_OF_DAY, 8);
            now.set(Calendar.MINUTE, 30);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            return now.getTimeInMillis();
        }
        // Otherwise, find the next working day at 8:30
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

    // ==================== DISTANCE UTILITY ====================
    private float getDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }
}