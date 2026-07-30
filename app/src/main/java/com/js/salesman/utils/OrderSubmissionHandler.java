package com.js.salesman.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.Customer;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.utils.managers.SessionManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderSubmissionHandler {

    public interface SubmissionCallback {
        void onStart();
        void onSuccess(String message);
        void onFailure(String error);
        void onFinish();
    }

    public static void submitOrder(Context context, Customer customer, List<Map<String, Object>> lines, 
                                 double total, double vat, double discount, SubmissionCallback callback) {
        if (callback != null) callback.onStart();

        Map<String, Object> payload = new HashMap<>();
        SessionManager session = new SessionManager(context);
        
        payload.put("sales_man_id", session.getUserId());
        payload.put("CustomerCode", customer.getSrNo()); // Using SrNo as per CheckoutFragment logic
        payload.put("OrderDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        
        // Add header fields that might be expected based on instructions
        payload.put("TotalAmount", total);
        payload.put("VatAmount", vat);
        payload.put("DiscountAmount", discount);
        
        // Add location if available
        if (session.getCachedLat() != null) payload.put("latitude", session.getCachedLat());
        if (session.getCachedLng() != null) payload.put("longitude", session.getCachedLng());

        payload.put("Lines", lines);

        ApiInterface api = ApiClient.getClient(context).create(ApiInterface.class);
        api.createOrder("create", payload).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                String message = "Unknown error";
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        Map<String, Object> body = response.body();
                        if (body.containsKey("message")) {
                            message = Objects.requireNonNull(body.get("message")).toString();
                        }
                        boolean success = false;
                        if (body.containsKey("success")) {
                            Object successObj = body.get("success");
                            if (successObj instanceof Boolean) {
                                success = (Boolean) successObj;
                            } else if (successObj != null) {
                                success = Boolean.parseBoolean(successObj.toString());
                            }
                        }
                        if (success) {
                            if (callback != null) callback.onSuccess(message);
                        } else {
                            if (callback != null) callback.onFailure(message);
                        }
                    } else {
                        message = parseError(response);
                        if (callback != null) callback.onFailure(message);
                    }
                } catch (Exception e) {
                    LogManager.logError(context, "OrderSubmissionHandler", "Error parsing response", e);
                    if (callback != null) callback.onFailure("Parsing error");
                } finally {
                    if (callback != null) callback.onFinish();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                LogManager.logError(context, "OrderSubmissionHandler", "Network call failed", t);
                if (callback != null) callback.onFailure("Network error");
                if (callback != null) callback.onFinish();
            }
        });
    }

    private static String parseError(Response<?> response) {
        String message = "Server error: " + response.code();
        ResponseBody errorBody = response.errorBody();
        if (errorBody != null) {
            try (ResponseBody body = errorBody) {
                String errorJson = body.string();
                JSONObject json = new JSONObject(errorJson);
                if (json.has("message")) {
                    message = json.getString("message");
                }
            } catch (Exception e) {
                // Ignore parsing errors and keep default message
            }
        }
        return message;
    }
}
