package com.js.salesman.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.interfaces.CustomerVisitDao;
import com.js.salesman.interfaces.PendingOrderDao;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.models.PendingOrder;
import com.js.salesman.repository.ProductRepository;
import com.js.salesman.repository.TrackingRepository;
import com.js.salesman.utils.database.AppDatabase;
import com.js.salesman.utils.managers.LogManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

public class SyncCoordinatorWorker extends Worker {

    private static final String TAG = "SyncCoordinatorWorker";
    public static final String WORK_NAME = "SyncCoordinator_OneTime";
    private final CustomerVisitDao visitDao;
    private final PendingOrderDao orderDao;
    private final ApiInterface api;
    private final Gson gson;

    public SyncCoordinatorWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        AppDatabase db = AppDatabase.getInstance(context);
        this.visitDao = db.customerVisitDao();
        this.orderDao = db.pendingOrderDao();
        this.api = ApiClient.getApi(context);
        this.gson = new Gson();
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting coalesced synchronization pipeline...");
        LogManager.log(getApplicationContext(), "SYNC_COORDINATOR",
                "Started coalesced sync pipeline");
        boolean hasTransientError = false;
        // 1. VISIT CREATIONS & SCHEDULES
        hasTransientError |= !syncPendingVisitCreations();
        // 2. VISIT TRANSITIONS
        hasTransientError |= !syncPendingVisitTransitions();
        // 3. TRACKING POINTS
        hasTransientError |= !syncTrackingPoints();
        // 4. ORDERS
        hasTransientError |= !syncPendingOrders();
        // 5. CACHE REFRESH
        refreshCache();
        if (hasTransientError) {
            Log.w(TAG, "Sync finished with transient error. Retrying with backoff.");
            return Result.retry();
        }
        Log.d(TAG, "Sync pipeline completed successfully.");
        return Result.success();
    }

    private boolean syncPendingVisitCreations() {
        List<CustomerVisit> pendingVisits = visitDao.getPendingVisits();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (CustomerVisit visit : pendingVisits) {
            if (visit.serverId != null && !visit.serverId.isEmpty()) {
                continue; // Already created on server
            }
            long now = System.currentTimeMillis();
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", visit.userId);
            body.put("customer_id", visit.customerId);
            body.put("customer_type", visit.customerType != null ? visit
                                                                .customerType : "REGISTERED");
            body.put("business_name", visit.businessName);
            body.put("notes", visit.notes);
            try {
                Response<Map<String, Object>> response;
                if ("SCHEDULED".equals(visit.visitStatus)) {
                    long scheduledTime = visit.scheduledAt != null ? visit.scheduledAt : visit.startedAt;
                    body.put("scheduled_at", dateFormat.format(new Date(scheduledTime)));
                    response = api.scheduleVisit("schedule", body).execute();
                } else {
                    body.put("started_at", dateFormat.format(new Date(visit.startedAt)));
                    body.put("start_latitude", visit.startLatitude);
                    body.put("start_longitude", visit.startLongitude);
                    body.put("visit_source", visit.visitSource != null ? visit.visitSource : "GPS_DETECTED");
                    response = api.createVisit("create", body).execute();
                }
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> resBody = response.body();
                    boolean success = Boolean.TRUE.equals(resBody.get("success"));
                    if (success) {
                        String assignedServerId = extractServerId(resBody);
                        if (assignedServerId != null && !assignedServerId.isEmpty()) {
                            visitDao.updateVisitWithServerId(visit.visitId, assignedServerId, now);
                            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                            db.trackingDao().updateVisitId(visit.visitId, assignedServerId);
                            db.pendingOrderDao().updateVisitId(visit.visitId, assignedServerId);
                        } else {
                            visitDao.markSyncedWithServerId(visit.visitId, visit.visitId, now);
                        }
                    } else {
                        String msg = String.valueOf(resBody.get("message"));
                        visitDao.markFailed(visit.visitId, now, msg);
                    }
                } else {
                    int code = response.code();
                    if (code >= 500 || code == 408 || code == 429) {
                        return false; // Retryable transient error
                    } else if (code == 409) { // Duplicate / Idempotent
                        visitDao.markAsSynced(visit.visitId, now);
                    } else {
                        visitDao.markFailed(visit.visitId, now, "HTTP " + code + ": " + response.message());
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Network failure syncing visit creation " + visit.visitId, e);
                return false;
            }
        }
        return true;
    }

    private boolean syncPendingVisitTransitions() {
        List<CustomerVisit> pendingVisits = visitDao.getPendingVisits();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (CustomerVisit visit : pendingVisits) {
            long now = System.currentTimeMillis();
            String targetServerId = visit.serverId != null ? visit.serverId : visit.visitId;
            try {
                Response<Map<String, Object>> response = null;
                Map<String, Object> body = new HashMap<>();
                if ("COMPLETED".equals(visit.visitStatus)) {
                    body.put("ended_at", dateFormat.format(new Date(visit.endedAt != null ? visit
                                                                                .endedAt : now)));
                    body.put("end_latitude", visit.endLatitude != null ? visit.endLatitude : 0.0);
                    body.put("end_longitude", visit.endLongitude != null ? visit.endLongitude : 0.0);
                    body.put("notes", visit.notes);
                    response = api.endVisit("end", targetServerId, body).execute();
                } else if ("CANCELLED".equals(visit.visitStatus)) {
                    body.put("reason", visit.cancelReason != null ? visit.cancelReason : (visit
                            .notes != null ? visit.notes : "Cancelled by user"));
                    response = api.cancelVisit("cancel", targetServerId, body).execute();
                } else if ("POSTPONED".equals(visit.visitStatus)) {
                    long schedTime = visit.scheduledAt != null ? visit.scheduledAt : now;
                    body.put("scheduled_at", dateFormat.format(new Date(schedTime)));
                    body.put("notes", visit.notes);
                    response = api.postponeVisit("postpone", targetServerId, body).execute();
                }
                if (response != null) {
                    if (response.isSuccessful() && response.body() != null) {
                        visitDao.markAsSynced(visit.visitId, now);
                    } else {
                        int code = response.code();
                        if (code >= 500 || code == 408 || code == 429) {
                            return false;
                        } else {
                            visitDao.markFailed(visit.visitId, now, "HTTP " + code);
                        }
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Network failure syncing visit transition " + visit.visitId, e);
                return false;
            }
        }
        return true;
    }

    private boolean syncTrackingPoints() {
        final boolean[] success = {true};
        final Object lock = new Object();
        TrackingRepository trackingRepository = TrackingRepository
                .getInstance(getApplicationContext());
        trackingRepository.syncPendingRecords(new TrackingRepository.SyncCallback() {
            @Override
            public void onSuccess(int syncedCount) {
                synchronized (lock) {
                    success[0] = true;
                    lock.notify();
                }
            }

            @Override
            public void onError(String message, boolean isTransient) {
                synchronized (lock) {
                    success[0] = !isTransient;
                    lock.notify();
                }
            }
        });
        synchronized (lock) {
            try {
                lock.wait(2 * 60 * 1000); // 2 min max wait
            } catch (InterruptedException e) {
                return false;
            }
        }
        return success[0];
    }

    private boolean syncPendingOrders() {
        List<PendingOrder> pendingOrders = orderDao.getPendingOrders();
        for (PendingOrder order : pendingOrders) {
            long now = System.currentTimeMillis();
            Map<String, Object> payload = new HashMap<>();
            payload.put("order_uuid", order.orderUuid);
            payload.put("sales_man_id", order.userId);
            payload.put("CustomerCode", order.customerId);
            payload.put("visit_id", order.visitId);
            payload.put("OrderDate", order.orderDate);
            payload.put("TotalAmount", order.totalAmount);
            payload.put("VatAmount", order.vatAmount);
            payload.put("DiscountAmount", order.discountAmount);
            if (order.latitude != null) payload.put("latitude", order.latitude);
            if (order.longitude != null) payload.put("longitude", order.longitude);
            try {
                List<Map<String, Object>> lines = gson.fromJson(order.linesJson,
                        new TypeToken<List<Map<String, Object>>>() {}.getType());
                payload.put("Lines", lines);
                Response<Map<String, Object>> response = api.createOrder("create",
                        payload).execute();
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> body = response.body();
                    boolean isSuccess = Boolean.TRUE.equals(body.get("success"));
                    if (isSuccess) {
                        orderDao.markSynced(order.orderUuid, now);
                    } else {
                        String msg = String.valueOf(body.get("message"));
                        orderDao.markFailed(order.orderUuid, now, msg);
                    }
                } else {
                    int code = response.code();
                    if (code >= 500 || code == 408 || code == 429) {
                        return false;
                    } else if (code == 409) { // Idempotent duplicate
                        orderDao.markSynced(order.orderUuid, now);
                    } else {
                        orderDao.markFailed(order.orderUuid, now, "HTTP " + code);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Network failure syncing order " + order.orderUuid, e);
                return false;
            }
        }
        return true;
    }

    private void refreshCache() {
        try {
            ProductRepository productRepository = new ProductRepository(getApplicationContext());
            productRepository.syncProducts(null);
        } catch (Exception e) {
            Log.w(TAG, "Background cache refresh warning", e);
        }
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

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncCoordinatorWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30, TimeUnit.SECONDS)
                .addTag(WORK_NAME)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request);
    }
}
