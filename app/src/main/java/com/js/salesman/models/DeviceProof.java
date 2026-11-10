package com.js.salesman.models;

import androidx.annotation.NonNull;

/**
 * Proof of device possession attached to the login request:
 * {@code device_uuid}, {@code challenge_id} and the Base64 {@code signature} of the challenge.
 */
public final class DeviceProof {

    private final String deviceUuid;
    private final String challengeId;
    private final String signature;

    public DeviceProof(@NonNull String deviceUuid, @NonNull String challengeId,
                        @NonNull String signature) {
        this.deviceUuid = deviceUuid;
        this.challengeId = challengeId;
        this.signature = signature;
    }

    @NonNull
    public String getDeviceUuid() {
        return deviceUuid;
    }

    @NonNull
    public String getChallengeId() {
        return challengeId;
    }

    @NonNull
    public String getSignature() {
        return signature;
    }
}
