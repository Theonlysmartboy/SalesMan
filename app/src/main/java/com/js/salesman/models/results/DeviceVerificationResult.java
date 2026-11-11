package com.js.salesman.models.results;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Outcome of a post-login device verification (challenge, sign, verify).
 */
public final class DeviceVerificationResult {

    public enum State {
        /** Server confirmed the signature; the device is approved. */
        APPROVED,
        /** Server says the device or account is no longer authorised (revoked, suspended, deleted...). */
        NOT_AUTHORISED,
        /** The phone's Keystore key is gone (reinstall, restored backup...). */
        KEY_MISSING,
        /** No definite answer: offline, timeout, server error, transient Keystore failure. */
        INCONCLUSIVE
    }

    private final State state;
    private final String message;

    private DeviceVerificationResult(@NonNull State state, @Nullable String message) {
        this.state = state;
        this.message = message;
    }

    @NonNull
    public static DeviceVerificationResult approved() {
        return new DeviceVerificationResult(State.APPROVED, null);
    }

    @NonNull
    public static DeviceVerificationResult notAuthorised(@Nullable String message) {
        return new DeviceVerificationResult(State.NOT_AUTHORISED, message);
    }

    @NonNull
    public static DeviceVerificationResult keyMissing() {
        return new DeviceVerificationResult(State.KEY_MISSING, null);
    }

    @NonNull
    public static DeviceVerificationResult inconclusive(@Nullable String message) {
        return new DeviceVerificationResult(State.INCONCLUSIVE, message);
    }

    @NonNull
    public State getState() {
        return state;
    }

    @Nullable
    public String getMessage() {
        return message;
    }
}