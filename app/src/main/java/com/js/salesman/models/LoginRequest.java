package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Body of {@code POST auth.php?action=login}.
 * <p>
 * The device fields are optional: when {@code null} they are omitted from the JSON
 * (Gson skips nulls), which is what the server sees from a client without device proof.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"}) // fields are read reflectively by Gson
public class LoginRequest {
    private final String userName;
    private final String password;
    @SerializedName("device_uuid")
    private final String deviceUuid;
    @SerializedName("challenge_id")
    private final String challengeId;
    private final String signature;

    public LoginRequest(String userName, String password) {
        this(userName, password, null);
    }

    public LoginRequest(String userName, String password, @Nullable DeviceProof proof) {
        this.userName = userName;
        this.password = password;
        this.deviceUuid = proof != null ? proof.getDeviceUuid() : null;
        this.challengeId = proof != null ? proof.getChallengeId() : null;
        this.signature = proof != null ? proof.getSignature() : null;
    }

    @NonNull
    public String getUserName() {
        return userName;
    }
}
