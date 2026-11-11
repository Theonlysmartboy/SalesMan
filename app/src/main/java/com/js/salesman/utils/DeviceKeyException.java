package com.js.salesman.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Thrown when the phone's device-binding key cannot be created, read or used.
 * <p>
 * {@link #isPermanent()} separates a key that is gone for good (missing, unrecoverable) from a
 * transient Keystore failure that may succeed on the next attempt.
 */
public class DeviceKeyException extends Exception {

    private final boolean permanent;

    public DeviceKeyException(@NonNull String message) {
        this(message, null, false);
    }

    public DeviceKeyException(@NonNull String message, @Nullable Throwable cause) {
        this(message, cause, false);
    }

    public DeviceKeyException(@NonNull String message, @Nullable Throwable cause,
                                boolean permanent) {
        super(message, cause);
        this.permanent = permanent;
    }

    /** True when the key will never be usable again and a new one must be created. */
    public boolean isPermanent() {
        return permanent;
    }
}