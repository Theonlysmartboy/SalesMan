package com.js.salesman.data.api;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class ApiClient {

    private static OkHttpClient client;

    public static OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }
}
