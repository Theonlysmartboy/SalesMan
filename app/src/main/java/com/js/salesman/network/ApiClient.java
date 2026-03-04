package com.js.salesman.network;

import android.content.Context;

import com.js.salesman.data.api.ApiService;
import com.js.salesman.utils.Db;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static Retrofit retrofit;

    public static Retrofit getClient(Context context) {
        if (retrofit != null) {
            return retrofit;
        }
        if (context == null) {
            throw new RuntimeException("Context is null");
        }
        // Always use application context to avoid leaks
        Context appContext = context.getApplicationContext();
        // Get base URL from DB
        Db db = new Db(appContext);
        HashMap<String, String> config = db.getConfig();
        if (config == null || !config.containsKey("url")) {
            throw new RuntimeException("Base URL not configured in DB");
        }
        String baseUrl = config.get("url");
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new RuntimeException("Base URL empty");
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        // Logging interceptor
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY); // change to NONE for release
        // OkHttp client
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                // Authorization Interceptor
                .addInterceptor(chain -> {
                    okhttp3.Request originalRequest = chain.request();
                    // Skip login endpoint
                    if (originalRequest.url().encodedPath().contains("auth.php")) {
                        return chain.proceed(originalRequest);
                    }
                    Db dbHelper = new Db(appContext);
                    String token = dbHelper.getToken();
                    okhttp3.Request.Builder builder = originalRequest.newBuilder();
                    if (token != null && !token.isEmpty()) {
                        builder.addHeader("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .addInterceptor(logging)
                .build();
        retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit;
    }

    public static ApiService getApi(Context context) {
        return getClient(context).create(ApiService.class);
    }
}