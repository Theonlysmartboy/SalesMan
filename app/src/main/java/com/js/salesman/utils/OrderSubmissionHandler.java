package com.js.salesman.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.interfaces.CustomerVisitDao;
import com.js.salesman.interfaces.PendingOrderDao;
import com.js.salesman.models.Customer;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.models.PendingOrder;
import com.js.salesman.utils.database.AppDatabase;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.workers.SyncCoordinatorWorker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class OrderSubmissionHandler {

    private static final String TAG = "OrderSubmissionHandler";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface SubmissionCallback {
        void onStart();
        void onSuccess(String message);
        void onFailure(String error);
        void onFinish();
    }

    public static void submitOrder(Context context, Customer customer, List<Map<String, Object>> lines,
                                 double total, double vat, double discount, SubmissionCallback callback) {
        if (callback != null) callback.onStart();

        executor.execute(() -> {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            try {
                Context appContext = context.getApplicationContext();
                SessionManager session = new SessionManager(appContext);
                AppDatabase db = AppDatabase.getInstance(appContext);
                CustomerVisitDao visitDao = db.customerVisitDao();
                PendingOrderDao orderDao = db.pendingOrderDao();

                String userId = session.getUserId();
                if (userId == null || userId.isEmpty()) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure("User ID unavailable.");
                            callback.onFinish();
                        }
                    });
                    return;
                }

                CustomerVisit activeVisit = visitDao.getActiveVisit(userId);
                if (activeVisit == null) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure("No active customer visit. You must start a visit before creating an order.");
                            callback.onFinish();
                        }
                    });
                    return;
                }

                String activeVisitId = activeVisit.serverId != null && !activeVisit.serverId.isEmpty() ? activeVisit.serverId : activeVisit.visitId;

                PendingOrder order = new PendingOrder();
                order.orderUuid = UUID.randomUUID().toString();
                order.userId = userId;
                order.customerId = customer.getSrNo() != null ? customer.getSrNo() : (customer.getCustomerCode() != null ? customer.getCustomerCode() : "");
                order.visitId = activeVisitId;
                order.totalAmount = total;
                order.vatAmount = vat;
                order.discountAmount = discount;
                order.latitude = session.getCachedLat();
                order.longitude = session.getCachedLng();
                order.linesJson = new Gson().toJson(lines);
                order.orderDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                order.syncStatus = "PENDING";

                orderDao.insert(order);
                LogManager.log(appContext, "ORDER_SUBMIT_LOCAL", "Order queued locally: " + order.orderUuid + " (Visit: " + activeVisitId + ")");

                // Immediate Online Submission
                if (NetworkUtil.isNetworkAvailable(appContext)) {
                    try {
                        ApiInterface api = ApiClient.getApi(appContext);
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("order_uuid", order.orderUuid);
                        payload.put("sales_man_id", userId);
                        payload.put("CustomerCode", order.customerId);
                        payload.put("visit_id", activeVisitId);
                        payload.put("OrderDate", order.orderDate);
                        payload.put("TotalAmount", total);
                        payload.put("VatAmount", vat);
                        payload.put("DiscountAmount", discount);
                        if (order.latitude != null) payload.put("latitude", order.latitude);
                        if (order.longitude != null) payload.put("longitude", order.longitude);
                        payload.put("Lines", lines);

                        Response<Map<String, Object>> res = api.createOrder("create", payload).execute();
                        if (res.isSuccessful() && res.body() != null && Boolean.TRUE.equals(res.body().get("success"))) {
                            orderDao.markSynced(order.orderUuid, System.currentTimeMillis());
                            order.syncStatus = "SYNCED";
                        } else {
                            SyncCoordinatorWorker.enqueue(appContext);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Immediate online order submission failed, queued for background sync", e);
                        SyncCoordinatorWorker.enqueue(appContext);
                    }
                } else {
                    SyncCoordinatorWorker.enqueue(appContext);
                }

                mainHandler.post(() -> {
                    if (callback != null) {
                        String msg = "SYNCED".equals(order.syncStatus) ? "Order placed and submitted successfully." : "Order placed locally and queued for synchronization.";
                        callback.onSuccess(msg);
                        callback.onFinish();
                    }
                });

            } catch (Exception e) {
                LogManager.logError(context, "OrderSubmissionHandler", "Failed to queue order locally", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure("Failed to record order locally: " + e.getMessage());
                        callback.onFinish();
                    }
                });
            }
        });
    }
}
