package com.js.salesman.utils.managers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.js.salesman.models.DeviceAuthResult;
import com.js.salesman.models.DeviceChallengeData;
import com.js.salesman.models.DeviceIdentity;
import com.js.salesman.models.DeviceProof;
import com.js.salesman.models.DeviceRegisterData;
import com.js.salesman.models.DeviceRegisterRequest;
import com.js.salesman.models.DeviceStatus;
import com.js.salesman.repository.DeviceApiResult;
import com.js.salesman.repository.DeviceRepository;
import com.js.salesman.utils.DeviceInfoCollector;
import com.js.salesman.utils.DeviceKeyException;

/**
 * Runs the device-binding steps that must succeed before a login request is sent:
 * <ol>
 *   <li>make sure the phone has a key pair and a device UUID;</li>
 *   <li>register the device if the server does not yet hold the current key;</li>
 *   <li>request a one-time challenge (only possible once an administrator approved the device);</li>
 *   <li>sign the challenge with the Keystore key.</li>
 * </ol>
 * When the device is not approved, the server's own status is used to tell the user exactly why.
 */
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

    // ------------------------------------------------------------------

    @NonNull
    private DeviceAuthResult requestAndSign(@NonNull String user, @NonNull String password,
                                            @NonNull DeviceIdentity identity) {
        DeviceApiResult<DeviceChallengeData> challengeResult =
                repository.requestChallenge(user, identity.getDeviceUuid());

        if (challengeResult.isNetworkError()) {
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
