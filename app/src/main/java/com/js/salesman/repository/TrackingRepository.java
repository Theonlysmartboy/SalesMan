package com.js.salesman.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.interfaces.TrackingDao;
import com.js.salesman.models.TrackingRecord;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.database.AppDatabase;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.workers.TrackingSyncWorker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class TrackingRepository {
    private static final String TAG = "TrackingRepository";
    private static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES_PER_RUN = 50;
    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long LEASE_DURATION_MS = 5 * 60 * 1000L;
    private static final String SAVE_ERROR = "Unable to save location.";
    private static final String SYNC_ERROR = "Unexpected error while syncing locations.";
    private static volatile TrackingRepository instance;
    private final Context context;
    private final TrackingDao trackingDao;
    private final Executor saveExecutor = Executors.newSingleThreadExecutor();
    private final Executor syncExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SaveCallback {
        void onSuccess(TrackingRecord record);
        void onError(String message);
    }

    public interface SyncCallback {
        void onSuccess(int syncedCount);
        void onError(String message, boolean isTransient);
    }

    public interface CountCallback {
        void onCount(int count);
    }

    public static TrackingRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (TrackingRepository.class) {
                if (instance == null) {
                    instance = new TrackingRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public TrackingRepository(Context context) {
        this.context = context.getApplicationContext();
        this.trackingDao = AppDatabase.getInstance(this.context).trackingDao();
    }

    public void saveLocation(String userId, double latitude, double longitude, long timestamp,
                                @Nullable SaveCallback callback) {
        saveLocation(userId, null, latitude, longitude, timestamp, callback);
    }

    public void saveLocation(String userId, @Nullable String visitId, double latitude,
                                double longitude, long timestamp, @Nullable SaveCallback callback) {
        saveExecutor.execute(() -> {
            final TrackingRecord record;
            try {
                record = new TrackingRecord(userId, visitId, latitude, longitude, timestamp);
                record.setId(trackingDao.insert(record));
            } catch (Exception e) {
                logFailure("TRACKING_LOCAL_SAVE_ERROR", "Failed to save location locally", e);
                if (callback != null) mainHandler.post(() -> callback.onError(SAVE_ERROR));
                return;
            }
            logEvent("TRACKING_LOCAL_SAVE", String.format(Locale.US,
                    "Saved offline location point [ID=%d, TrackingID=%s, VisitID=%s, "
                            + "Lat=%.6f, Lng=%.6f, Time=%d]",
                    record.getId(), record.getTrackingId(), visitId, latitude, longitude, timestamp));
            if (callback != null) mainHandler.post(() -> callback.onSuccess(record));
            triggerSync();
        });
    }

    public void saveBatchLocations(String userId, @Nullable List<Map<String, Object>> locationList,
                                    @Nullable Runnable onComplete) {
        if (locationList == null || locationList.isEmpty()) {
            postComplete(onComplete);
            return;
        }
        // Copy so the caller can safely mutate their list after this returns.
        final List<Map<String, Object>> snapshot = new ArrayList<>(locationList);
        saveExecutor.execute(() -> {
            try {
                List<TrackingRecord> records = new ArrayList<>();
                int skipped = 0;
                for (Map<String, Object> point : snapshot) {
                    TrackingRecord record = toRecord(userId, point);
                    if (record != null) {
                        records.add(record);
                    } else {
                        skipped++;
                    }
                }
                if (skipped > 0) {
                    Log.w(TAG, "saveBatchLocations: skipped " + skipped + " invalid point(s)");
                }
                if (records.isEmpty()) return;
                trackingDao.insertAll(records);
                logEvent("TRACKING_LOCAL_SAVE", String.format(Locale.US,
                        "Saved batch of %d tracking records locally", records.size()));
                TrackingSyncWorker.enqueueOneTimeSync(context);
            } catch (Exception e) {
                logFailure("TRACKING_LOCAL_SAVE_ERROR", "Failed to save batch locations", e);
            } finally {
                postComplete(onComplete);
            }
        });
    }

    @Nullable
    private static TrackingRecord toRecord(String userId, @Nullable Map<String, Object> point) {
        if (point == null) return null;
        Object lat = point.get("latitude");
        Object lng = point.get("longitude");
        if (!(lat instanceof Number) || !(lng instanceof Number)) return null;
        double latitude = ((Number) lat).doubleValue();
        double longitude = ((Number) lng).doubleValue();
        if (!isValidCoordinate(latitude, longitude)) return null;
        Object ts = point.get("timestamp");
        long timestamp = ts instanceof Number ? ((Number) ts).longValue() : System.currentTimeMillis();
        Object visit = point.get("visit_id");
        String visitId = visit != null ? String.valueOf(visit) : null;
        return new TrackingRecord(userId, visitId, latitude, longitude, timestamp);
    }

    private static boolean isValidCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }

    public void syncPendingRecords(@Nullable SyncCallback callback) {
        runSync(onMain(callback));
    }

    private void triggerSync() {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            TrackingSyncWorker.enqueueOneTimeSync(context);
            return;
        }
        runSync(new SyncCallback() {
            @Override
            public void onSuccess(int syncedCount) {
                // Nothing to do.
            }
            @Override
            public void onError(String message, boolean isTransient) {
                if (isTransient) TrackingSyncWorker.enqueueOneTimeSync(context);
            }
        });
    }

    private void runSync(@Nullable SyncCallback callback) {
        syncExecutor.execute(() -> {
            int totalSynced = 0;
            String activeBatchId = null;
            try {
                ApiInterface api = ApiClient.getApi(context);
                for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
                    String batchId = UUID.randomUUID().toString();
                    List<TrackingRecord> pending = trackingDao.claimPendingRecords(
                            BATCH_SIZE, batchId, System.currentTimeMillis(), LEASE_DURATION_MS);
                    if (pending == null || pending.isEmpty()) {
                        Log.d(TAG, "No pending tracking records to synchronize.");
                        completeSync(totalSynced, false, callback);
                        return;
                    }
                    activeBatchId = batchId;
                    logEvent("TRACKING_SYNC_STARTED", String.format(Locale.US,
                            "Uploading batch of %d records (BatchID=%s, Range IDs=%d..%d)",
                            pending.size(), batchId, pending.get(0).getId(),
                            pending.get(pending.size() - 1).getId()));
                    Response<Void> response;
                    try {
                        response = api.sendLocation("save-batch", buildPayload(batchId, pending)).execute();
                    } catch (IOException e) {
                        String message = "Network request failed: " + e.getMessage();
                        Log.w(TAG, "TRACKING_SYNC_NETWORK_ERROR: " + message, e);
                        safeLogError("TRACKING_SYNC_NETWORK_ERROR", message, e);
                        releaseBatch(batchId, false, message);
                        activeBatchId = null;
                        if (callback != null) callback.onError(message, true);
                        return;
                    }
                    if (response.isSuccessful()) {
                        int updated = trackingDao.updateSuccessForBatch(batchId);
                        activeBatchId = null;
                        if (updated == 0) {
                            Log.w(TAG, "Batch completion updated 0 rows. Lease may have "
                                    + "expired or been reclaimed for BatchID=" + batchId);
                        } else {
                            totalSynced += updated;
                        }
                        logEvent("TRACKING_SYNC_SUCCESS", String.format(Locale.US,
                                "Successfully synced batch of %d records (BatchID=%s)",
                                pending.size(), batchId));
                        continue;
                    }
                    int code = response.code();
                    String message = "HTTP Error " + code + ": " + response.message();
                    boolean isTransient = isTransientHttpCode(code);
                    logEvent(isTransient ? "TRACKING_SYNC_RETRY" : "TRACKING_SYNC_PERMANENT_ERROR",
                            isTransient
                                    ? "Transient HTTP " + code + " response, keeping records PENDING"
                                    : "Permanent HTTP " + code + " response, marking records FAILED");
                    releaseBatch(batchId, !isTransient, message);
                    activeBatchId = null;
                    if (callback != null) callback.onError(message, isTransient);
                    return;
                }
                completeSync(totalSynced, true, callback);
            } catch (Exception e) {
                logFailure("TRACKING_SYNC_ERROR", "Unexpected sync exception", e);
                if (activeBatchId != null) {
                    releaseBatch(activeBatchId, false, SYNC_ERROR);
                }
                if (callback != null) callback.onError(SYNC_ERROR, true);
            }
        });
    }

    private void completeSync(int totalSynced, boolean moreRemaining, @Nullable SyncCallback callback) {
        if (totalSynced > 0) {
            // Housekeeping must never turn a successful sync into a failure.
            try {
                trackingDao.deleteOldSyncedRecords(System.currentTimeMillis() - SEVEN_DAYS_MS);
            } catch (Exception e) {
                Log.w(TAG, "Housekeeping of old synced records failed", e);
            }
        }
        if (moreRemaining) {
            TrackingSyncWorker.enqueueOneTimeSync(context);
        }
        if (callback != null) callback.onSuccess(totalSynced);
    }

    private Map<String, Object> buildPayload(String batchId, List<TrackingRecord> records) {
        List<Map<String, Object>> points = new ArrayList<>();
        String userId = null;
        boolean mixedUsers = false;
        for (TrackingRecord record : records) {
            String recordUser = record.getUserId();
            if (recordUser != null) {
                if (userId == null) {
                    userId = recordUser;
                } else if (!userId.equals(recordUser)) {
                    mixedUsers = true;
                }
            }
            Map<String, Object> point = new HashMap<>();
            point.put("tracking_id", record.getTrackingId());
            if (record.getVisitId() != null) {
                point.put("visit_id", record.getVisitId());
            }
            point.put("latitude", record.getLatitude());
            point.put("longitude", record.getLongitude());
            point.put("timestamp", record.getTimestamp());
            points.add(point);
        }
        if (mixedUsers) {
            Log.w(TAG, "Batch " + batchId + " contains records from multiple users; "
                    + "all will be uploaded as user " + userId);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", userId != null ? userId : "");
        payload.put("batch_id", batchId);
        payload.put("locations", points);
        return payload;
    }

    private void releaseBatch(String batchId, boolean permanent, String message) {
        try {
            trackingDao.updateFailureForBatch(batchId,
                    permanent ? TrackingRecord.STATUS_FAILED : TrackingRecord.STATUS_PENDING,
                    message);
        } catch (Exception e) {
            // The lease will expire on its own and the records will be reclaimed.
            Log.e(TAG, "Could not release batch " + batchId, e);
        }
    }

    private static boolean isTransientHttpCode(int code) {
        // 5xx, 408 Request Timeout, 429 Too Many Requests, and 401/403
        // (retryable after re-authentication).
        return code >= 500 || code == 408 || code == 429 || code == 401 || code == 403;
    }

    public void getPendingCountAsync(@Nullable CountCallback callback) {
        saveExecutor.execute(() -> {
            int count = 0;
            try {
                count = trackingDao.getPendingCount();
            } catch (Exception e) {
                Log.e(TAG, "getPendingCountAsync failed", e);
            }
            final int result = count;
            if (callback != null) mainHandler.post(() -> callback.onCount(result));
        });
    }

    private void postComplete(@Nullable Runnable onComplete) {
        if (onComplete != null) mainHandler.post(onComplete);
    }

    @Nullable
    private SyncCallback onMain(@Nullable SyncCallback callback) {
        if (callback == null) return null;
        return new SyncCallback() {
            @Override
            public void onSuccess(int syncedCount) {
                mainHandler.post(() -> callback.onSuccess(syncedCount));
            }
            @Override
            public void onError(String message, boolean isTransient) {
                mainHandler.post(() -> callback.onError(message, isTransient));
            }
        };
    }

    private void logEvent(@NonNull String code, @NonNull String message) {
        Log.d(TAG, code + ": " + message);
        try {
            LogManager.log(context, code, message);
        } catch (Exception e) {
            Log.w(TAG, "LogManager.log failed", e);
        }
    }

    private void logFailure(@NonNull String code, @NonNull String message, @NonNull Exception cause) {
        Log.e(TAG, message, cause);
        safeLogError(code, message, cause);
    }

    private void safeLogError(@NonNull String code, @NonNull String message, @NonNull Exception cause) {
        try {
            LogManager.logError(context, code, message, cause);
        } catch (Exception e) {
            Log.w(TAG, "LogManager.logError failed", e);
        }
    }
}