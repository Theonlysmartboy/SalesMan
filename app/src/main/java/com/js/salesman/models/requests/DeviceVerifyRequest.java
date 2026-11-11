package com.js.salesman.models.requests;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * Body of {@code POST devices.php?action=verify}: the signed challenge that proves
 * this phone still holds its registered key.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"}) // fields are read reflectively by Gson
public final class DeviceVerifyRequest {

    private final String username;
    @SerializedName("device_uuid")
    private final String deviceUuid;
    @SerializedName("challenge_id")
    private final String challengeId;
    private final String signature;

    public DeviceVerifyRequest(@NonNull String username, @NonNull String deviceUuid,
                                @NonNull String challengeId, @NonNull String signature) {
        this.username = username;
        this.deviceUuid = deviceUuid;
        this.challengeId = challengeId;
        this.signature = signature;
    }
}