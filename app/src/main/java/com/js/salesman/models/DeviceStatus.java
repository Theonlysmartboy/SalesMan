package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Lifecycle state of a device as reported by the server (user_devices.status).
 */
public enum DeviceStatus {
    PENDING,
    APPROVED,
    SUSPENDED,
    REVOKED,
    UNKNOWN;

    @NonNull
    public static DeviceStatus fromServer(@Nullable String value) {
        if (value == null) return UNKNOWN;
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "pending":
                return PENDING;
            case "approved":
                return APPROVED;
            case "suspended":
                return SUSPENDED;
            case "revoked":
                return REVOKED;
            default:
                return UNKNOWN;
        }
    }
}
