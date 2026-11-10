package com.js.salesman.models;

import com.google.gson.annotations.SerializedName;

/**
 * {@code data} payload of {@code devices.php?action=challenge}.
 */
public class DeviceChallengeData {
    @SerializedName("challenge_id")
    public String challengeId;
    public String challenge;
    @SerializedName("expires_in")
    public int expiresIn;
}
