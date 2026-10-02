package com.js.salesman.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.interfaces.CustomerVisitDao;
import com.js.salesman.interfaces.TrackingDao;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.models.TrackingRecord;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.database.AppDatabase;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.workers.SyncCoordinatorWorker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class CustomerVisitRepository {

    private static final String TAG = "CustomerVisitRepository";

    private static volatile CustomerVisitRepository instance;

    private final Context context;
    private final CustomerVisitDao customerVisitDao;
    private final TrackingDao trackingDao;
    private final ApiInterface api;
    private final Executor executor;
    private final Handler mainHandler;

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

    public interface CountCallback {
        void onCount(int count);
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
        this.trackingDao = database.trackingDao();
        this.api = ApiClient.getApi(this.context);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public String getActiveVisitIdOrNull(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        CustomerVisit active = customerVisitDao.getActiveVisit(userId);
        return active != null ? (active.serverId != null && !active.serverId.isEmpty() ? active.serverId : active.visitId) : null;
    }

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
                    String activeName = activeVisit.businessName != null ? activeVisit.businessName : "another customer";
                    notifyError(callback, "You have an active visit at " + activeName + ". End or cancel it first.");
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

                Log.d(TAG, "Customer visit created locally: " + visit.visitId);
                LogManager.log(context, "CUSTOMER_VISIT_STARTED_LOCAL", "Created visit " + visit.visitId);

                // Initial tracking record for visit start
                TrackingRecord startTracking = new TrackingRecord(userId, visit.visitId, latitude, longitude, now);
                trackingDao.insert(startTracking);

                // Immediate Online Submission if connected
                if (NetworkUtil.isNetworkAvailable(context)) {
                    try {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                        Map<String, Object> body = new HashMap<>();
                        body.put("visit_id", visit.visitId);
                        body.put("user_id", userId);
                        body.put("customer_id", customerId);
                        body.put("customer_type", customerType != null ? customerType : "REGISTERED");
                        body.put("business_name", businessName);
                        body.put("started_at", dateFormat.format(new Date(now)));
                        body.put("start_latitude", latitude);
                        body.put("start_longitude", longitude);
                        body.put("visit_source", visitSource != null ? visitSource : "GPS_DETECTED");
                        body.put("notes", notes);

                        Response<Map<String, Object>> response = api.createVisit("create", body).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> resBody = response.body();
                            if (Boolean.TRUE.equals(resBody.get("success"))) {
                                String serverId = extractServerId(resBody);
                                String effectiveServerId = serverId != null ? serverId : visit.visitId;
                                customerVisitDao.markSyncedWithServerId(visit.visitId, effectiveServerId, System.currentTimeMillis());
                                visit.serverId = effectiveServerId;
                                visit.syncStatus = "SYNCED";

                                // Immediate sync of initial tracking point
                                submitInitialTracking(userId, effectiveServerId, startTracking);
                            } else {
                                SyncCoordinatorWorker.enqueue(context);
                            }
                        } else {
                            SyncCoordinatorWorker.enqueue(context);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Immediate online visit submission failed, queued for background sync", e);
                        SyncCoordinatorWorker.enqueue(context);
                    }
                } else {
                    SyncCoordinatorWorker.enqueue(context);
                }

                notifySuccess(callback, visit);
            } catch (Exception e) {
                Log.e(TAG, "Error starting customer visit", e);
                LogManager.logError(context, "CUSTOMER_VISIT_START_ERROR", "Failed to start customer visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    private void submitInitialTracking(String userId, String visitId, TrackingRecord trackingRecord) {
        try {
            Map<String, Object> trackingPayload = new HashMap<>();
            trackingPayload.put("user_id", userId);
            trackingPayload.put("batch_id", UUID.randomUUID().toString());

            List<Map<String, Object>> locs = new ArrayList<>();
            Map<String, Object> point = new HashMap<>();
            point.put("tracking_id", trackingRecord.getTrackingId());
            point.put("visit_id", visitId);
            point.put("latitude", trackingRecord.getLatitude());
            point.put("longitude", trackingRecord.getLongitude());
            point.put("timestamp", trackingRecord.getTimestamp());
            locs.add(point);
            trackingPayload.put("locations", locs);

            Response<Void> res = api.sendLocation("save-batch", trackingPayload).execute();
            if (res.isSuccessful()) {
                trackingRecord.setStatus("SYNCED");
                trackingDao.update(trackingRecord);
            }
        } catch (Exception e) {
            Log.w(TAG, "Immediate tracking sync warning", e);
        }
    }

    public void scheduleVisit(String userId, String customerId, String businessName,
            long scheduledAt, String notes, VisitCallback callback) {
        executor.execute(() -> {
            try {
                if (userId == null || userId.trim().isEmpty()) {
                    notifyError(callback, "User ID is required.");
                    return;
                }
                if (customerId == null || customerId.trim().isEmpty()) {
                    notifyError(callback, "Registered customer is required for scheduling.");
                    return;
                }
                long now = System.currentTimeMillis();
                CustomerVisit visit = new CustomerVisit();
                visit.visitId = UUID.randomUUID().toString();
                visit.userId = userId;
                visit.customerId = customerId;
                visit.customerType = "REGISTERED";
                visit.businessName = businessName;
                visit.startedAt = scheduledAt;
                visit.scheduledAt = scheduledAt;
                visit.visitStatus = "SCHEDULED";
                visit.visitSource = "GPS_DETECTED";
                visit.notes = notes;
                visit.syncStatus = "PENDING";
                visit.createdAt = now;
                visit.updatedAt = now;
                customerVisitDao.insert(visit);

                if (NetworkUtil.isNetworkAvailable(context)) {
                    try {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                        Map<String, Object> body = new HashMap<>();
                        body.put("visit_id", visit.visitId);
                        body.put("user_id", userId);
                        body.put("customer_id", customerId);
                        body.put("business_name", businessName);
                        body.put("scheduled_at", dateFormat.format(new Date(scheduledAt)));
                        body.put("notes", notes);

                        Response<Map<String, Object>> response = api.scheduleVisit("schedule", body).execute();
                        if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().get("success"))) {
                            String serverId = extractServerId(response.body());
                            customerVisitDao.markSyncedWithServerId(visit.visitId, serverId != null ? serverId : visit.visitId, System.currentTimeMillis());
                            visit.serverId = serverId;
                            visit.syncStatus = "SYNCED";
                        } else {
                            SyncCoordinatorWorker.enqueue(context);
                        }
                    } catch (Exception e) {
                        SyncCoordinatorWorker.enqueue(context);
                    }
                } else {
                    SyncCoordinatorWorker.enqueue(context);
                }

                notifySuccess(callback, visit);
            } catch (Exception e) {
                Log.e(TAG, "Error scheduling customer visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    public void startScheduledVisit(String visitId, double latitude, double longitude,
            VisitCallback callback) {
        executor.execute(() -> {
            try {
                CustomerVisit visit = customerVisitDao.getByVisitId(visitId);
                if (visit == null) {
                    notifyError(callback, "Scheduled visit not found.");
                    return;
                }
                CustomerVisit activeVisit = customerVisitDao.getActiveVisit(visit.userId);
                if (activeVisit != null) {
                    String activeName = activeVisit.businessName != null ? activeVisit.businessName : "another customer";
                    notifyError(callback, "You have an active visit at " + activeName + ". End or cancel it first.");
                    return;
                }
                long now = System.currentTimeMillis();
                customerVisitDao.startScheduledVisit(visitId, now, latitude, longitude, now);
                visit.visitStatus = "IN_PROGRESS";
                visit.startedAt = now;
                visit.startLatitude = latitude;
                visit.startLongitude = longitude;
                visit.syncStatus = "PENDING";

                if (NetworkUtil.isNetworkAvailable(context)) {
                    try {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                        Map<String, Object> body = new HashMap<>();
                        body.put("started_at", dateFormat.format(new Date(now)));
                        body.put("start_latitude", latitude);
                        body.put("start_longitude", longitude);
                        body.put("notes", visit.notes);

                        String targetId = visit.serverId != null ? visit.serverId : visit.visitId;
                        Response<Map<String, Object>> response = api.startVisit("start", targetId, body).execute();
                        if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().get("success"))) {
                            customerVisitDao.markAsSynced(visit.visitId, System.currentTimeMillis());
                            visit.syncStatus = "SYNCED";
                        } else {
                            SyncCoordinatorWorker.enqueue(context);
                        }
                    } catch (Exception e) {
                        SyncCoordinatorWorker.enqueue(context);
                    }
                } else {
                    SyncCoordinatorWorker.enqueue(context);
                }

                notifySuccess(callback, visit);
            } catch (Exception e) {
                Log.e(TAG, "Error starting scheduled visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    public void postponeVisit(String visitId, long newScheduledAt, String notes, SimpleCallback callback) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                int updated = customerVisitDao.postponeVisit(visitId, newScheduledAt, notes, now);
                if (updated == 0) {
                    notifyError(callback, "Visit not found.");
                    return;
                }

                CustomerVisit visit = customerVisitDao.getByVisitId(visitId);
                if (NetworkUtil.isNetworkAvailable(context) && visit != null) {
                    try {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                        Map<String, Object> body = new HashMap<>();
                        body.put("scheduled_at", dateFormat.format(new Date(newScheduledAt)));
                        body.put("notes", notes);

                        String targetId = visit.serverId != null ? visit.serverId : visit.visitId;
                        Response<Map<String, Object>> response = api.postponeVisit("postpone", targetId, body).execute();
                        if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().get("success"))) {
                            customerVisitDao.markAsSynced(visit.visitId, System.currentTimeMillis());
                        } else {
                            SyncCoordinatorWorker.enqueue(context);
                        }
                    } catch (Exception e) {
                        SyncCoordinatorWorker.enqueue(context);
                    }
                } else {
                    SyncCoordinatorWorker.enqueue(context);
                }

                notifySuccess(callback);
            } catch (Exception e) {
                Log.e(TAG, "Error postponing visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

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

                if (NetworkUtil.isNetworkAvailable(context)) {
                    try {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                        Map<String, Object> body = new HashMap<>();
                        body.put("ended_at", dateFormat.format(new Date(endedAt)));
                        body.put("end_latitude", latitude);
                        body.put("end_longitude", longitude);
                        body.put("notes", notes);

                        String targetId = visit.serverId != null ? visit.serverId : visit.visitId;
                        Response<Map<String, Object>> response = api.endVisit("end", targetId, body).execute();
                        if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().get("success"))) {
                            customerVisitDao.markAsSynced(visit.visitId, System.currentTimeMillis());
                            visit.syncStatus = "SYNCED";
                        } else {
                            SyncCoordinatorWorker.enqueue(context);
                        }
                    } catch (Exception e) {
                        SyncCoordinatorWorker.enqueue(context);
                    }
                } else {
                    SyncCoordinatorWorker.enqueue(context);
                }

                notifySuccess(callback, visit);
            } catch (Exception e) {
                Log.e(TAG, "Error completing customer visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    public void cancelVisit(String visitId, String notes, VisitCallback callback) {
        executor.execute(() -> {
            try {
                CustomerVisit visit = customerVisitDao.getByVisitId(visitId);
                if (visit == null) {
                    notifyError(callback, "Customer visit was not found.");
                    return;
                }
                long endedAt = System.currentTimeMillis();
                long durationSeconds = visit.startedAt > 0 ? Math.max(0, (endedAt - visit.startedAt) / 1000) : 0;
                visit.visitStatus = "CANCELLED";
                visit.endedAt = endedAt;
                visit.durationSeconds = durationSeconds;
                visit.cancelReason = notes;
                visit.updatedAt = endedAt;
                visit.syncStatus = "PENDING";
                if (notes != null) {
                    visit.notes = notes;
                }
                customerVisitDao.update(visit);

                if (NetworkUtil.isNetworkAvailable(context)) {
                    try {
                        Map<String, Object> body = new HashMap<>();
                        body.put("reason", notes != null ? notes : "Cancelled by user");

                        String targetId = visit.serverId != null ? visit.serverId : visit.visitId;
                        Response<Map<String, Object>> response = api.cancelVisit("cancel", targetId, body).execute();
                        if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().get("success"))) {
                            customerVisitDao.markAsSynced(visit.visitId, System.currentTimeMillis());
                            visit.syncStatus = "SYNCED";
                        } else {
                            SyncCoordinatorWorker.enqueue(context);
                        }
                    } catch (Exception e) {
                        SyncCoordinatorWorker.enqueue(context);
                    }
                } else {
                    SyncCoordinatorWorker.enqueue(context);
                }

                notifySuccess(callback, visit);
            } catch (Exception e) {
                Log.e(TAG, "Error cancelling customer visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    public void getActiveVisit(String userId, VisitCallback callback) {
        executor.execute(() -> {
            try {
                CustomerVisit visit = customerVisitDao.getActiveVisit(userId);
                notifySuccess(callback, visit);
            } catch (Exception e) {
                Log.e(TAG, "Error getting active customer visit", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    public void getScheduledVisits(String userId, VisitListCallback callback) {
        executor.execute(() -> {
            try {
                List<CustomerVisit> visits = customerVisitDao.getScheduledVisits(userId);
                notifySuccess(callback, visits);
            } catch (Exception e) {
                Log.e(TAG, "Error getting scheduled visits", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(getSafeErrorMessage(e)));
                }
            }
        });
    }

    public void getVisitsByUser(String userId, VisitListCallback callback) {
        executor.execute(() -> {
            try {
                List<CustomerVisit> visits = customerVisitDao.getVisitsByUser(userId);
                notifySuccess(callback, visits);
            } catch (Exception e) {
                Log.e(TAG, "Error getting customer visits", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(getSafeErrorMessage(e)));
                }
            }
        });
    }

    public void getVisitsByCustomer(String customerId, VisitListCallback callback) {
        executor.execute(() -> {
            try {
                List<CustomerVisit> visits = customerVisitDao.getVisitsByCustomer(customerId);
                notifySuccess(callback, visits);
            } catch (Exception e) {
                Log.e(TAG, "Error getting customer visit history", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(getSafeErrorMessage(e)));
                }
            }
        });
    }

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
                notifySuccess(callback);
                SyncCoordinatorWorker.enqueue(context);
            } catch (Exception e) {
                Log.e(TAG, "Error linking visit to customer", e);
                notifyError(callback, getSafeErrorMessage(e));
            }
        });
    }

    public void getPendingVisits(VisitListCallback callback) {
        executor.execute(() -> {
            try {
                List<CustomerVisit> visits = customerVisitDao.getPendingVisits();
                notifySuccess(callback, visits);
            } catch (Exception e) {
                Log.e(TAG, "Error getting pending customer visits", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(getSafeErrorMessage(e)));
                }
            }
        });
    }

    public void getPendingVisitCount(CountCallback callback) {
        executor.execute(() -> {
            try {
                int count = customerVisitDao.getPendingVisitCount();
                if (callback != null) {
                    mainHandler.post(() -> callback.onCount(count));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting pending visit count", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onCount(0));
                }
            }
        });
    }

    private String extractServerId(Map<String, Object> resBody) {
        if (resBody == null) return null;
        Object dataObj = resBody.get("data");
        if (dataObj instanceof Map) {
            Map<?, ?> dataMap = (Map<?, ?>) dataObj;
            if (dataMap.containsKey("visit_id")) {
                return String.valueOf(dataMap.get("visit_id"));
            } else if (dataMap.containsKey("id")) {
                return String.valueOf(dataMap.get("id"));
            }
        }
        return null;
    }

    private void notifySuccess(VisitCallback callback, CustomerVisit visit) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(visit));
        }
    }

    private void notifySuccess(VisitListCallback callback, List<CustomerVisit> visits) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(visits));
        }
    }

    private void notifySuccess(SimpleCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void notifyError(VisitCallback callback, String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message));
        }
    }

    private void notifyError(SimpleCallback callback, String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message));
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
