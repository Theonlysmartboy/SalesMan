package com.js.salesman.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

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
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class CustomerVisitRepository {

    private static final String TAG = "CustomerVisitRepository";
    private static final String STATUS_SCHEDULED = "SCHEDULED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String SYNC_PENDING = "PENDING";
    private static final String SYNC_SYNCED = "SYNCED";
    private static final String TYPE_REGISTERED = "REGISTERED";
    private static final String SOURCE_GPS = "GPS_DETECTED";
    private static final String GENERIC_ERROR = "Something went wrong. Please try again.";
    private static volatile CustomerVisitRepository instance;
    private final Context context;
    private final AppDatabase db;
    private final CustomerVisitDao customerVisitDao;
    private final TrackingDao trackingDao;
    private final Executor actionExecutor = Executors.newSingleThreadExecutor();
    private final Executor readExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<ActiveVisitChangeListener> activeVisitChangeListeners =
            new CopyOnWriteArrayList<>();

    public interface ActiveVisitChangeListener {
        void onActiveVisitChanged(@Nullable CustomerVisit visit);
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

    public interface IdCallback {
        void onResult(@Nullable String id);
    }

    public static CustomerVisitRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (CustomerVisitRepository.class) {
                if (instance == null) {
                    instance = new CustomerVisitRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private CustomerVisitRepository(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
        this.customerVisitDao = db.customerVisitDao();
        this.trackingDao = db.trackingDao();
    }

    public void addActiveVisitChangeListener(@Nullable ActiveVisitChangeListener listener) {
        if (listener != null) {
            activeVisitChangeListeners.addIfAbsent(listener);
        }
    }

    public void removeActiveVisitChangeListener(@Nullable ActiveVisitChangeListener listener) {
        if (listener != null) {
            activeVisitChangeListeners.remove(listener);
        }
    }

    public void notifyActiveVisitChanged(@Nullable CustomerVisit visit) {
        mainHandler.post(() -> {
            for (ActiveVisitChangeListener listener : activeVisitChangeListeners) {
                listener.onActiveVisitChanged(visit);
            }
        });
    }

    @WorkerThread
    @Nullable
    public String getActiveVisitIdOrNull(String userId) {
        if (isBlank(userId)) return null;
        CustomerVisit active = customerVisitDao.getActiveVisit(userId);
        return active != null ? resolveRemoteId(active) : null;
    }

    public void getActiveVisitId(String userId, @NonNull IdCallback callback) {
        readExecutor.execute(() -> {
            String id = null;
            try {
                id = getActiveVisitIdOrNull(userId);
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_READ_ERROR", "getActiveVisitId", e);
            }
            final String result = id;
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void startVisit(String userId, String customerId, String customerType,
                            String businessName, double latitude, double longitude,
                            String visitSource, String notes, VisitCallback callback) {
        actionExecutor.execute(() -> {
            try {
                if (isBlank(userId)) {
                    notifyError(callback, "User ID is required.");
                    return;
                }
                CustomerVisit activeVisit = customerVisitDao.getActiveVisit(userId);
                if (activeVisit != null) {
                    notifyError(callback, activeVisitMessage(activeVisit));
                    return;
                }
                long now = System.currentTimeMillis();
                String clientVisitId = UUID.randomUUID().toString();
                String type = customerType != null ? customerType : TYPE_REGISTERED;
                String source = visitSource != null ? visitSource : SOURCE_GPS;
                // Network first; nothing local has been written yet.
                String serverId = isOnline()
                        ? createVisitRemotely(clientVisitId, userId, customerId, type,
                        businessName, now, latitude, longitude, source, notes)
                        : null;
                boolean synced = serverId != null;
                CustomerVisit visit = newVisit(clientVisitId, serverId, userId, customerId, type,
                        businessName, now, STATUS_IN_PROGRESS, source, notes, now);
                visit.endedAt = null;
                visit.durationSeconds = 0;
                visit.startLatitude = latitude;
                visit.startLongitude = longitude;
                TrackingRecord tracking = new TrackingRecord(userId, synced ? serverId : clientVisitId,
                        latitude, longitude, now);
                boolean trackingSent = synced && submitInitialTracking(userId, serverId, tracking);
                tracking.setStatus(trackingSent
                        ? TrackingRecord.STATUS_SYNCED : TrackingRecord.STATUS_PENDING);
                persist(visit, tracking, true);
                if (!synced || !trackingSent) {
                    SyncCoordinatorWorker.enqueue(context);
                }
                notifyActiveVisitChanged(visit);
                notifySuccess(callback, visit);
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_START_ERROR", "startVisit", e);
                notifyError(callback, GENERIC_ERROR);
            }
        });
    }

    public void scheduleVisit(String userId, String customerId, String businessName,
                                long scheduledAt, String notes, VisitCallback callback) {
        actionExecutor.execute(() -> {
            try {
                if (isBlank(userId)) {
                    notifyError(callback, "User ID is required.");
                    return;
                }
                if (isBlank(customerId)) {
                    notifyError(callback, "Registered customer is required for scheduling.");
                    return;
                }
                long now = System.currentTimeMillis();
                String clientVisitId = UUID.randomUUID().toString();
                String serverId = isOnline()
                        ? scheduleVisitRemotely(clientVisitId, userId, customerId,
                        businessName, scheduledAt, notes)
                        : null;
                CustomerVisit visit = newVisit(clientVisitId, serverId, userId, customerId,
                        TYPE_REGISTERED, businessName, scheduledAt, STATUS_SCHEDULED,
                        SOURCE_GPS, notes, now);
                visit.scheduledAt = scheduledAt;
                persist(visit, null, true);
                if (serverId == null) {
                    SyncCoordinatorWorker.enqueue(context);
                }
                notifySuccess(callback, visit);
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_SCHEDULE_ERROR", "scheduleVisit", e);
                notifyError(callback, GENERIC_ERROR);
            }
        });
    }

    public void startScheduledVisit(String visitId, double latitude, double longitude,
                                    VisitCallback callback) {
        actionExecutor.execute(() -> {
            try {
                CustomerVisit visit = findVisit(visitId);
                if (visit == null) {
                    notifyError(callback, "Scheduled visit not found.");
                    return;
                }
                if (!STATUS_SCHEDULED.equals(visit.visitStatus)) {
                    notifyError(callback, "This visit can no longer be started.");
                    return;
                }
                CustomerVisit activeVisit = customerVisitDao.getActiveVisit(visit.userId);
                if (activeVisit != null) {
                    notifyError(callback, activeVisitMessage(activeVisit));
                    return;
                }
                long now = System.currentTimeMillis();
                String remoteId = resolveRemoteId(visit);
                boolean synced = remoteId != null && isOnline()
                        && startVisitRemotely(remoteId, visit.notes, now, latitude, longitude);
                visit.visitStatus = STATUS_IN_PROGRESS;
                visit.startedAt = now;
                visit.startLatitude = latitude;
                visit.startLongitude = longitude;
                visit.syncStatus = synced ? SYNC_SYNCED : SYNC_PENDING;
                visit.updatedAt = now;
                String trackingVisitId = synced
                        ? remoteId
                        : (!isBlank(visit.visitId) ? visit.visitId : visit.clientVisitId);
                TrackingRecord tracking = new TrackingRecord(
                        visit.userId, trackingVisitId, latitude, longitude, now);
                boolean trackingSent = synced
                        && submitInitialTracking(visit.userId, remoteId, tracking);
                tracking.setStatus(trackingSent
                        ? TrackingRecord.STATUS_SYNCED : TrackingRecord.STATUS_PENDING);
                persist(visit, tracking, false);
                if (!synced || !trackingSent) {
                    SyncCoordinatorWorker.enqueue(context);
                }
                notifyActiveVisitChanged(visit);
                notifySuccess(callback, visit);
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_START_SCHEDULED_ERROR",
                        "startScheduledVisit", e);
                notifyError(callback, GENERIC_ERROR);
            }
        });
    }

    public void postponeVisit(String visitId, long newScheduledAt, String notes,
                                SimpleCallback callback) {
        actionExecutor.execute(() -> {
            try {
                if (isBlank(visitId)) {
                    notifyError(callback, "Visit ID is required.");
                    return;
                }
                long now = System.currentTimeMillis();
                int updated = customerVisitDao.postponeVisit(visitId, newScheduledAt, notes, now);
                if (updated == 0) {
                    notifyError(callback, "Visit not found.");
                    return;
                }
                CustomerVisit visit = customerVisitDao.getByVisitId(visitId);
                boolean synced = visit != null && isOnline()
                        && postponeRemotely(resolveRemoteId(visit), newScheduledAt, notes);
                if (synced) {
                    try {
                        customerVisitDao.markAsSynced(visitId, System.currentTimeMillis());
                    } catch (Exception e) {
                        // The server already has the change; the sync worker will reconcile.
                        Log.w(TAG, "postponeVisit: could not mark as synced", e);
                        SyncCoordinatorWorker.enqueue(context);
                    }
                } else {
                    SyncCoordinatorWorker.enqueue(context);
                }
                notifySuccess(callback);
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_POSTPONE_ERROR", "postponeVisit", e);
                notifyError(callback, GENERIC_ERROR);
            }
        });
    }

    public void completeVisit(String visitId, double latitude, double longitude,
                                String notes, VisitCallback callback) {
        actionExecutor.execute(() -> {
            try {
                CustomerVisit visit = findVisit(visitId);
                if (visit == null) {
                    notifyError(callback, "Customer visit was not found.");
                    return;
                }
                if (!STATUS_IN_PROGRESS.equals(visit.visitStatus)) {
                    notifyError(callback, "Customer visit is no longer active.");
                    return;
                }
                long endedAt = System.currentTimeMillis();
                long durationSeconds = Math.max(0, (endedAt - visit.startedAt) / 1000);
                String remoteId = resolveRemoteId(visit);
                boolean synced = remoteId != null && isOnline()
                        && endVisitRemotely(remoteId, endedAt, latitude, longitude, notes);
                visit.visitStatus = STATUS_COMPLETED;
                visit.endedAt = endedAt;
                visit.durationSeconds = durationSeconds;
                visit.endLatitude = latitude;
                visit.endLongitude = longitude;
                if (notes != null) visit.notes = notes;
                visit.syncStatus = synced ? SYNC_SYNCED : SYNC_PENDING;
                visit.updatedAt = endedAt;
                persist(visit, null, false);
                if (synced) {
                    flushPendingTracking();
                } else {
                    SyncCoordinatorWorker.enqueue(context);
                }
                notifyActiveVisitChanged(null);
                notifySuccess(callback, visit);
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_COMPLETE_ERROR", "completeVisit", e);
                notifyError(callback, GENERIC_ERROR);
            }
        });
    }

    public void cancelVisit(String visitId, String notes, VisitCallback callback) {
        actionExecutor.execute(() -> {
            try {
                CustomerVisit visit = findVisit(visitId);
                if (visit == null) {
                    notifyError(callback, "Customer visit was not found.");
                    return;
                }
                if (STATUS_COMPLETED.equals(visit.visitStatus)
                        || STATUS_CANCELLED.equals(visit.visitStatus)) {
                    notifyError(callback, "This visit is already closed.");
                    return;
                }
                long endedAt = System.currentTimeMillis();
                // Only visits that actually started have a meaningful duration.
                long durationSeconds = STATUS_IN_PROGRESS.equals(visit.visitStatus)
                        ? Math.max(0, (endedAt - visit.startedAt) / 1000) : 0;
                String remoteId = resolveRemoteId(visit);
                boolean synced = remoteId != null && isOnline()
                        && cancelVisitRemotely(remoteId, notes);
                visit.visitStatus = STATUS_CANCELLED;
                visit.endedAt = endedAt;
                visit.durationSeconds = durationSeconds;
                visit.cancelReason = notes;
                if (notes != null) visit.notes = notes;
                visit.syncStatus = synced ? SYNC_SYNCED : SYNC_PENDING;
                visit.updatedAt = endedAt;
                persist(visit, null, false);
                if (!synced) {
                    SyncCoordinatorWorker.enqueue(context);
                }
                notifyActiveVisitChanged(null);
                notifySuccess(callback, visit);
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_CANCEL_ERROR", "cancelVisit", e);
                notifyError(callback, GENERIC_ERROR);
            }
        });
    }

    public void linkVisitToCustomer(String visitId, String customerId, SimpleCallback callback) {
        actionExecutor.execute(() -> {
            try {
                if (isBlank(visitId)) {
                    notifyError(callback, "Visit ID is required.");
                    return;
                }
                if (isBlank(customerId)) {
                    notifyError(callback, "Customer ID is required.");
                    return;
                }
                int updated = customerVisitDao.linkToCustomer(
                        visitId, customerId, System.currentTimeMillis());
                if (updated == 0) {
                    notifyError(callback, "Customer visit was not found.");
                    return;
                }
                Log.d(TAG, "Linked visit " + visitId + " to customer " + customerId);
                SyncCoordinatorWorker.enqueue(context);
                notifySuccess(callback);
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_LINK_ERROR", "linkVisitToCustomer", e);
                notifyError(callback, GENERIC_ERROR);
            }
        });
    }

    public void getActiveVisit(String userId, VisitCallback callback) {
        readExecutor.execute(() -> {
            try {
                notifySuccess(callback, customerVisitDao.getActiveVisit(userId));
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_READ_ERROR", "getActiveVisit", e);
                notifyError(callback, GENERIC_ERROR);
            }
        });
    }

    public void getScheduledVisits(String userId, VisitListCallback callback) {
        readList("getScheduledVisits", () -> customerVisitDao.getScheduledVisits(userId), callback);
    }

    public void getVisitsByUser(String userId, VisitListCallback callback) {
        readList("getVisitsByUser", () -> customerVisitDao.getVisitsByUser(userId), callback);
    }

    public void getVisitsByCustomer(String customerId, VisitListCallback callback) {
        readList("getVisitsByCustomer",
                () -> customerVisitDao.getVisitsByCustomer(customerId), callback);
    }

    public void getPendingVisits(VisitListCallback callback) {
        readList("getPendingVisits", customerVisitDao::getPendingVisits, callback);
    }

    public void getPendingVisitCount(CountCallback callback) {
        readExecutor.execute(() -> {
            int count = 0;
            try {
                count = customerVisitDao.getPendingVisitCount();
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_READ_ERROR", "getPendingVisitCount", e);
            }
            final int result = count;
            if (callback != null) mainHandler.post(() -> callback.onCount(result));
        });
    }

    private void readList(String operation, Callable<List<CustomerVisit>> query,
                            VisitListCallback callback) {
        readExecutor.execute(() -> {
            try {
                List<CustomerVisit> visits = query.call();
                notifySuccess(callback, visits != null ? visits : new ArrayList<>());
            } catch (Exception e) {
                reportFailure("CUSTOMER_VISIT_READ_ERROR", operation, e);
                notifyError(callback, GENERIC_ERROR);
            }
        });
    }

    @WorkerThread
    private ApiInterface api() {
        return ApiClient.getApi(context);
    }

    @Nullable
    private String createVisitRemotely(String clientVisitId, String userId,
                                        String customerId, String customerType,
                                        String businessName, long startedAt,
                                        double latitude, double longitude,
                                        String source, String notes) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("client_visit_id", clientVisitId);
            body.put("user_id", userId);
            if (!isBlank(customerId)) body.put("customer_id", customerId);
            body.put("customer_type", customerType);
            body.put("business_name", businessName);
            body.put("started_at", formatTimestamp(startedAt));
            body.put("start_latitude", latitude);
            body.put("start_longitude", longitude);
            body.put("visit_source", source);
            if (notes != null) body.put("notes", notes);
            Response<Map<String, Object>> response = api().createVisit("create", body).execute();
            if (isSuccess(response)) {
                String serverId = extractServerId(response.body());
                if (!isBlank(serverId)) return serverId;
            }
        } catch (Exception e) {
            Log.w(TAG, "Online visit creation failed, falling back to offline", e);
        }
        return null;
    }

    @Nullable
    private String scheduleVisitRemotely(String clientVisitId, String userId, String customerId,
                                            String businessName, long scheduledAt, String notes) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("client_visit_id", clientVisitId);
            body.put("user_id", userId);
            body.put("customer_id", customerId);
            body.put("business_name", businessName);
            body.put("scheduled_at", formatTimestamp(scheduledAt));
            if (notes != null) body.put("notes", notes);
            Response<Map<String, Object>> response = api().scheduleVisit("schedule", body).execute();
            if (isSuccess(response)) {
                String serverId = extractServerId(response.body());
                if (!isBlank(serverId)) return serverId;
            }
        } catch (Exception e) {
            Log.w(TAG, "Online schedule visit failed, falling back to offline", e);
        }
        return null;
    }

    private boolean startVisitRemotely(String remoteId, @Nullable String notes, long startedAt,
                                        double latitude, double longitude) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("started_at", formatTimestamp(startedAt));
            body.put("start_latitude", latitude);
            body.put("start_longitude", longitude);
            if (notes != null) body.put("notes", notes);
            return isSuccess(api().startVisit("start", remoteId, body).execute());
        } catch (Exception e) {
            Log.w(TAG, "Online start scheduled visit failed, falling back to offline", e);
            return false;
        }
    }

    private boolean postponeRemotely(String remoteId, long newScheduledAt, @Nullable String notes) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("scheduled_at", formatTimestamp(newScheduledAt));
            body.put("notes", notes);
            return isSuccess(api().postponeVisit("postpone", remoteId, body).execute());
        } catch (Exception e) {
            Log.w(TAG, "Online postpone failed, will sync later", e);
            return false;
        }
    }

    private boolean endVisitRemotely(String remoteId, long endedAt, double latitude,
                                        double longitude, @Nullable String notes) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("ended_at", formatTimestamp(endedAt));
            body.put("end_latitude", latitude);
            body.put("end_longitude", longitude);
            if (notes != null) body.put("notes", notes);
            return isSuccess(api().endVisit("end", remoteId, body).execute());
        } catch (Exception e) {
            Log.w(TAG, "Online complete visit failed, falling back to offline", e);
            return false;
        }
    }

    private boolean cancelVisitRemotely(String remoteId, @Nullable String notes) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("reason", notes != null ? notes : "Cancelled by user");
            return isSuccess(api().cancelVisit("cancel", remoteId, body).execute());
        } catch (Exception e) {
            Log.w(TAG, "Online cancel visit failed, falling back to offline", e);
            return false;
        }
    }

    private boolean submitInitialTracking(String userId, String visitId, TrackingRecord record) {
        try {
            Map<String, Object> point = new HashMap<>();
            point.put("tracking_id", record.getTrackingId());
            point.put("visit_id", visitId);
            point.put("latitude", record.getLatitude());
            point.put("longitude", record.getLongitude());
            point.put("timestamp", record.getTimestamp());
            List<Map<String, Object>> locations = new ArrayList<>();
            locations.add(point);
            Map<String, Object> payload = new HashMap<>();
            payload.put("user_id", userId);
            payload.put("batch_id", UUID.randomUUID().toString());
            payload.put("locations", locations);
            Response<Void> response = api().sendLocation("save-batch", payload).execute();
            return response.isSuccessful();
        } catch (Exception e) {
            Log.w(TAG, "Immediate tracking sync failed, will retry later", e);
            return false;
        }
    }

    private void flushPendingTracking() {
        try {
            TrackingRepository.getInstance(context).syncPendingRecords(null);
        } catch (Exception e) {
            Log.w(TAG, "Could not flush tracking records, scheduling background sync", e);
            SyncCoordinatorWorker.enqueue(context);
        }
    }

    /** Writes the visit and optional tracking record atomically. */
    private void persist(@NonNull CustomerVisit visit, @Nullable TrackingRecord tracking,
                            boolean isNew) {
        db.runInTransaction(() -> {
            if (isNew) {
                customerVisitDao.insert(visit);
            } else {
                customerVisitDao.update(visit);
            }
            if (tracking != null) {
                trackingDao.insert(tracking);
            }
        });
    }

    private CustomerVisit newVisit(String clientVisitId, @Nullable String serverId,
                                    String userId, @Nullable String customerId, String customerType,
                                    String businessName, long startedAt, String status, String source,
                                    @Nullable String notes, long now) {
        CustomerVisit visit = new CustomerVisit();
        visit.clientVisitId = clientVisitId;
        visit.visitId = serverId;
        visit.serverId = serverId;
        visit.userId = userId;
        visit.customerId = customerId;
        visit.customerType = customerType;
        visit.businessName = businessName;
        visit.startedAt = startedAt;
        visit.visitStatus = status;
        visit.visitSource = source;
        visit.notes = notes;
        visit.syncStatus = serverId != null ? SYNC_SYNCED : SYNC_PENDING;
        visit.createdAt = now;
        visit.updatedAt = now;
        return visit;
    }

    /** Finds a visit by server/visit ID, falling back to the offline client ID. */
    @Nullable
    private CustomerVisit findVisit(@Nullable String id) {
        if (isBlank(id)) return null;
        CustomerVisit visit = customerVisitDao.getByVisitId(id);
        if (visit != null) return visit;
        List<CustomerVisit> pending = customerVisitDao.getPendingVisits();
        if (pending != null) {
            for (CustomerVisit candidate : pending) {
                if (id.equals(candidate.clientVisitId)) return candidate;
            }
        }
        return null;
    }

    @Nullable
    private static String resolveRemoteId(@NonNull CustomerVisit visit) {
        if (!isBlank(visit.serverId)) return visit.serverId;
        if (!isBlank(visit.visitId)) return visit.visitId;
        return visit.clientVisitId;
    }

    private static String activeVisitMessage(CustomerVisit active) {
        String name = active.businessName != null ? active.businessName : "another customer";
        return "You have an active visit at " + name + ". End or cancel it first.";
    }

    private boolean isOnline() {
        return NetworkUtil.isNetworkAvailable(context);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isSuccess(@Nullable Response<Map<String, Object>> response) {
        return response != null
                && response.isSuccessful()
                && response.body() != null
                && Boolean.TRUE.equals(response.body().get("success"));
    }

    private static String formatTimestamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    @Nullable
    private static String extractServerId(@Nullable Map<String, Object> body) {
        if (body == null) return null;
        String id = firstId(body);
        if (id != null) return id;
        Object data = body.get("data");
        return data instanceof Map ? firstId((Map<?, ?>) data) : null;
    }

    @Nullable
    private static String firstId(Map<?, ?> map) {
        Object value = map.get("visit_id");
        if (value == null) value = map.get("id");
        return value != null ? idToString(value) : null;
    }

    /** Gson parses JSON numbers into Double; avoid turning 123 into "123.0". */
    private static String idToString(Object value) {
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
        }
        return String.valueOf(value);
    }

    private void reportFailure(String code, String operation, Exception e) {
        Log.e(TAG, operation + " failed", e);
        LogManager.logError(context, code, operation + " failed", e);
    }

    private void notifySuccess(@Nullable VisitCallback callback, CustomerVisit visit) {
        if (callback != null) mainHandler.post(() -> callback.onSuccess(visit));
    }

    private void notifySuccess(@Nullable VisitListCallback callback, List<CustomerVisit> visits) {
        if (callback != null) mainHandler.post(() -> callback.onSuccess(visits));
    }

    private void notifySuccess(@Nullable SimpleCallback callback) {
        if (callback != null) mainHandler.post(callback::onSuccess);
    }

    private void notifyError(@Nullable VisitCallback callback, String message) {
        if (callback != null) mainHandler.post(() -> callback.onError(message));
    }

    private void notifyError(@Nullable VisitListCallback callback, String message) {
        if (callback != null) mainHandler.post(() -> callback.onError(message));
    }

    private void notifyError(@Nullable SimpleCallback callback, String message) {
        if (callback != null) mainHandler.post(() -> callback.onError(message));
    }
}