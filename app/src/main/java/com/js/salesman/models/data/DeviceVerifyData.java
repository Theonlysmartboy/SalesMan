package com.js.salesman.models.data;

import com.google.gson.annotations.SerializedName;

/**
 * {@code data} payload of {@code devices.php?action=verify}.
 */
public class DeviceVerifyData {
    @SerializedName("device_id")
    public long deviceId;
    public String status;
    @SerializedName("verified_at")
    public String verifiedAt;
}