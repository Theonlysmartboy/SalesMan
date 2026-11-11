package com.js.salesman.models.requests;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * Body of {@code POST devices.php?action=challenge}.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"}) // fields are read reflectively by Gson
public final class DeviceChallengeRequest {

    private final String username;
    @SerializedName("device_uuid")
    private final String deviceUuid;

    public DeviceChallengeRequest(@NonNull String username, @NonNull String deviceUuid) {
        this.username = username;
        this.deviceUuid = deviceUuid;
    }
}
