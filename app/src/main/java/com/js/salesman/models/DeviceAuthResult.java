package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Outcome of the device check that runs before a login request is sent.
 * Only {@link State#READY} carries a {@link DeviceProof}.
 */
public final class DeviceAuthResult {

    public enum State {
        /** Device is approved and a signed challenge is available. */
        READY,
        /** Device is registered but an administrator has not approved it yet. */
        PENDING_APPROVAL,
        /** An administrator suspended this device. */
        SUSPENDED,
        /** Server refused the request (bad credentials, revoked, bound to another user...). */
        REJECTED,
        /** The server could not be reached. */
        NETWORK_ERROR,
        /** The phone's Keystore key could not be created or used. */
        DEVICE_ERROR
    }

    private final State state;
    private final DeviceProof proof;
    private final String message;
    private final Throwable error;

    private DeviceAuthResult(@NonNull State state, @Nullable DeviceProof proof,
                                @Nullable String message, @Nullable Throwable error) {
        this.state = state;
        this.proof = proof;
        this.message = message;
        this.error = error;
    }

    @NonNull
    public static DeviceAuthResult ready(@NonNull DeviceProof proof) {
        return new DeviceAuthResult(State.READY, proof, null, null);
    }

    @NonNull
    public static DeviceAuthResult pendingApproval() {
        return new DeviceAuthResult(State.PENDING_APPROVAL, null, null, null);
    }

    @NonNull
    public static DeviceAuthResult suspended() {
        return new DeviceAuthResult(State.SUSPENDED, null, null, null);
    }

    @NonNull
    public static DeviceAuthResult rejected(@Nullable String serverMessage) {
        return new DeviceAuthResult(State.REJECTED, null, serverMessage, null);
    }

    @NonNull
    public static DeviceAuthResult networkError(@NonNull Throwable error) {
        return new DeviceAuthResult(State.NETWORK_ERROR, null, error.getMessage(), error);
    }

    @NonNull
    public static DeviceAuthResult deviceError(@Nullable String message, @Nullable Throwable error) {
        return new DeviceAuthResult(State.DEVICE_ERROR, null, message, error);
    }

    @NonNull
    public State getState() {
        return state;
    }

    /** Present only when {@link #getState()} is {@link State#READY}. */
    @Nullable
    public DeviceProof getProof() {
        return proof;
    }

    /** Server or diagnostic message; may be null. */
    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public Throwable getError() {
        return error;
    }
}
