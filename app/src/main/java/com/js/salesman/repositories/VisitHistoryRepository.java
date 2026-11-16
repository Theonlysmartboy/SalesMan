package com.js.salesman.repositories;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.VisitHistoryFilter;
import com.js.salesman.models.VisitHistoryPage;
import com.js.salesman.models.responses.VisitHistoryResponse;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.VisitHistoryMapper;
import com.js.salesman.utils.managers.LogManager;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Loads the paged visit history from the server. Callbacks arrive on the main thread.
 */
public class VisitHistoryRepository {

    private static final String LOG_TAG = "VISITS";
    private static final String ACTION_HISTORY = "history";

    public interface PageCallback {
        void onSuccess(@NonNull VisitHistoryPage page);
        /**
         * @param networkError true when the server could not be reached (as opposed to the
         *                     server answering with an error)
         */
        void onError(@NonNull String message, boolean networkError);
    }

    private final Context appContext;

    public VisitHistoryRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Requests one page. The returned call can be cancelled; a cancelled call never reports back.
     * @param offset number of visits to skip (page start)
     * @param limit  page size
     */
    @NonNull
    public Call<VisitHistoryResponse> loadPage(@NonNull String userId,
                                                @NonNull VisitHistoryFilter filter,
                                                int offset, int limit,
                                                @NonNull PageCallback callback) {
        Call<VisitHistoryResponse> call = ApiClient.getClient(appContext)
                .create(ApiInterface.class)
                .getHistory(ACTION_HISTORY, userId, filter.statusParam(),
                        filter.getFrom(), filter.getTo(), limit, offset);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<VisitHistoryResponse> c,
                                    @NonNull Response<VisitHistoryResponse> response) {
                VisitHistoryResponse body = response.body();
                if (response.isSuccessful() && body != null && body.success) {
                    callback.onSuccess(VisitHistoryMapper.toPage(body));
                    return;
                }
                String message = body != null && notBlank(body.message)
                        ? body.message
                        : parseErrorMessage(response);
                LogManager.logError(appContext, LOG_TAG, "History request rejected: " + message,
                        new Exception(message));
                callback.onError(message, false);
            }

            @Override
            public void onFailure(@NonNull Call<VisitHistoryResponse> c, @NonNull Throwable t) {
                if (c.isCanceled()) return;
                LogManager.logError(appContext, LOG_TAG, "History request failed", t);
                callback.onError(NetworkUtil.getFriendlyNetError(appContext, t, false),
                        t instanceof IOException);
            }
        });
        return call;
    }

    @NonNull
    private static String parseErrorMessage(@NonNull Response<?> response) {
        String fallback = "Failed to load visits (" + response.code() + ")";
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody == null) return fallback;
            JsonObject root = new Gson().fromJson(errorBody.string(), JsonObject.class);
            if (root != null && root.has("message") && root.get("message").isJsonPrimitive()) {
                String message = root.get("message").getAsString();
                return notBlank(message) ? message : fallback;
            }
        } catch (Exception ignored) {
            // fall through to the generic message
        }
        return fallback;
    }

    private static boolean notBlank(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }
}