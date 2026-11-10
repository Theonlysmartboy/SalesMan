package com.js.salesman.models;

import com.google.gson.annotations.SerializedName;

/**
 * {@code data} payload of {@code devices.php?action=register}.
 */
public class DeviceRegisterData {
    @SerializedName("device_id")
    public long deviceId;
    public String status;
    @SerializedName("requires_approval")
    public boolean requiresApproval;
    public String message;
}
