package com.js.salesman.data.api;

import com.js.salesman.models.LoginRequest;
import com.js.salesman.models.LoginResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    // action=login is required by your backend
    @POST("api/auth.php")
    Call<LoginResponse> login(
            @Query("action") String action,
            @Body LoginRequest request
    );
}
