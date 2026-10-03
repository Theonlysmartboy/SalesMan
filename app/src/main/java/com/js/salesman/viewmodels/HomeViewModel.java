package com.js.salesman.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.js.salesman.interfaces.CustomerVisitDao;
import com.js.salesman.interfaces.PendingOrderDao;
import com.js.salesman.interfaces.TrackingDao;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.repository.CustomerVisitRepository;
import com.js.salesman.utils.database.AppDatabase;
import com.js.salesman.utils.managers.SessionManager;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class HomeViewModel extends AndroidViewModel {

    private final CustomerVisitDao visitDao;
    private final PendingOrderDao orderDao;
    private final TrackingDao trackingDao;
    private final SessionManager sessionManager;
    private final CustomerVisitRepository visitRepository;
    private final Executor executor;
    private final MutableLiveData<CustomerVisit> activeVisitLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> pendingSyncCountLiveData = new MutableLiveData<>();

    private final CustomerVisitRepository.ActiveVisitChangeListener visitChangeListener = visit -> {
        activeVisitLiveData.postValue(visit);
        refreshActiveVisit();
        refreshPendingSyncCount();
    };

    public HomeViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        this.visitDao = db.customerVisitDao();
        this.orderDao = db.pendingOrderDao();
        this.trackingDao = db.trackingDao();
        this.sessionManager = new SessionManager(application);
        this.visitRepository = CustomerVisitRepository.getInstance(application);
        this.executor = Executors.newSingleThreadExecutor();

        this.visitRepository.addActiveVisitChangeListener(visitChangeListener);
    }

    public LiveData<CustomerVisit> getActiveVisit() {
        refreshActiveVisit();
        return activeVisitLiveData;
    }

    public LiveData<Integer> getPendingSyncCount() {
        refreshPendingSyncCount();
        return pendingSyncCountLiveData;
    }

    public void refreshActiveVisit() {
        executor.execute(() -> {
            String userId = sessionManager.getUserId();
            if (userId != null) {
                CustomerVisit active = visitDao.getActiveVisit(userId);
                activeVisitLiveData.postValue(active);
            } else {
                activeVisitLiveData.postValue(null);
            }
        });
    }

    public void refreshPendingSyncCount() {
        executor.execute(() -> {
            int visitsPending = visitDao.getPendingVisitCount();
            int ordersPending = orderDao.getPendingCount();
            int trackingPending = trackingDao.getPendingCount();
            pendingSyncCountLiveData.postValue(visitsPending + ordersPending + trackingPending);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        visitRepository.removeActiveVisitChangeListener(visitChangeListener);
    }
}
