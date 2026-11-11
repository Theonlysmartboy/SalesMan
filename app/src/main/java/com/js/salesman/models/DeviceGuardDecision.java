package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An action the UI must take after a device check. "Everything is fine" is never delivered.
 */
public final class DeviceGuardDecision {

    public enum Type {
        /** The device is not (or no longer) authorised: tell the user and sign out. */
        BLOCK,
        /** The phone has been offline too long: the user must connect so it can be verified. */
        VERIFICATION_REQUIRED
    }

    private final Type type;
    private final String message;

    private DeviceGuardDecision(@NonNull Type type, @Nullable String message) {
        this.type = type;
        this.message = message;
    }

    @NonNull
    public static DeviceGuardDecision block(@Nullable String message) {
        return new DeviceGuardDecision(Type.BLOCK, message);
    }

    @NonNull
    public static DeviceGuardDecision verificationRequired() {
        return new DeviceGuardDecision(Type.VERIFICATION_REQUIRED, null);
    }

    @NonNull
    public Type getType() {
        return type;
    }

    /** User-facing text for {@link Type#BLOCK}; may be null. */
    @Nullable
    public String getMessage() {
        return message;
    }
}