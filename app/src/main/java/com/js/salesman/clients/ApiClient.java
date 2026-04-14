package com.js.salesman.clients;
import android.content.Context;

import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.utils.Db;
import com.js.salesman.utils.SettingsManager;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static Retrofit retrofit;
    private static String baseUrl;

    public static Retrofit getClient(Context context) {
        Context appContext = context.getApplicationContext();
        SettingsManager settings = new SettingsManager(appContext);
        
        Db db = new Db(appContext);
        HashMap<String, String> config = db.getConfig();
        String currentConfigUrl = config.get("url");
        String effectiveUrl = settings.getApiBaseUrl(currentConfigUrl);

        if (retrofit != null && baseUrl != null && baseUrl.equals(effectiveUrl)) {
            return retrofit;
        }

        baseUrl = effectiveUrl;
        if (baseUrl == null) {
            baseUrl = "http://localhost/"; // Fallback
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    okhttp3.Request originalRequest = chain.request();
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

    public static ApiInterface getApi(Context context) {
        return getClient(context).create(ApiInterface.class);
    }

    public static String getBaseUrl() {
        return baseUrl;
    }
}