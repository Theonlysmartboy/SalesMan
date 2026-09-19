package com.js.salesman.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.js.salesman.repository.ProductRepository;

import java.util.concurrent.TimeUnit;

public class ProductSyncWorker extends Worker {
    private static final String TAG = "ProductSyncWorker";

    public ProductSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Background product sync started");
        ProductRepository repository = new ProductRepository(getApplicationContext());
        
        final boolean[] success = {false};
        final String[] error = {null};
        final Object lock = new Object();

        repository.syncProducts(new ProductRepository.SyncCallback() {
            @Override
            public void onSuccess() {
                synchronized (lock) {
                    success[0] = true;
                    lock.notify();
                }
            }

            @Override
            public void onError(String message) {
                synchronized (lock) {
                    success[0] = false;
                    error[0] = message;
                    lock.notify();
                }
            }
        });

        synchronized (lock) {
            try {
                // Wait for sync to complete (max 5 minutes)
                lock.wait(5 * 60 * 1000);
            } catch (InterruptedException e) {
                return Result.retry();
            }
        }

        if (success[0]) {
            return Result.success();
        } else {
            return Result.retry();
        }
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ProductSyncWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag("ProductSync")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ProductSync",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }
}
