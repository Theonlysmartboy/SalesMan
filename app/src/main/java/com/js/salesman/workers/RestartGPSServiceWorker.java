package com.js.salesman.workers;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.js.salesman.services.GPSService;

public class RestartGPSServiceWorker extends Worker{
    public RestartGPSServiceWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Start the GPS service again
        Intent serviceIntent = new Intent(getApplicationContext(), GPSService.class);
        getApplicationContext().startService(serviceIntent);
        return Result.success();
    }
}
