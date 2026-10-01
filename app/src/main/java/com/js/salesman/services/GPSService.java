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
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import com.js.salesman.R;
import com.js.salesman.interfaces.CustomerVisitDao;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.repository.TrackingRepository;
import com.js.salesman.utils.database.AppDatabase;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.workers.RestartGPSServiceWorker;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GPSService extends Service {
    private static final String TAG = "GPSService";
    private static final String CHANNEL_ID = "gps_tracking_channel";
    // ==================== TRACKING CONFIGURATION ====================
    private static final long TRACKING_INTERVAL_MS = 180_000L;
    private static final long MIN_UPDATE_INTERVAL_MS = 180_000L;
    private static final long MAX_UPDATE_DELAY_MS = 180_000L;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private TrackingRepository trackingRepository;
    private CustomerVisitDao visitDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ==================== SERVICE LIFECYCLE ====================
    @Override
    public void onCreate() {
        super.onCreate();
        trackingRepository = new TrackingRepository(this);
        visitDao = AppDatabase.getInstance(this).customerVisitDao();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Cypos Salesman Tracking Active")
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
                if (isOutsideWorkingHours()) {
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                    scheduleRestart();
                    stopSelf();
                    return;
                }

                SessionManager session = new SessionManager(GPSService.this);
                String userId = session.getUserId();
                if (userId == null || userId.trim().isEmpty()) {
                    Log.w(TAG, "Location update skipped: user ID is unavailable.");
                    LogManager.log(GPSService.this, "LOCATION_UPDATE",
                            "USER_ID_UNAVAILABLE");
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                    stopSelf();
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    if (location == null) {
                        continue;
                    }
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();
                    if (!isValidLocation(lat, lng)) {
                        Log.w(TAG, "Invalid location received. Skipping.");
                        LogManager.log(GPSService.this, "LOCATION_UPDATE",
                                "INVALID_LOCATION");
                        continue;
                    }

                    // Dynamically resolve active visit_id from Room for EVERY location point
                    executor.execute(() -> {
                        CustomerVisit activeVisit = visitDao.getActiveVisit(userId);
                        String activeVisitId = activeVisit != null ? activeVisit.visitId : null;

                        trackingRepository.saveLocation(userId, activeVisitId, lat, lng,
                                location.getTime(), null);
                        Log.d(TAG, "Location saved: lat=" + lat + ", lng=" + lng +
                                ", visitId=" + activeVisitId + ", time=" + location.getTime());
                        LogManager.log(GPSService.this, "LOCATION_UPDATE",
                                "Location saved: lat=" + lat + ", lng=" + lng +
                                        ", visitId=" + activeVisitId + ", time=" + location.getTime());
                    });
                }
            }
        };
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest
                .Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, TRACKING_INTERVAL_MS)
                        .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
                        .setMinUpdateDistanceMeters(0)
                        .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)
                        .build();
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Location tracking started. Interval: 3 minutes.");
            LogManager.log(this, "LOCATION_TRACKING", "Location tracking " +
                    "started. Interval: 3 minutes.");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing.", e);
            LogManager.logError(this, TAG, "Location permission missing", e);
        }
    }

    private boolean isValidLocation(double lat, double lng) {
        return lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0 &&
                !(lat == 0.0 && lng == 0.0);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        "GPS Tracking Service", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        executor.shutdown();
        Log.d(TAG, "GPS tracking service destroyed.");
        LogManager.log(this, "LOCATION_TRACKING",
                "GPS tracking service destroyed.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isOutsideWorkingHours() {
        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int currentMinutes = hour * 60 + minute;
        final int START_MINUTES = 510; // 8:30 AM
        int END_MINUTES;
        if (day == Calendar.SATURDAY) {
            END_MINUTES = 960; // 4:00 PM
        } else if (day >= Calendar.MONDAY && day <= Calendar.FRIDAY) {
            END_MINUTES = 1050; // 5:30 PM
        } else {
            return true; // Sunday
        }
        return currentMinutes < START_MINUTES || currentMinutes >= END_MINUTES;
    }

    private boolean isWorkingDay(int dayOfWeek) {
        return dayOfWeek != Calendar.SUNDAY;
    }

    private void scheduleRestart() {
        long nextStart = getNextStartTime();
        long delay = Math.max(0L, nextStart - System.currentTimeMillis());
        OneTimeWorkRequest restartWork = new OneTimeWorkRequest
                .Builder(RestartGPSServiceWorker.class)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .addTag("gps_restart")
                        .build();
        WorkManager.getInstance(this).enqueueUniqueWork("GPSService_Restart",
                ExistingWorkPolicy.REPLACE, restartWork);
        Log.d(TAG, "GPS service restart scheduled in "
        + TimeUnit.MILLISECONDS.toMinutes(delay) + " minutes.");
        LogManager.log(this, "LOCATION_TRACKING",
                "GPS service restart scheduled in "
                + TimeUnit.MILLISECONDS.toMinutes(delay) + " minutes.");
    }

    private long getNextStartTime() {
        Calendar now = Calendar.getInstance();
        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int day = now.get(Calendar.DAY_OF_WEEK);
        final int START_MINUTES = 510; // 8:30 AM
        if (isWorkingDay(day) && currentMinutes < START_MINUTES) {
            now.set(Calendar.HOUR_OF_DAY, 8);
            now.set(Calendar.MINUTE, 30);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            return now.getTimeInMillis();
        }
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
}
