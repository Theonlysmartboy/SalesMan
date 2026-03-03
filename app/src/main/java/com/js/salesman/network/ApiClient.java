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

        if (retrofit == null) {

            // 🔥 get base URL from DB
            Db db = new Db(context);
            HashMap<String, String> config = db.getConfig();

            String baseUrl = config.get("url");

            if (baseUrl == null || baseUrl.isEmpty()) {
                throw new RuntimeException("Base URL not configured in DB");
            }

            // ⚠️ must end with /
            if (!baseUrl.endsWith("/")) {
                baseUrl = baseUrl + "/";
            }

            // Logging interceptor
            HttpLoggingInterceptor logging =
                    new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit;
    }

    public static ApiService getApi(Context context) {
        return getClient(context).create(ApiService.class);
    }
}