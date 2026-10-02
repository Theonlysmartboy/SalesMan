package com.js.salesman.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.js.salesman.models.CustomerVisit;
import com.js.salesman.repository.CustomerVisitRepository;
import com.js.salesman.utils.managers.SessionManager;

import java.util.List;

public class ScheduledVisitsViewModel extends AndroidViewModel {

    private final CustomerVisitRepository visitRepository;
    private final SessionManager sessionManager;

    private final MutableLiveData<List<CustomerVisit>> scheduledVisitsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> operationSuccessLiveData = new MutableLiveData<>();

    public ScheduledVisitsViewModel(@NonNull Application application) {
        super(application);
        this.visitRepository = CustomerVisitRepository.getInstance(application);
        this.sessionManager = new SessionManager(application);
    }

    public LiveData<List<CustomerVisit>> getScheduledVisits() { return scheduledVisitsLiveData; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<Boolean> getOperationSuccess() { return operationSuccessLiveData; }

    public void loadScheduledVisits() {
        String userId = sessionManager.getUserId();
        if (userId == null) return;
        visitRepository.getScheduledVisits(userId, new CustomerVisitRepository.VisitListCallback() {
            @Override
            public void onSuccess(List<CustomerVisit> visits) {
                scheduledVisitsLiveData.postValue(visits);
            }

            @Override
            public void onError(String message) {
                errorLiveData.postValue(message);
            }
        });
    }

    public void startScheduledVisit(String visitId, double lat, double lng) {
        visitRepository.startScheduledVisit(visitId, lat, lng, new CustomerVisitRepository
                .VisitCallback() {
            @Override
            public void onSuccess(CustomerVisit visit) {
                operationSuccessLiveData.postValue(true);
                loadScheduledVisits();
            }

            @Override
            public void onError(String message) {
                errorLiveData.postValue(message);
            }
        });
    }

    public void postponeVisit(String visitId, long newScheduledAt, String notes) {
        visitRepository.postponeVisit(visitId, newScheduledAt, notes,
                new CustomerVisitRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                operationSuccessLiveData.postValue(true);
                loadScheduledVisits();
            }

            @Override
            public void onError(String message) {
                errorLiveData.postValue(message);
            }
        });
    }

    public void cancelVisit(String visitId, String reason) {
        visitRepository.cancelVisit(visitId, reason, new CustomerVisitRepository.VisitCallback() {
            @Override
            public void onSuccess(CustomerVisit visit) {
                operationSuccessLiveData.postValue(true);
                loadScheduledVisits();
            }

            @Override
            public void onError(String message) {
                errorLiveData.postValue(message);
            }
        });
    }
}
