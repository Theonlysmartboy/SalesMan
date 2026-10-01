package com.js.salesman.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.js.salesman.models.CustomerVisit;
import com.js.salesman.repository.CustomerVisitRepository;
import com.js.salesman.utils.managers.SessionManager;

public class ActiveVisitViewModel extends AndroidViewModel {

    private final CustomerVisitRepository visitRepository;
    private final SessionManager sessionManager;
    private final MutableLiveData<CustomerVisit> activeVisitLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> actionCompletedLiveData = new MutableLiveData<>();

    public ActiveVisitViewModel(@NonNull Application application) {
        super(application);
        this.visitRepository = CustomerVisitRepository.getInstance(application);
        this.sessionManager = new SessionManager(application);
    }

    public LiveData<CustomerVisit> getActiveVisit() { return activeVisitLiveData; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<Boolean> getActionCompleted() { return actionCompletedLiveData; }

    public void loadActiveVisit() {
        String userId = sessionManager.getUserId();
        if (userId == null) return;
        visitRepository.getActiveVisit(userId, new CustomerVisitRepository.VisitCallback() {
            @Override
            public void onSuccess(CustomerVisit visit) {
                activeVisitLiveData.postValue(visit);
            }
            @Override
            public void onError(String message) {
                errorLiveData.postValue(message);
            }
        });
    }

    public void endVisit(String visitId, double lat, double lng, String notes) {
        visitRepository.completeVisit(visitId, lat, lng, notes, new CustomerVisitRepository.VisitCallback() {
            @Override
            public void onSuccess(CustomerVisit visit) {
                actionCompletedLiveData.postValue(true);
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
                actionCompletedLiveData.postValue(true);
            }
            @Override
            public void onError(String message) {
                errorLiveData.postValue(message);
            }
        });
    }
}
