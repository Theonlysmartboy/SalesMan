package com.js.salesman.repository;

import android.content.Context;
import android.util.Log;

import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.interfaces.TrackingDao;
import com.js.salesman.models.TrackingRecord;
import com.js.salesman.utils.database.AppDatabase;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.workers.TrackingSyncWorker;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

import retrofit2.Response;

public class TrackingRepository {
    private static final String TAG = "TrackingRepository";
    private static final int BATCH_SIZE = 100;
    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;

    private final Context context;
    private final TrackingDao trackingDao;
    private final ApiInterface apiInterface;
    private final Executor executor;

    public interface SaveCallback {
        void onSuccess(TrackingRecord record);
        void onError(String message);
    }

    public interface SyncCallback {
        void onSuccess(int syncedCount);
        void onError(String message, boolean isTransient);
    }

    public TrackingRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(this.context);
        this.trackingDao = db.trackingDao();
        this.apiInterface = ApiClient.getApi(this.context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void saveLocation(String userId, double latitude, double longitude, long timestamp,
                                SaveCallback callback) {
        executor.execute(() -> {
            try {
                TrackingRecord record = new TrackingRecord(userId, latitude, longitude, timestamp);
                long id = trackingDao.insert(record);
                record.setId(id);

                String logMsg = String.format(Locale.US, "Saved offline location " +
                                "point [ID=%d, TrackingID=%s, Lat=%.6f, Lng=%.6f, Time=%d]",
                        id, record.getTrackingId(), latitude, longitude, timestamp);
                Log.d(TAG, "TRACKING_LOCAL_SAVE: " + logMsg);
                LogManager.log(context, "TRACKING_LOCAL_SAVE", logMsg);

                if (callback != null) {
                    callback.onSuccess(record);
                }

                // Trigger background sync worker
                TrackingSyncWorker.enqueueOneTimeSync(context);
            } catch (Exception e) {
                Log.e(TAG, "Error saving location record locally", e);
                LogManager.logError(context, "TRACKING_LOCAL_SAVE_ERROR",
                        "Failed to save location locally", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    public void saveBatchLocations(String userId, List<Map<String, Object>> locationList,
                                    Runnable onComplete) {
        if (locationList == null || locationList.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        executor.execute(() -> {
            try {
                List<TrackingRecord> records = new ArrayList<>();
                for (Map<String, Object> pointMap : locationList) {
                    Object latObj = pointMap.get("latitude");
                    Object lngObj = pointMap.get("longitude");
                    Object tsObj = pointMap.get("timestamp");

                    double lat = (latObj instanceof Number) ? ((Number) latObj).doubleValue() : 0.0;
                    double lng = (lngObj instanceof Number) ? ((Number) lngObj).doubleValue() : 0.0;
                    long ts = (tsObj instanceof Number) ? ((Number) tsObj)
                            .longValue() : System.currentTimeMillis();
                    records.add(new TrackingRecord(userId, lat, lng, ts));
                }
                trackingDao.insertAll(records);

                String logMsg = String.format(Locale.US, "Saved batch of %d tracking " +
                        "records locally", records.size());
                Log.d(TAG, "TRACKING_LOCAL_SAVE: " + logMsg);
                LogManager.log(context, "TRACKING_LOCAL_SAVE", logMsg);

                TrackingSyncWorker.enqueueOneTimeSync(context);
            } catch (Exception e) {
                Log.e(TAG, "Error saving batch locations", e);
                LogManager.logError(context, "TRACKING_LOCAL_SAVE_ERROR",
                        "Failed to save batch locations", e);
            } finally {
                if (onComplete != null) onComplete.run();
            }
        });
    }

    public void syncPendingRecords(SyncCallback callback) {
        executor.execute(() -> {
            int totalSynced = 0;
            try {
                // Recover any stuck SYNCING records back to PENDING before reading
                trackingDao.resetSyncingToPending();

                while (true) {
                    List<TrackingRecord> pendingRecords = trackingDao.getPendingRecords(BATCH_SIZE);
                    if (pendingRecords == null || pendingRecords.isEmpty()) {
                        Log.d(TAG, "No pending tracking records to synchronize.");
                        if (callback != null) callback.onSuccess(totalSynced);
                        return;
                    }

                    List<Long> recordIds = new ArrayList<>();
                    List<Map<String, Object>> pointPayloads = new ArrayList<>();
                    String effectiveUserId = null;

                    for (TrackingRecord record : pendingRecords) {
                        recordIds.add(record.getId());
                        if (effectiveUserId == null && record.getUserId() != null) {
                            effectiveUserId = record.getUserId();
                        }

                        Map<String, Object> point = new HashMap<>();
                        point.put("tracking_id", record.getTrackingId());
                        point.put("latitude", record.getLatitude());
                        point.put("longitude", record.getLongitude());
                        point.put("timestamp", record.getTimestamp());
                        pointPayloads.add(point);
                    }
                    // Mark as SYNCING atomically to prevent concurrent duplicate uploads
                    trackingDao.updateStatusForIds(recordIds, TrackingRecord.STATUS_SYNCING);
                    String batchUuid = UUID.randomUUID().toString();
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("user_id", effectiveUserId != null ? effectiveUserId : "");
                    payload.put("batch_id", batchUuid);
                    payload.put("locations", pointPayloads);
                    String startLog = String.format(Locale.US, "TRACKING_SYNC_STARTED: " +
                                    "Uploading batch of %d records (BatchID=%s, Range IDs=%d..%d)",
                            pendingRecords.size(), batchUuid, recordIds.get(0),
                            recordIds.get(recordIds.size() - 1));
                    Log.d(TAG, startLog);
                    LogManager.log(context, "TRACKING_SYNC_STARTED", startLog);
                    Response<Void> response;
                    try {
                        response = apiInterface.sendLocation("save-batch", payload).execute();
                    } catch (IOException e) {
                        boolean isTransient = isTransientNetworkError(e);
                        String errMsg = "Network request failed: " + e.getMessage();
                        Log.w(TAG, "TRACKING_SYNC_NETWORK_ERROR: " + errMsg, e);
                        LogManager.logError(context, "TRACKING_SYNC_NETWORK_ERROR", errMsg, e);

                        // Reset status to PENDING for retry
                        trackingDao.updateFailureForIds(recordIds, TrackingRecord.STATUS_PENDING,
                                errMsg);
                        if (callback != null) callback.onError(errMsg, isTransient);
                        return;
                    }

                    if (response.isSuccessful()) {
                        trackingDao.updateStatusForIds(recordIds, TrackingRecord.STATUS_SYNCED);
                        totalSynced += recordIds.size();

                        String successLog = String.format(Locale.US, "TRACKING_SYNC_SUCCESS: " +
                                        "Successfully synced batch of %d records (BatchID=%s)",
                                recordIds.size(), batchUuid);
                        Log.d(TAG, successLog);
                        LogManager.log(context, "TRACKING_SYNC_SUCCESS", successLog);

                        // Housekeeping: delete synced records older than 7 days
                        long olderThan = System.currentTimeMillis() - SEVEN_DAYS_MS;
                        trackingDao.deleteOldSyncedRecords(olderThan);
                    } else {
                        int code = response.code();
                        String errorMsg = "HTTP Error " + code + ": " + response.message();

                        if (isTransientHttpCode(code)) {
                            // Temporary server issue or auth expiration -> keep as PENDING for retry
                            String retryLog = String.format(Locale.US, "TRACKING_SYNC_RETRY: " +
                                    "Transient HTTP %d response, keeping records PENDING", code);
                            Log.w(TAG, retryLog);
                            LogManager.log(context, "TRACKING_SYNC_RETRY", retryLog);

                            trackingDao.updateFailureForIds(recordIds, TrackingRecord.STATUS_PENDING,
                                    errorMsg);
                            if (callback != null) callback.onError(errorMsg, true);
                            return;
                        } else {
                            // Permanent 4xx client error -> mark as FAILED for diagnosis
                            String permLog = String.format(Locale.US,
                                    "TRACKING_SYNC_PERMANENT_ERROR: Permanent HTTP %d " +
                                            "response, marking records FAILED", code);
                            Log.e(TAG, permLog);
                            LogManager.log(context, "TRACKING_SYNC_PERMANENT_ERROR", permLog);

                            trackingDao.updateFailureForIds(recordIds, TrackingRecord.STATUS_FAILED,
                                    errorMsg);
                            if (callback != null) callback.onError(errorMsg, false);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during tracking sync", e);
                LogManager.logError(context, "TRACKING_SYNC_ERROR", "Unexpected sync " +
                        "exception", e);
                if (callback != null) callback.onError(e.getMessage(), true);
            }
        });
    }

    private boolean isTransientNetworkError(Throwable t) {
        return t instanceof UnknownHostException
                || t instanceof ConnectException
                || t instanceof SocketTimeoutException
                || t instanceof SSLException
                || (t.getMessage() != null && t.getMessage().contains("ENETUNREACH"));
    }

    private boolean isTransientHttpCode(int code) {
        // 5xx Server Errors, 408 Request Timeout, 429 Too Many Requests,
        // and 401/403 Auth Issues (retryable after re-authentication)
        return code >= 500 || code == 408 || code == 429 || code == 401 || code == 403;
    }

    public void getPendingCountAsync(CountCallback callback) {
        executor.execute(() -> {
            try {
                int count = trackingDao.getPendingCount();
                if (callback != null) callback.onCount(count);
            } catch (Exception e) {
                if (callback != null) callback.onCount(0);
            }
        });
    }

    public interface CountCallback {
        void onCount(int count);
    }
}
