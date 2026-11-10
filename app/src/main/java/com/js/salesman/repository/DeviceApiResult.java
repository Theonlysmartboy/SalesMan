package com.js.salesman.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

/**
 * Result of a device API call. Expected failures are values, not exceptions.
 *
 * @param <T> type of the success payload
 */
public final class DeviceApiResult<T> {

    private final boolean success;
    private final int httpCode;
    private final String message;
    private final T data;
    private final IOException networkError;

    private DeviceApiResult(boolean success, int httpCode, @Nullable String message,
                            @Nullable T data, @Nullable IOException networkError) {
        this.success = success;
        this.httpCode = httpCode;
        this.message = message;
        this.data = data;
        this.networkError = networkError;
    }

    @NonNull
    public static <T> DeviceApiResult<T> success(int httpCode, @Nullable String message,
                                                    @NonNull T data) {
        return new DeviceApiResult<>(true, httpCode, message, data, null);
    }

    @NonNull
    public static <T> DeviceApiResult<T> failure(int httpCode, @Nullable String message) {
        return new DeviceApiResult<>(false, httpCode, message, null, null);
    }

    @NonNull
    public static <T> DeviceApiResult<T> networkFailure(@NonNull IOException error) {
        return new DeviceApiResult<>(false, -1, error.getMessage(), null, error);
    }

    public boolean isSuccess() {
        return success;
    }

    /** True when no HTTP response was obtained (offline, DNS failure, timeout). */
    public boolean isNetworkError() {
        return networkError != null;
    }

    public int getHttpCode() {
        return httpCode;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    /** Non-null whenever {@link #isSuccess()} is true. */
    @Nullable
    public T getData() {
        return data;
    }

    @Nullable
    public IOException getNetworkError() {
        return networkError;
    }
}
