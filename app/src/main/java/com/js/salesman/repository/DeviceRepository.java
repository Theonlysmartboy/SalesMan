package com.js.salesman.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.DeviceApiInterface;
import com.js.salesman.models.DeviceApiResponse;
import com.js.salesman.models.DeviceChallengeData;
import com.js.salesman.models.DeviceChallengeRequest;
import com.js.salesman.models.DeviceRegisterData;
import com.js.salesman.models.DeviceRegisterRequest;
import com.js.salesman.utils.managers.LogManager;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Talks to {@code devices.php}. All methods are blocking and must run on a background thread.
 */
public class DeviceRepository {

    private static final String LOG_TAG = "DEVICE";
    private static final String ACTION_REGISTER = "register";
    private static final String ACTION_CHALLENGE = "challenge";
    private static final String DEFAULT_ERROR = "Request failed";

    private static volatile DeviceRepository INSTANCE;
    private final Context appContext;

    private DeviceRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    public static DeviceRepository getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (DeviceRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DeviceRepository(context);
                }
            }
        }
        return INSTANCE;
    }

    /** Registers this phone (idempotent for the same device and key). */
    @WorkerThread
    @NonNull
    public DeviceApiResult<DeviceRegisterData> register(@NonNull DeviceRegisterRequest request) {
        return execute("register", api().register(ACTION_REGISTER, request));
    }

    /** Requests a one-time login challenge for an approved device. */
    @WorkerThread
    @NonNull
    public DeviceApiResult<DeviceChallengeData> requestChallenge(@NonNull String username,
                                                                 @NonNull String deviceUuid) {
        return execute("challenge",
                api().challenge(ACTION_CHALLENGE, new DeviceChallengeRequest(username, deviceUuid)));
    }

    // ------------------------------------------------------------------

    private DeviceApiInterface api() {
        return ApiClient.getClient(appContext).create(DeviceApiInterface.class);
    }

    @NonNull
    private <T> DeviceApiResult<T> execute(@NonNull String operation,
                                            @NonNull Call<DeviceApiResponse<T>> call) {
        try {
            Response<DeviceApiResponse<T>> response = call.execute();
            DeviceApiResponse<T> body = response.body();
            if (response.isSuccessful() && body != null) {
                if (body.success && body.data != null) {
                    return DeviceApiResult.success(response.code(), body.message, body.data);
                }
                return DeviceApiResult.failure(response.code(),
                        orDefault(body.message, DEFAULT_ERROR));
            }
            return DeviceApiResult.failure(response.code(), parseErrorMessage(response));
        } catch (IOException e) {
            LogManager.logError(appContext, LOG_TAG, operation + " failed (network)", e);
            return DeviceApiResult.networkFailure(e);
        } catch (RuntimeException e) {
            // e.g. the server returned HTML / malformed JSON
            LogManager.logError(appContext, LOG_TAG, operation + " returned an unreadable response", e);
            return DeviceApiResult.failure(-1, "Unexpected response from server");
        }
    }

    @NonNull
    private static String parseErrorMessage(@NonNull Response<?> response) {
        String fallback = DEFAULT_ERROR + " (" + response.code() + ")";
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody == null) return fallback;
            JsonObject root = new Gson().fromJson(errorBody.string(), JsonObject.class);
            if (root != null) {
                JsonElement message = root.get("message");
                if (message != null && message.isJsonPrimitive()) {
                    return orDefault(message.getAsString(), fallback);
                }
            }
        } catch (Exception ignored) {
            // fall through to the generic message
        }
        return fallback;
    }

    @NonNull
    private static String orDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }
}
