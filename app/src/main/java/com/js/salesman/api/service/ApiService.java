package com.js.salesman.api.service;

import com.js.salesman.models.LoginRequest;
import com.js.salesman.models.LoginResponse;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.models.ProductResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    // action=login is required by your backend
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
}
