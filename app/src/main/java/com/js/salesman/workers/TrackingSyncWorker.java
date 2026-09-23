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

import java.util.concurrent.TimeUnit;

public class TrackingSyncWorker extends Worker {
    private static final String TAG = "TrackingSyncWorker";
    public static final String WORK_NAME_PERIODIC = "TrackingSync_Periodic";
    public static final String WORK_NAME_ONETIME = "TrackingSync_OneTime";

    public TrackingSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Background tracking sync started");
        TrackingRepository repository = new TrackingRepository(getApplicationContext());

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

        if (success[0]) {
            Log.d(TAG, "Tracking sync completed successfully.");
            return Result.success();
        } else {
            if (isTransientError[0]) {
                Log.w(TAG, "Tracking sync failed with transient error (" + errorMsg[0] + "), scheduling retry.");
                return Result.retry();
            } else {
                Log.e(TAG, "Tracking sync failed with permanent error (" + errorMsg[0] + ").");
                return Result.failure();
            }
        }
    }

    public static void enqueueOneTimeSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TrackingSyncWorker.class)
                .setConstraints(constraints)
                .addTag(WORK_NAME_ONETIME)
                .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_NAME_ONETIME,
                ExistingWorkPolicy.REPLACE,
                request
        );
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
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }
}
