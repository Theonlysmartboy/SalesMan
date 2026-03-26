package com.js.salesman.api.service;

import com.js.salesman.models.LoginRequest;
import com.js.salesman.models.LoginResponse;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.models.ProductResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/tracking.php")
    Call<Void> sendLocation(@Query("action") String action,
                            @Body Map<String, Object> locationData);

    @POST("api/auth.php")
    Call<LoginResponse> login(
            @Query("action") String action,
            @Body LoginRequest request
    );
    @GET("api/products.php")
    Call<ProductListResponse> syncProducts(
            @Query("action") String action,
            @Query("lastSync") String lastSync,
            @Query("limit") int limit,
            @Query("offset") int offset
    );
    @GET("api/products.php")
    Call<ProductListResponse> searchProducts(
            @Query("action") String action,
            @Query("q") String query
    );
    @GET("api/products.php")
    Call<ProductResponse> getProductDetails(
            @Query("action") String action,
            @Query("code")String code
    );

    @GET("api/customers.php")
    Call<Map<String, Object>> syncCustomers(
            @Query("action") String action,
            @Query("lastSync") String lastSync,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    @GET("api/customers.php")
    Call<Map<String, Object>> searchCustomers(
            @Query("action") String action,
            @Query("q") String query
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
}
