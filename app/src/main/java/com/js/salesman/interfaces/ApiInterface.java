package com.js.salesman.interfaces;

import com.js.salesman.models.ApiResponse;
import com.js.salesman.models.Customer;
import com.js.salesman.models.LoginRequest;
import com.js.salesman.models.LoginResponse;
import com.js.salesman.models.Order;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.models.ProductResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiInterface {
    @POST("api/tracking.php")
    Call<Void> sendLocation(
            @Query("action") String action,
            @Body Map<String, Object> locationData);

    @POST("api/auth.php")
    Call<LoginResponse> login(
            @Query("action") String action,
            @Body LoginRequest request
    );

    @POST("api/auth.php")
    Call<Map<String, Object>> setHasPin(
            @Query("action") String action,
            @Body Map<String, Object> payload
    );

    @POST("api/auth.php")
    Call<Map<String, Object>> requestPasswordReset(
            @Query("action") String action,
            @Body Map<String, Object> body
    );

    @POST("api/auth.php")
    Call<Map<String, Object>> resetPassword(
            @Query("action") String action,
            @Body Map<String, Object> body
    );

    @GET("api/products.php")
    Call<ProductListResponse> syncProducts(
            @Query("action") String action,
            @Query("lastSync") String lastSync,
            @Query("limit") int limit,
            @Query("offset") int offset,
            @Query("lat") Double lat,
            @Query("lng") Double lng
    );
    @GET("api/products.php")
    Call<ProductListResponse> searchProducts(
            @Query("action") String action,
            @Query("q") String query,
            @Query("lat") Double lat,
            @Query("lng") Double lng
    );
    @GET("api/products.php")
    Call<ProductResponse> getProductDetails(
            @Query("action") String action,
            @Query("code") String code,
            @Query("lat") Double lat,
            @Query("lng") Double lng
    );

    @GET("api/customers.php")
    Call<ApiResponse<Customer>> syncCustomers(
            @Query("action") String action,
            @Query("lastSync") String lastSync,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    @POST("api/customers.php")
    Call<ApiResponse<Customer>> searchCustomers(
            @Query("action") String action,
            @Body Map<String, Object> searchData
    );

    @POST("api/customers.php")
    Call<Map<String, Object>> createCustomer(
            @Query("action") String action,
            @Body Map<String, Object> customerData
    );

    @POST("api/orders.php")
    Call<Map<String, Object>> createOrder(
            @Query("action") String action,
            @Body Map<String, Object> orderData
    );

    @GET("api/orders.php")
    Call<ApiResponse<Order>> filterOrders(
            @Query("action") String action,
            @Query("salesman") String salesmanCode,
            @Query("product") String productCode,
            @Query("customer") String customerSrNo,
            @Query("date_from") String dateFrom
    );

    @GET("api/orders.php")
    Call<com.js.salesman.models.OrderDetailsResponse> getOrderDetails(
            @Query("action") String action,
            @Query("OrderNumber") String orderNo
    );

    @GET("api/orders.php")
    Call<Map<String, Object>> getSalesReport(
            @Query("action") String action,
            @Query("salesman") String salesman,
            @Query("month") String month,
            @Query("product") String product,
            @Query("customer") String customer
    );
}
