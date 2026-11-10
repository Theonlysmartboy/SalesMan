package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Body of {@code POST devices.php?action=register}.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"}) // fields are read reflectively by Gson
public final class DeviceRegisterRequest {

    private final String username;
    private final String password;
    @SerializedName("device_uuid")
    private final String deviceUuid;
    @SerializedName("public_key")
    private final String publicKey;
    @SerializedName("device_name")
    private final String deviceName;
    private final String manufacturer;
    private final String model;
    @SerializedName("android_version")
    private final String androidVersion;
    @SerializedName("installation_version")
    private final String installationVersion;
    @SerializedName("device_fingerprint")
    private final String deviceFingerprint;

    private DeviceRegisterRequest(@NonNull Builder b) {
        this.username = b.username;
        this.password = b.password;
        this.deviceUuid = b.deviceUuid;
        this.publicKey = b.publicKey;
        this.deviceName = b.deviceName;
        this.manufacturer = b.manufacturer;
        this.model = b.model;
        this.androidVersion = b.androidVersion;
        this.installationVersion = b.installationVersion;
        this.deviceFingerprint = b.deviceFingerprint;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String username;
        private String password;
        private String deviceUuid;
        private String publicKey;
        private String deviceName;
        private String manufacturer;
        private String model;
        private String androidVersion;
        private String installationVersion;
        private String deviceFingerprint;

        private Builder() {
        }

        public Builder username(@NonNull String value) {
            this.username = value;
            return this;
        }

        public Builder password(@NonNull String value) {
            this.password = value;
            return this;
        }

        public Builder deviceUuid(@NonNull String value) {
            this.deviceUuid = value;
            return this;
        }

        public Builder publicKey(@NonNull String value) {
            this.publicKey = value;
            return this;
        }

        public Builder deviceName(@Nullable String value) {
            this.deviceName = value;
            return this;
        }

        public Builder manufacturer(@Nullable String value) {
            this.manufacturer = value;
            return this;
        }

        public Builder model(@Nullable String value) {
            this.model = value;
            return this;
        }

        public Builder androidVersion(@Nullable String value) {
            this.androidVersion = value;
            return this;
        }

        public Builder installationVersion(@Nullable String value) {
            this.installationVersion = value;
            return this;
        }

        public Builder deviceFingerprint(@Nullable String value) {
            this.deviceFingerprint = value;
            return this;
        }

        @NonNull
        public DeviceRegisterRequest build() {
            if (isBlank(username) || isBlank(password) || isBlank(deviceUuid) || isBlank(publicKey)) {
                throw new IllegalStateException(
                        "username, password, deviceUuid and publicKey are required");
            }
            return new DeviceRegisterRequest(this);
        }

        private static boolean isBlank(@Nullable String s) {
            return s == null || s.trim().isEmpty();
        }
    }
}
