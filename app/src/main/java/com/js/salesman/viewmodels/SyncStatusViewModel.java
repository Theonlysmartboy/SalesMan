package com.js.salesman.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.ExistingWorkPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.js.salesman.interfaces.CustomerVisitDao;
import com.js.salesman.interfaces.PendingOrderDao;
import com.js.salesman.interfaces.TrackingDao;
import com.js.salesman.utils.database.AppDatabase;
import com.js.salesman.workers.SyncCoordinatorWorker;
import com.js.salesman.workers.TrackingSyncWorker;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SyncStatusViewModel extends AndroidViewModel {

    private static final String TAG = "SyncStatusViewModel";

    private final CustomerVisitDao visitDao;
    private final PendingOrderDao orderDao;
    private final TrackingDao trackingDao;
    private final Executor executor;
    private final MutableLiveData<Integer> pendingVisitsLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> failedVisitsLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> pendingOrdersLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> failedOrdersLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> pendingTrackingLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> failedTrackingLiveData = new MutableLiveData<>(0);

    public SyncStatusViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        this.visitDao = db.customerVisitDao();
        this.orderDao = db.pendingOrderDao();
        this.trackingDao = db.trackingDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<Integer> getPendingVisits() { return pendingVisitsLiveData; }
    public LiveData<Integer> getFailedVisits() { return failedVisitsLiveData; }
    public LiveData<Integer> getPendingOrders() { return pendingOrdersLiveData; }
    public LiveData<Integer> getFailedOrders() { return failedOrdersLiveData; }
    public LiveData<Integer> getPendingTracking() { return pendingTrackingLiveData; }
    public LiveData<Integer> getFailedTracking() { return failedTrackingLiveData; }

    public LiveData<List<WorkInfo>> getSyncWorkInfo() {
        return WorkManager.getInstance(getApplication())
                .getWorkInfosForUniqueWorkLiveData(SyncCoordinatorWorker.WORK_NAME);
    }

    public void refreshCounts() {
        executor.execute(() -> {
            pendingVisitsLiveData.postValue(visitDao.getPendingVisitCount());
            failedVisitsLiveData.postValue(visitDao.getFailedVisitCount());
            pendingOrdersLiveData.postValue(orderDao.getPendingCount());
            failedOrdersLiveData.postValue(orderDao.getFailedCount());
            pendingTrackingLiveData.postValue(trackingDao.getPendingCount());
            failedTrackingLiveData.postValue(trackingDao.getFailedCount());
        });
    }

    public void triggerSyncNow() {
        executor.execute(() -> {
            try {
                visitDao.resetFailedToPending();
                trackingDao.resetFailedToPending();
                orderDao.resetFailedToPending();
            } catch (Exception e) {
                Log.w(TAG, "Error resetting failed status to pending", e);
            }
            SyncCoordinatorWorker.enqueueManualSync(getApplication());
            TrackingSyncWorker.enqueueOneTimeSync(getApplication(), ExistingWorkPolicy.REPLACE);
            refreshCounts();
        });
    }
}
