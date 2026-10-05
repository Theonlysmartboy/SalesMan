package com.js.salesman.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.interfaces.CustomerVisitDao;
import com.js.salesman.interfaces.PendingOrderDao;
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

    private final List<ActiveVisitChangeListener> activeVisitChangeListeners = new ArrayList<>();
    private final Object actionLock = new Object();

    public interface ActiveVisitChangeListener {
        void onActiveVisitChanged(CustomerVisit visit);
    }

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
        PendingOrderDao pendingOrderDao = database.pendingOrderDao();
        this.api = ApiClient.getApi(this.context);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void addActiveVisitChangeListener(ActiveVisitChangeListener listener) {
        synchronized (activeVisitChangeListeners) {
            if (listener != null && !activeVisitChangeListeners.contains(listener)) {
                activeVisitChangeListeners.add(listener);
            }
        }
    }

    public void removeActiveVisitChangeListener(ActiveVisitChangeListener listener) {
        synchronized (activeVisitChangeListeners) {
            activeVisitChangeListeners.remove(listener);
        }
    }

    public void notifyActiveVisitChanged(CustomerVisit visit) {
        mainHandler.post(() -> {
            synchronized (activeVisitChangeListeners) {
                for (ActiveVisitChangeListener listener : activeVisitChangeListeners) {
                    listener.onActiveVisitChanged(visit);
                }
            }
        });
    }

    public String getActiveVisitIdOrNull(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        CustomerVisit active = customerVisitDao.getActiveVisit(userId);
        if (active == null) return null;
        return (active.serverId != null && !active.serverId.isEmpty()) ? active.serverId : (active.visitId != null ? active.visitId : active.clientVisitId);
    }

    public void startVisit(String userId, String customerId, String customerType,
            String businessName, double latitude, double longitude, String visitSource,
            String notes, VisitCallback callback) {
        executor.execute(() -> {
            synchronized (actionLock) {
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
                    String clientVisitId = UUID.randomUUID().toString();

                    if (NetworkUtil.isNetworkAvailable(context)) {
                        try {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                            Map<String, Object> body = new HashMap<>();
                            body.put("client_visit_id", clientVisitId);
                            body.put("user_id", userId);
                            if (customerId != null && !customerId.trim().isEmpty()) {
                                body.put("customer_id", customerId);
                            }
                            body.put("customer_type", customerType != null ? customerType : "REGISTERED");
                            body.put("business_name", businessName);
                            body.put("started_at", dateFormat.format(new Date(now)));
                            body.put("start_latitude", latitude);
                            body.put("start_longitude", longitude);
                            body.put("visit_source", visitSource != null ? visitSource : "GPS_DETECTED");
                            if (notes != null) body.put("notes", notes);

                            Response<Map<String, Object>> response = api.createVisit("create", body).execute();
                            if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().get("success"))) {
                                String serverId = extractServerId(response.body());
                                if (serverId != null && !serverId.isEmpty()) {
                                    CustomerVisit visit = new CustomerVisit();
                                    visit.clientVisitId = clientVisitId;
                                    visit.visitId = serverId;
                                    visit.serverId = serverId;
                                    visit.userId = userId;
                                    visit.customerId = customerId;
                                    visit.customerType = customerType != null ? customerType : "REGISTERED";
                                    visit.businessName = businessName;
                                    visit.startedAt = now;
                                    visit.endedAt = null;
                                    visit.durationSeconds = 0;
                                    visit.startLatitude = latitude;
                                    visit.startLongitude = longitude;
                                    visit.visitStatus = "IN_PROGRESS";
                                    visit.visitSource = visitSource != null ? visitSource : "GPS_DETECTED";
                                    visit.notes = notes;
                                    visit.syncStatus = "SYNCED";
                                    visit.createdAt = now;
                                    visit.updatedAt = now;

                                    customerVisitDao.insert(visit);

                                    TrackingRecord startTracking = new TrackingRecord(userId, serverId, latitude, longitude, now);
                                    boolean trackingSent = submitInitialTracking(userId, serverId, startTracking);
                                    startTracking.setStatus(trackingSent ? TrackingRecord.STATUS_SYNCED : TrackingRecord.STATUS_PENDING);
                                    trackingDao.insert(startTracking);

                                    notifyActiveVisitChanged(visit);
                                    notifySuccess(callback, visit);
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Online visit creation failed, falling back to offline", e);
                        }
                    }

                    // OFFLINE FALLBACK
                    CustomerVisit visit = new CustomerVisit();
                    visit.clientVisitId = clientVisitId;
                    visit.visitId = null;
                    visit.serverId = null;
                    visit.userId = userId;
                    visit.customerId = customerId;
                    visit.customerType = customerType != null ? customerType : "REGISTERED";
                    visit.businessName = businessName;
                    visit.startedAt = now;
                    visit.endedAt = null;
                    visit.durationSeconds = 0;
                    visit.startLatitude = latitude;
                    visit.startLongitude = longitude;
                    visit.visitStatus = "IN_PROGRESS";
                    visit.visitSource = visitSource != null ? visitSource : "GPS_DETECTED";
                    visit.notes = notes;
                    visit.syncStatus = "PENDING";
                    visit.createdAt = now;
                    visit.updatedAt = now;

                    customerVisitDao.insert(visit);

                    TrackingRecord startTracking = new TrackingRecord(userId, clientVisitId, latitude, longitude, now);
                    startTracking.setStatus(TrackingRecord.STATUS_PENDING);
                    trackingDao.insert(startTracking);

                    SyncCoordinatorWorker.enqueue(context);

                    notifyActiveVisitChanged(visit);
                    notifySuccess(callback, visit);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting customer visit", e);
                    LogManager.logError(context, "CUSTOMER_VISIT_START_ERROR", "Failed to start customer visit", e);
                    notifyError(callback, getSafeErrorMessage(e));
                }
            }
        });
    }

    private boolean submitInitialTracking(String userId, String visitId, TrackingRecord trackingRecord) {
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
            return res.isSuccessful();
        } catch (Exception e) {
            Log.w(TAG, "Immediate tracking sync warning", e);
            return false;
        }
    }

    public void scheduleVisit(String userId, String customerId, String businessName,
            long scheduledAt, String notes, VisitCallback callback) {
        executor.execute(() -> {
            synchronized (actionLock) {
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
                    String clientVisitId = UUID.randomUUID().toString();

                    if (NetworkUtil.isNetworkAvailable(context)) {
                        try {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                            Map<String, Object> body = new HashMap<>();
                            body.put("client_visit_id", clientVisitId);
                            body.put("user_id", userId);
                            body.put("customer_id", customerId);
                            body.put("business_name", businessName);
                            body.put("scheduled_at", dateFormat.format(new Date(scheduledAt)));
                            if (notes != null) body.put("notes", notes);

                            Response<Map<String, Object>> response = api.scheduleVisit("schedule", body).execute();
                            if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().get("success"))) {
                                String serverId = extractServerId(response.body());
                                if (serverId != null && !serverId.isEmpty()) {
                                    CustomerVisit visit = new CustomerVisit();
                                    visit.clientVisitId = clientVisitId;
                                    visit.visitId = serverId;
                                    visit.serverId = serverId;
                                    visit.userId = userId;
                                    visit.customerId = customerId;
                                    visit.customerType = "REGISTERED";
                                    visit.businessName = businessName;
                                    visit.startedAt = scheduledAt;
                                    visit.scheduledAt = scheduledAt;
                                    visit.visitStatus = "SCHEDULED";
                                    visit.visitSource = "GPS_DETECTED";
                                    visit.notes = notes;
                                    visit.syncStatus = "SYNCED";
                                    visit.createdAt = now;
                                    visit.updatedAt = now;

                                    customerVisitDao.insert(visit);

                                    notifySuccess(callback, visit);
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Online schedule visit failed, falling back to offline", e);
                        }
                    }

                    // OFFLINE FALLBACK
                    CustomerVisit visit = new CustomerVisit();
                    visit.clientVisitId = clientVisitId;
                    visit.visitId = null;
                    visit.serverId = null;
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

                    SyncCoordinatorWorker.enqueue(context);

                    notifySuccess(callback, visit);
                } catch (Exception e) {
                    Log.e(TAG, "Error scheduling customer visit", e);
                    notifyError(callback, getSafeErrorMessage(e));
                }
            }
        });
    }

    public void startScheduledVisit(String visitId, double latitude, double longitude,
            VisitCallback callback) {
        executor.execute(() -> {
            synchronized (actionLock) {
                try {
                    CustomerVisit visit = customerVisitDao.getByVisitId(visitId);
                    if (visit == null) {
                        List<CustomerVisit> pending = customerVisitDao.getPendingVisits();
                        for (CustomerVisit v : pending) {
                            if (visitId.equals(v.clientVisitId)) {
                                visit = v;
                                break;
                            }
                        }
                    }
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
                    String targetId = (visit.serverId != null && !visit.serverId.isEmpty()) ? visit.serverId : (visit.visitId != null ? visit.visitId : visit.clientVisitId);

                    if (NetworkUtil.isNetworkAvailable(context) && targetId != null) {
                        try {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                            Map<String, Object> body = new HashMap<>();
                            body.put("started_at", dateFormat.format(new Date(now)));
                            body.put("start_latitude", latitude);
                            body.put("start_longitude", longitude);
                            if (visit.notes != null) body.put("notes", visit.notes);

                            Response<Map<String, Object>> response = api.startVisit("start", targetId, body).execute();
                            if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().get("success"))) {
                                visit.visitStatus = "IN_PROGRESS";
                                visit.startedAt = now;
                                visit.startLatitude = latitude;
                                visit.startLongitude = longitude;
                                visit.syncStatus = "SYNCED";
                                visit.updatedAt = now;
                                customerVisitDao.update(visit);

                                TrackingRecord startTracking = new TrackingRecord(visit.userId, targetId, latitude, longitude, now);
                                boolean trackingSent = submitInitialTracking(visit.userId, targetId, startTracking);
                                startTracking.setStatus(trackingSent ? TrackingRecord.STATUS_SYNCED : TrackingRecord.STATUS_PENDING);
                                trackingDao.insert(startTracking);

                                notifyActiveVisitChanged(visit);
                                notifySuccess(callback, visit);
                                return;
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Online start scheduled visit failed, falling back to offline", e);
                        }
                    }

                    // OFFLINE FALLBACK
                    visit.visitStatus = "IN_PROGRESS";
                    visit.startedAt = now;
                    visit.startLatitude = latitude;
                    visit.startLongitude = longitude;
                    visit.syncStatus = "PENDING";
                    visit.updatedAt = now;
                    customerVisitDao.update(visit);

                    String trackingVisitId = (visit.visitId != null && !visit.visitId.isEmpty()) ? visit.visitId : visit.clientVisitId;
                    TrackingRecord startTracking = new TrackingRecord(visit.userId, trackingVisitId, latitude, longitude, now);
                    startTracking.setStatus(TrackingRecord.STATUS_PENDING);
                    trackingDao.insert(startTracking);

                    SyncCoordinatorWorker.enqueue(context);

                    notifyActiveVisitChanged(visit);
                    notifySuccess(callback, visit);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting scheduled visit", e);
                    notifyError(callback, getSafeErrorMessage(e));
                }
            }
        });
    }

    public void postponeVisit(String visitId, long newScheduledAt, String notes, SimpleCallback callback) {
        executor.execute(() -> {
            synchronized (actionLock) {
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

                            String targetId = visit.serverId != null ? visit.serverId : (visit.visitId != null ? visit.visitId : visit.clientVisitId);
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
            }
        });
    }

    public void completeVisit(String visitId, double latitude, double longitude,
            String notes, VisitCallback callback) {
        executor.execute(() -> {
            synchronized (actionLock) {
                try {
                    CustomerVisit visit = customerVisitDao.getByVisitId(visitId);
                    if (visit == null) {
                        List<CustomerVisit> pending = customerVisitDao.getPendingVisits();
                        for (CustomerVisit v : pending) {
                            if (visitId.equals(v.clientVisitId)) {
                                visit = v;
                                break;
                            }
                        }
                    }
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
                    String targetServerId = visit.serverId != null ? visit.serverId : (visit.visitId != null ? visit.visitId : visit.clientVisitId);

                    if (NetworkUtil.isNetworkAvailable(context) && targetServerId != null) {
                        try {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                            Map<String, Object> body = new HashMap<>();
                            body.put("ended_at", dateFormat.format(new Date(endedAt)));
                            body.put("end_latitude", latitude);
                            body.put("end_longitude", longitude);
                            if (notes != null) body.put("notes", notes);

                            Response<Map<String, Object>> response = api.endVisit("end", targetServerId, body).execute();
                            if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().get("success"))) {
                                visit.visitStatus = "COMPLETED";
                                visit.endedAt = endedAt;
                                visit.durationSeconds = durationSeconds;
                                visit.endLatitude = latitude;
                                visit.endLongitude = longitude;
                                visit.notes = notes != null ? notes : visit.notes;
                                visit.syncStatus = "SYNCED";
                                visit.updatedAt = endedAt;
                                customerVisitDao.update(visit);

                                // Immediately submit outstanding/final tracking records
                                TrackingRepository.getInstance(context).syncPendingRecords(null);

                                notifyActiveVisitChanged(null);
                                notifySuccess(callback, visit);
                                return;
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Online complete visit failed, falling back to offline", e);
                        }
                    }

                    // OFFLINE FALLBACK
                    visit.visitStatus = "COMPLETED";
                    visit.endedAt = endedAt;
                    visit.durationSeconds = durationSeconds;
                    visit.endLatitude = latitude;
                    visit.endLongitude = longitude;
                    if (notes != null) visit.notes = notes;
                    visit.syncStatus = "PENDING";
                    visit.updatedAt = endedAt;
                    customerVisitDao.update(visit);

                    SyncCoordinatorWorker.enqueue(context);

                    notifyActiveVisitChanged(null);
                    notifySuccess(callback, visit);
                } catch (Exception e) {
                    Log.e(TAG, "Error completing customer visit", e);
                    notifyError(callback, getSafeErrorMessage(e));
                }
            }
        });
    }

    public void cancelVisit(String visitId, String notes, VisitCallback callback) {
        executor.execute(() -> {
            synchronized (actionLock) {
                try {
                    CustomerVisit visit = customerVisitDao.getByVisitId(visitId);
                    if (visit == null) {
                        List<CustomerVisit> pending = customerVisitDao.getPendingVisits();
                        for (CustomerVisit v : pending) {
                            if (visitId.equals(v.clientVisitId)) {
                                visit = v;
                                break;
                            }
                        }
                    }
                    if (visit == null) {
                        notifyError(callback, "Customer visit was not found.");
                        return;
                    }

                    long endedAt = System.currentTimeMillis();
                    long durationSeconds = visit.startedAt > 0 ? Math.max(0, (endedAt - visit.startedAt) / 1000) : 0;
                    String targetServerId = visit.serverId != null ? visit.serverId : (visit.visitId != null ? visit.visitId : visit.clientVisitId);

                    if (NetworkUtil.isNetworkAvailable(context) && targetServerId != null) {
                        try {
                            Map<String, Object> body = new HashMap<>();
                            body.put("reason", notes != null ? notes : "Cancelled by user");

                            Response<Map<String, Object>> response = api.cancelVisit("cancel", targetServerId, body).execute();
                            if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().get("success"))) {
                                visit.visitStatus = "CANCELLED";
                                visit.endedAt = endedAt;
                                visit.durationSeconds = durationSeconds;
                                visit.cancelReason = notes;
                                visit.notes = notes != null ? notes : visit.notes;
                                visit.syncStatus = "SYNCED";
                                visit.updatedAt = endedAt;
                                customerVisitDao.update(visit);

                                notifyActiveVisitChanged(null);
                                notifySuccess(callback, visit);
                                return;
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Online cancel visit failed, falling back to offline", e);
                        }
                    }

                    // OFFLINE FALLBACK
                    visit.visitStatus = "CANCELLED";
                    visit.endedAt = endedAt;
                    visit.durationSeconds = durationSeconds;
                    visit.cancelReason = notes;
                    if (notes != null) visit.notes = notes;
                    visit.syncStatus = "PENDING";
                    visit.updatedAt = endedAt;
                    customerVisitDao.update(visit);

                    SyncCoordinatorWorker.enqueue(context);

                    notifyActiveVisitChanged(null);
                    notifySuccess(callback, visit);
                } catch (Exception e) {
                    Log.e(TAG, "Error cancelling customer visit", e);
                    notifyError(callback, getSafeErrorMessage(e));
                }
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
            synchronized (actionLock) {
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
        if (resBody.containsKey("visit_id") && resBody.get("visit_id") != null) {
            return String.valueOf(resBody.get("visit_id"));
        }
        if (resBody.containsKey("id") && resBody.get("id") != null) {
            return String.valueOf(resBody.get("id"));
        }
        Object dataObj = resBody.get("data");
        if (dataObj instanceof Map) {
            Map<?, ?> dataMap = (Map<?, ?>) dataObj;
            if (dataMap.containsKey("visit_id") && dataMap.get("visit_id") != null) {
                return String.valueOf(dataMap.get("visit_id"));
            } else if (dataMap.containsKey("id") && dataMap.get("id") != null) {
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
