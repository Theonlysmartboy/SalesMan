package com.js.salesman.models;

import androidx.annotation.NonNull;

/**
 * Immutable snapshot of this phone's cryptographic identity.
 */
public final class DeviceIdentity {

    private final String deviceUuid;
    private final String publicKeyBase64;
    private final String keyFingerprint;

    public DeviceIdentity(@NonNull String deviceUuid, @NonNull String publicKeyBase64,
                            @NonNull String keyFingerprint) {
        this.deviceUuid = deviceUuid;
        this.publicKeyBase64 = publicKeyBase64;
        this.keyFingerprint = keyFingerprint;
    }

    /** 64-character lowercase hex identifier sent as {@code device_uuid}. */
    @NonNull
    public String getDeviceUuid() {
        return deviceUuid;
    }

    /** X.509 SubjectPublicKeyInfo (DER), Base64 without wrapping; sent as {@code public_key}. */
    @NonNull
    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    /** SHA-256 (hex) of the public key; used locally to know which key the server holds. */
    @NonNull
    public String getKeyFingerprint() {
        return keyFingerprint;
    }
}
