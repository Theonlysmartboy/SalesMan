package com.js.salesman.data.repo;

import androidx.annotation.NonNull;

import com.js.salesman.data.api.ApiClient;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AuthRepository {

    public interface LoginCallback {
        void onSuccess(String token, JSONObject user);
        void onError(String message);
    }

    public void login(String baseUrl, String username, String password, LoginCallback callback) {

        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("userName", username);
            jsonBody.put("password", password);
            RequestBody body = RequestBody.create(
                    jsonBody.toString(),
                    MediaType.parse("application/json")
            );
            HttpUrl url = Objects.requireNonNull(HttpUrl.parse(baseUrl))
                    .newBuilder()
                    .addQueryParameter("action", "login")
                    .build();
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            ApiClient.getClient().newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    callback.onError("Network error");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response)
                        throws IOException {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        boolean success = json.getBoolean("success");
                        if (success) {
                            JSONObject data = json.getJSONObject("data");
                            String token = data.getString("token");
                            JSONObject user = data.getJSONObject("user");
                            callback.onSuccess(token, user);
                        } else {
                            callback.onError(json.getString("message"));
                        }
                    } catch (Exception e) {
                        callback.onError("Invalid server response");
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request build failed");
        }
    }
}
