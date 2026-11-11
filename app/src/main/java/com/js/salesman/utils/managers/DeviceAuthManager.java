package com.js.salesman.utils.managers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.js.salesman.models.results.DeviceAuthResult;
import com.js.salesman.models.data.DeviceChallengeData;
import com.js.salesman.models.DeviceIdentity;
import com.js.salesman.models.DeviceProof;
import com.js.salesman.models.data.DeviceRegisterData;
import com.js.salesman.models.requests.DeviceRegisterRequest;
import com.js.salesman.models.DeviceStatus;
import com.js.salesman.models.results.DeviceVerificationResult;
import com.js.salesman.models.data.DeviceVerifyData;
import com.js.salesman.models.requests.DeviceVerifyRequest;
import com.js.salesman.repository.DeviceApiResult;
import com.js.salesman.repository.DeviceRepository;
import com.js.salesman.utils.DeviceInfoCollector;
import com.js.salesman.utils.DeviceKeyException;

public final class DeviceAuthManager {
    private static final String LOG_TAG = "DEVICE";
    private static volatile DeviceAuthManager INSTANCE;
    private final Context appContext;
    private final DeviceIdentityManager identityManager;
    private final DeviceRepository repository;

    private DeviceAuthManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.identityManager = DeviceIdentityManager.getInstance(appContext);
        this.repository = DeviceRepository.getInstance(appContext);
    }

    @NonNull
    public static DeviceAuthManager getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (DeviceAuthManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DeviceAuthManager(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Prepares the proof that accompanies the login request. Blocking: call from a background thread.
     */
    @WorkerThread
    @NonNull
    public DeviceAuthResult prepareProof(@NonNull String username, @NonNull String password) {
        final String user = username.trim();
        final DeviceIdentity identity;
        try {
            identity = identityManager.getOrCreateIdentity();
        } catch (DeviceKeyException e) {
            LogManager.logError(appContext, LOG_TAG, "Device identity unavailable", e);
            return DeviceAuthResult.deviceError(e.getMessage(), e);
        }
        // The server must hold our current public key before it can verify our signatures.
        if (!identityManager.isKeyRegistered(identity.getKeyFingerprint())) {
            DeviceAuthResult blocked = registerOrExplain(user, password, identity);
            if (blocked != null) return blocked;
        }
        return requestAndSign(user, password, identity);
    }

    /**
     * Confirms with the server that this phone is still authorised: challenge, sign, verify.
     * Never creates a key, so a vanished key is reported instead of silently replaced.
     * Blocking: call from a background thread.
     */
    @WorkerThread
    @NonNull
    public DeviceVerificationResult verifyDevice(@NonNull String username) {
        final String user = username.trim();
        final String deviceUuid;
        try {
            if (!identityManager.hasKeyPair()) {
                return DeviceVerificationResult.keyMissing();
            }
            deviceUuid = identityManager.getDeviceUuid();
        } catch (DeviceKeyException e) {
            LogManager.logError(appContext, LOG_TAG, "Unable to inspect the device key", e);
            return DeviceVerificationResult.inconclusive(e.getMessage());
        }
        DeviceApiResult<DeviceChallengeData> challengeResult =
                repository.requestChallenge(user, deviceUuid);
        if (!challengeResult.isSuccess()) {
            return classifyFailure(challengeResult);
        }
        DeviceChallengeData challenge = challengeResult.getData();
        if (challenge == null || isBlank(challenge.challengeId) || isBlank(challenge.challenge)) {
            return DeviceVerificationResult.inconclusive("Invalid challenge received from the server");
        }
        final String signature;
        try {
            signature = identityManager.sign(challenge.challenge);
        } catch (DeviceKeyException e) {
            LogManager.logError(appContext, LOG_TAG, "Unable to sign the verification challenge", e);
            return e.isPermanent()
                    ? DeviceVerificationResult.keyMissing()
                    : DeviceVerificationResult.inconclusive(e.getMessage());
        }
        DeviceApiResult<DeviceVerifyData> verifyResult = repository.verifyDevice(
                new DeviceVerifyRequest(user, deviceUuid, challenge.challengeId, signature));
        if (!verifyResult.isSuccess()) {
            return classifyFailure(verifyResult);
        }
        return DeviceVerificationResult.approved();
    }

    /** 401/403 means "not authorised"; anything else (offline, 5xx, bad body) is inconclusive. */
    @NonNull
    private static DeviceVerificationResult classifyFailure(@NonNull DeviceApiResult<?> result) {
        if (!result.isNetworkError()
                && (result.getHttpCode() == 401 || result.getHttpCode() == 403)) {
            return DeviceVerificationResult.notAuthorised(result.getMessage());
        }
        return DeviceVerificationResult.inconclusive(result.getMessage());
    }

    // ------------------------------------------------------------------

    @NonNull
    private DeviceAuthResult requestAndSign(@NonNull String user, @NonNull String password,
                                            @NonNull DeviceIdentity identity) {
        DeviceApiResult<DeviceChallengeData> challengeResult =
                repository.requestChallenge(user, identity.getDeviceUuid());
        if (challengeResult.isNetworkError()) {
            assert challengeResult.getNetworkError() != null;
            return DeviceAuthResult.networkError(challengeResult.getNetworkError());
        }
        if (!challengeResult.isSuccess()) {
            // The server only issues challenges to approved devices. Ask it for the device's
            // status (register is idempotent) so the user gets a precise explanation.
            DeviceAuthResult explained = registerOrExplain(user, password, identity);
            if (explained != null) return explained;
            return DeviceAuthResult.rejected(challengeResult.getMessage());
        }
        DeviceChallengeData challenge = challengeResult.getData();
        if (challenge == null || isBlank(challenge.challengeId) || isBlank(challenge.challenge)) {
            return DeviceAuthResult.rejected("Invalid challenge received from the server");
        }
        try {
            String signature = identityManager.sign(challenge.challenge);
            return DeviceAuthResult.ready(
                    new DeviceProof(identity.getDeviceUuid(), challenge.challengeId, signature));
        } catch (DeviceKeyException e) {
            LogManager.logError(appContext, LOG_TAG, "Unable to sign the login challenge", e);
            return DeviceAuthResult.deviceError(e.getMessage(), e);
        }
    }

    /**
     * Registers the device (or confirms an existing registration) and interprets the status.
     *
     * @return {@code null} when the device is approved and the flow may continue; otherwise the
     * terminal result to show to the user.
     */
    @Nullable
    private DeviceAuthResult registerOrExplain(@NonNull String user, @NonNull String password,
                                                @NonNull DeviceIdentity identity) {
        DeviceRegisterRequest request = DeviceRegisterRequest.builder()
                .username(user)
                .password(password)
                .deviceUuid(identity.getDeviceUuid())
                .publicKey(identity.getPublicKeyBase64())
                .deviceName(DeviceInfoCollector.deviceName(appContext))
                .manufacturer(DeviceInfoCollector.manufacturer())
                .model(DeviceInfoCollector.model())
                .androidVersion(DeviceInfoCollector.androidVersion())
                .installationVersion(DeviceInfoCollector.appVersion(appContext))
                .deviceFingerprint(DeviceInfoCollector.buildFingerprint())
                .build();
        DeviceApiResult<DeviceRegisterData> result = repository.register(request);
        if (result.isNetworkError()) {
            return DeviceAuthResult.networkError(result.getNetworkError());
        }
        if (!result.isSuccess()) {
            // wrong credentials, inactive account, revoked device, device bound to another user...
            return DeviceAuthResult.rejected(result.getMessage());
        }
        // The server now holds this key (new registration, re-installation or existing device).
        identityManager.markKeyRegistered(identity.getKeyFingerprint());
        DeviceRegisterData data = result.getData();
        DeviceStatus status = DeviceStatus.fromServer(data != null ? data.status : null);
        LogManager.log(appContext, LOG_TAG, "Device status: " + status);
        switch (status) {
            case APPROVED:
                return null;
            case PENDING:
                return DeviceAuthResult.pendingApproval();
            case SUSPENDED:
                return DeviceAuthResult.suspended();
            case REVOKED:
            case UNKNOWN:
            default:
                return DeviceAuthResult.rejected(data != null ? data.message : null);
        }
    }
    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}