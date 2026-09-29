package com.js.salesman.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.js.salesman.repository.TrackingRepository;
import com.js.salesman.utils.managers.LogManager;

import java.util.concurrent.TimeUnit;

public class TrackingSyncWorker extends Worker {
    private static final String TAG = "TrackingSyncWorker";
    public static final String WORK_NAME_PERIODIC = "TrackingSync_Periodic";
    public static final String WORK_NAME_ONETIME = "TrackingSync_OneTime";
    private static volatile long lastLocationSavedAt = 0;
    public static void markLocationSaved() {
        lastLocationSavedAt = System.currentTimeMillis();
    }
    public static long getLastLocationSavedAt() {
        return lastLocationSavedAt;
    }

    public TrackingSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Background tracking sync started");
        LogManager.log(getApplicationContext(), "TRACKING_SYNC_STARTED",
                "Background tracking sync started");
        TrackingRepository repository = TrackingRepository.getInstance(getApplicationContext());
        boolean lastSyncSuccessful;
        boolean lastSyncTransient;
        String[] lastErrorMsg = {null};
        while (true) {
            long checkTime = System.currentTimeMillis();
            final boolean[] success = {false};
            final boolean[] isTransientError = {true};
            final String[] errorMsg = {null};
            final Object lock = new Object();
            repository.syncPendingRecords(new TrackingRepository.SyncCallback() {
                @Override
                public void onSuccess(int syncedCount) {
                    synchronized (lock) {
                        success[0] = true;
                        lock.notify();
                    }
                }
                @Override
                public void onError(String message, boolean isTransient) {
                    synchronized (lock) {
                        success[0] = false;
                        isTransientError[0] = isTransient;
                        errorMsg[0] = message;
                        lock.notify();
                    }
                }
            });
            synchronized (lock) {
                try {
                    // Wait for sync to finish (timeout 3 minutes)
                    lock.wait(3 * 60 * 1000);
                } catch (InterruptedException e) {
                    return Result.retry();
                }
            }
            lastSyncSuccessful = success[0];
            lastSyncTransient = isTransientError[0];
            lastErrorMsg[0] = errorMsg[0];
            // Exit shutdown check: if no location was saved during this iteration, break
            if (getLastLocationSavedAt() <= checkTime) {
                break;
            }
        }
        if (lastSyncSuccessful) {
            Log.d(TAG, "Tracking sync completed successfully.");
            LogManager.log(getApplicationContext(), "TRACKING_SYNC_SUCCESS",
                    "Tracking sync completed successfully");
            return Result.success();
        } else {
            if (lastSyncTransient) {
                Log.w(TAG, "Tracking sync failed with transient error (" + lastErrorMsg[0] + ")," +
                        " scheduling retry.");
                LogManager.log(getApplicationContext(), "TRACKING_SYNC_RETRY",
                        "Tracking sync failed with transient error (" + lastErrorMsg[0] + ")," +
                                " scheduling retry.");
                return Result.retry();
            } else {
                Log.e(TAG, "Tracking sync failed with permanent error (" + lastErrorMsg[0] + ").");
                LogManager.log(getApplicationContext(), "TRACKING_SYNC_ERROR",
                        "Tracking sync failed with permanent error (" + lastErrorMsg[0] + ").");
                return Result.failure();
            }
        }
    }

    public static void enqueueOneTimeSync(Context context) {
        markLocationSaved();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TrackingSyncWorker.class)
                .setConstraints(constraints)
                .addTag(WORK_NAME_ONETIME)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_NAME_ONETIME, ExistingWorkPolicy.KEEP, request);
    }

    public static void schedulePeriodicSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                TrackingSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(WORK_NAME_PERIODIC)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
