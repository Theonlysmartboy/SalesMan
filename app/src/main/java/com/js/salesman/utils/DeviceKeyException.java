package com.js.salesman.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Thrown when the phone's device-binding key cannot be created, read or used.
 */
public class DeviceKeyException extends Exception {

    public DeviceKeyException(@NonNull String message) {
        super(message);
    }

    public DeviceKeyException(@NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
