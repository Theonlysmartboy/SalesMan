package com.js.salesman.repository;

import android.content.Context;
import android.util.Log;

import com.js.salesman.interfaces.CustomerVisitDao;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.utils.database.AppDatabase;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.workers.TrackingSyncWorker;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CustomerVisitRepository {

    private static final String TAG = "CustomerVisitRepository";

    private static volatile CustomerVisitRepository instance;

    private final Context context;
    private final CustomerVisitDao customerVisitDao;
    private final Executor executor;

    public interface VisitCallback {
        void onSuccess(CustomerVisit visit);
        void onError(String message);
    }

    public interface VisitListCallback {
        void onSuccess(List<CustomerVisit> visits);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    public static CustomerVisitRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (CustomerVisitRepository.class) {
                if (instance == null) {
                    instance = new CustomerVisitRepository(
                            context.getApplicationContext()
                    );
                }
            }
        }

        return instance;
    }

    public CustomerVisitRepository(Context context) {
        this.context = context.getApplicationContext();

        AppDatabase database = AppDatabase.getInstance(this.context);

        this.customerVisitDao = database.customerVisitDao();

        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Starts a new customer visit.
     * The visit is created locally first, making the operation
     * fully offline-safe.
     */
    public void startVisit(String userId, String customerId, String customerType,
            String businessName, double latitude, double longitude, String visitSource,
            String notes, VisitCallback callback) {
        executor.execute(() -> {
            try {
                if (userId == null || userId.trim().isEmpty()) {
                    notifyError(callback, "User ID is required.");
                    return;
                }
                CustomerVisit activeVisit = customerVisitDao.getActiveVisit(userId);
                if (activeVisit != null) {
                    notifyError(callback, "You already have an active customer visit.");
                    return;
                }
                long now = System.currentTimeMillis();
                CustomerVisit visit = new CustomerVisit();
                visit.visitId = UUID.randomUUID().toString();
                visit.userId = userId;
                visit.customerId = customerId;
                visit.customerType = customerType;
                visit.businessName = businessName;
                visit.startedAt = now;
                visit.endedAt = null;
                visit.durationSeconds = 0;
                visit.startLatitude = latitude;
                visit.startLongitude = longitude;
                visit.endLatitude = null;
                visit.endLongitude = null;
                visit.visitStatus = "IN_PROGRESS";
                visit.visitSource = visitSource;
                visit.notes = notes;
                visit.syncStatus = "PENDING";
                visit.createdAt = now;
                visit.updatedAt = now;
                customerVisitDao.insert(visit);
                Log.d(TAG, "Customer visit started: " + visit.visitId);
                LogManager.log(context, "CUSTOMER_VISIT_STARTED",
                        "Started customer visit " +
                                visit.visitId + " for user " + userId);
                if (callback != null) {
                    callback.onSuccess(visit);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting customer visit", e);
                LogManager.logError(context, "CUSTOMER_VISIT_START_ERROR",
                        "Failed to start customer visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    /**
     * Completes an active customer visit.
     * Duration is calculated from startedAt to the completion time.
     */
    public void completeVisit(String visitId, double latitude, double longitude,
            String notes, VisitCallback callback) {
        executor.execute(() -> {
            try {
                CustomerVisit visit = customerVisitDao.getByVisitId(visitId);
                if (visit == null) {
                    notifyError(callback, "Customer visit was not found.");
                    return;
                }
                if (!"IN_PROGRESS".equals(visit.visitStatus)) {
                    notifyError(callback, "Customer visit is no longer active.");
                    return;
                }
                long endedAt = System.currentTimeMillis();
                long durationSeconds = Math.max(0, (endedAt - visit.startedAt) / 1000);
                visit.visitStatus = "COMPLETED";
                visit.endedAt = endedAt;
                visit.durationSeconds = durationSeconds;
                visit.endLatitude = latitude;
                visit.endLongitude = longitude;
                visit.updatedAt = endedAt;
                visit.syncStatus = "PENDING";
                if (notes != null) {
                    visit.notes = notes;
                }
                customerVisitDao.update(visit);
                Log.d(TAG, "Customer visit completed: " +
                                visit.visitId + ", duration=" +
                                durationSeconds + " seconds");
                LogManager.log(context, "CUSTOMER_VISIT_COMPLETED",
                        "Completed customer visit " + visit.visitId +
                                ", duration=" + durationSeconds + " seconds");
                if (callback != null) {
                    callback.onSuccess(visit);
                }
                TrackingSyncWorker.enqueueOneTimeSync(context);
            } catch (Exception e) {
                Log.e(TAG, "Error completing customer visit", e);
                LogManager.logError(context, "CUSTOMER_VISIT_COMPLETE_ERROR",
                        "Failed to complete customer visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    /**
     * Cancels an active customer visit.
     */
    public void cancelVisit(String visitId, String notes, VisitCallback callback) {
        executor.execute(() -> {
            try {
                CustomerVisit visit = customerVisitDao.getByVisitId(visitId);
                if (visit == null) {
                    notifyError(callback, "Customer visit was not found.");
                    return;
                }
                if (!"IN_PROGRESS".equals(visit.visitStatus)) {
                    notifyError(callback, "Customer visit is no longer active.");
                    return;
                }
                long endedAt = System.currentTimeMillis();
                long durationSeconds = Math.max(0, (endedAt - visit.startedAt) / 1000);
                visit.visitStatus = "CANCELLED";
                visit.endedAt = endedAt;
                visit.durationSeconds = durationSeconds;
                visit.updatedAt = endedAt;
                visit.syncStatus = "PENDING";
                if (notes != null) {
                    visit.notes = notes;
                }
                customerVisitDao.update(visit);
                Log.d(TAG, "Customer visit cancelled: " + visit.visitId);
                LogManager.log(context, "CUSTOMER_VISIT_CANCELLED",
                        "Cancelled customer visit " + visit.visitId);
                if (callback != null) {
                    callback.onSuccess(visit);
                }
                TrackingSyncWorker.enqueueOneTimeSync(context);
            } catch (Exception e) {
                Log.e(TAG, "Error cancelling customer visit", e);
                LogManager.logError(context, "CUSTOMER_VISIT_CANCEL_ERROR",
                        "Failed to cancel customer visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    /**
     * Returns the currently active visit for a user.
     */
    public void getActiveVisit(String userId, VisitCallback callback) {
        executor.execute(() -> {
            try {
                CustomerVisit visit = customerVisitDao.getActiveVisit(userId);
                if (callback != null) {
                    callback.onSuccess(visit);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting active customer visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    /**
     * Returns all visits belonging to a user.
     */
    public void getVisitsByUser(String userId, VisitListCallback callback) {
        executor.execute(() -> {
            try {
                List<CustomerVisit> visits = customerVisitDao.getVisitsByUser(userId);
                if (callback != null) {
                    callback.onSuccess(visits);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting customer visits", e);
                if (callback != null) {
                    callback.onError(getSafeErrorMessage(e));
                }
            }
        });
    }

    /**
     * Returns visit history for a registered customer.
     */
    public void getVisitsByCustomer(String customerId, VisitListCallback callback) {
        executor.execute(() -> {
            try {
                List<CustomerVisit> visits = customerVisitDao.getVisitsByCustomer(customerId);
                if (callback != null) {
                    callback.onSuccess(visits);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting customer visit history", e);
                if (callback != null) {
                    callback.onError(getSafeErrorMessage(e));
                }
            }
        });
    }

    /**
     * Links a visit that was initially created for a new customer
     * to an existing registered customer.
     */
    public void linkVisitToCustomer(String visitId, String customerId, SimpleCallback callback) {
        executor.execute(() -> {
            try {
                if (visitId == null || visitId.trim().isEmpty()) {
                    notifyError(callback, "Visit ID is required.");
                    return;
                }
                if (customerId == null || customerId.trim().isEmpty()) {
                    notifyError(callback, "Customer ID is required.");
                    return;
                }
                long now = System.currentTimeMillis();
                int updated = customerVisitDao.linkToCustomer(visitId, customerId, now);
                if (updated == 0) {
                    notifyError(callback, "Customer visit was not found.");
                    return;
                }
                Log.d(TAG, "Linked visit " + visitId + " to customer " + customerId);
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error linking visit to customer", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    /**
     * Returns locally pending visits waiting for synchronization.
     */
    public void getPendingVisits(VisitListCallback callback) {
        executor.execute(() -> {
            try {
                List<CustomerVisit> visits = customerVisitDao.getPendingVisits();
                if (callback != null) {
                    callback.onSuccess(visits);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting pending customer visits", e);
                if (callback != null) {
                    callback.onError(getSafeErrorMessage(e));
                }
            }
        });
    }

    /**
     * Returns the number of customer visits waiting for synchronization.
     */
    public void getPendingVisitCount(CountCallback callback) {
        executor.execute(() -> {
            try {
                int count = customerVisitDao.getPendingVisitCount();
                if (callback != null) {
                    callback.onCount(count);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting pending visit count", e);
                if (callback != null) {
                    callback.onCount(0);
                }
            }
        });
    }

    public interface CountCallback {
        void onCount(int count);
    }

    private void notifyError(VisitCallback callback, String message) {
        if (callback != null) {
            callback.onError(message);
        }
    }

    private void notifyError(SimpleCallback callback, String message) {
        if (callback != null) {
            callback.onError(message);
        }
    }

    private String getSafeErrorMessage(Exception e) {
        if (e == null) {
            return "An unexpected error occurred.";
        }
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "An unexpected error occurred.";
        }
        return message;
    }
}