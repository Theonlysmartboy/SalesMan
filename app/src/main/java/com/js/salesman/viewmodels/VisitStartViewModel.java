package com.js.salesman.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.js.salesman.models.CustomerVisit;
import com.js.salesman.repository.CustomerVisitRepository;
import com.js.salesman.utils.managers.SessionManager;

public class VisitStartViewModel extends AndroidViewModel {

    private final CustomerVisitRepository visitRepository;
    private final SessionManager sessionManager;

    private final MutableLiveData<Boolean> isLoadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<CustomerVisit> visitStartedLiveData = new MutableLiveData<>();

    public VisitStartViewModel(@NonNull Application application) {
        super(application);
        this.visitRepository = CustomerVisitRepository.getInstance(application);
        this.sessionManager = new SessionManager(application);
    }

    public LiveData<Boolean> getIsLoading() { return isLoadingLiveData; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<CustomerVisit> getVisitStarted() { return visitStartedLiveData; }

    public void startVisit(String customerId, String customerType, String businessName,
                        double lat, double lng, String visitSource, String notes) {
        String userId = sessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            errorLiveData.postValue("User ID is missing.");
            return;
        }

        isLoadingLiveData.setValue(true);
        visitRepository.startVisit(userId, customerId, customerType, businessName, lat, lng,
                visitSource, notes, new CustomerVisitRepository.VisitCallback() {
                    @Override
                    public void onSuccess(CustomerVisit visit) {
                        isLoadingLiveData.postValue(false);
                        visitStartedLiveData.postValue(visit);
                    }

                    @Override
                    public void onError(String message) {
                        isLoadingLiveData.postValue(false);
                        errorLiveData.postValue(message);
                    }
                });
    }

    public void scheduleVisit(String customerId, String businessName, long scheduledAt, String notes) {
        String userId = sessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            errorLiveData.postValue("User ID is missing.");
            return;
        }

        isLoadingLiveData.setValue(true);
        visitRepository.scheduleVisit(userId, customerId, businessName, scheduledAt, notes,
                new CustomerVisitRepository.VisitCallback() {
                    @Override
                    public void onSuccess(CustomerVisit visit) {
                        isLoadingLiveData.postValue(false);
                        visitStartedLiveData.postValue(visit);
                    }

                    @Override
                    public void onError(String message) {
                        isLoadingLiveData.postValue(false);
                        errorLiveData.postValue(message);
                    }
                });
    }
}
