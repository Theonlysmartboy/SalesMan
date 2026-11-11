package com.js.salesman.utils.managers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.js.salesman.models.DeviceIdentity;
import com.js.salesman.utils.DeviceKeyException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;

public final class DeviceIdentityManager {
    private static final String TAG = "DeviceIdentityManager";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "com.js.salesman.device_binding_key";
    private static final String EC_CURVE = "secp256r1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String UUID_NAMESPACE = "salesman-device-v1|";
    private static final String KNOWN_BROKEN_ANDROID_ID = "9774d56d682e549c";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final String PREFS_NAME = "device_identity";
    private static final String PREF_FALLBACK_SOURCE = "fallback_source";
    private static final String PREF_REGISTERED_KEY_FP = "registered_key_fingerprint";
    private static volatile DeviceIdentityManager INSTANCE;
    private final Context appContext;
    private final SharedPreferences prefs;
    private final Object lock = new Object();

    private DeviceIdentityManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public static DeviceIdentityManager getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (DeviceIdentityManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DeviceIdentityManager(context);
                }
            }
        }
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    /**
     * Returns this phone's identity, creating the key pair on first use.
     */
    @WorkerThread
    @NonNull
    public DeviceIdentity getOrCreateIdentity() throws DeviceKeyException {
        synchronized (lock) {
            try {
                PublicKey publicKey = ensureKeyPair();
                byte[] der = publicKey.getEncoded();
                return new DeviceIdentity(
                        computeDeviceUuid(),
                        Base64.encodeToString(der, Base64.NO_WRAP),
                        sha256Hex(der));
            } catch (GeneralSecurityException | IOException | RuntimeException e) {
                Log.e(TAG, "Unable to obtain device identity", e);
                throw new DeviceKeyException("Unable to access the device key", e);
            }
        }
    }

    /**
     * Signs the challenge text (SHA256withECDSA, DER) and returns the Base64 signature,
     * matching the server's {@code openssl_verify(..., OPENSSL_ALGO_SHA256)}.
     */
    @WorkerThread
    @NonNull
    public String sign(@NonNull String challenge) throws DeviceKeyException {
        if (challenge.isEmpty()) {
            throw new IllegalArgumentException("challenge must not be empty");
        }
        synchronized (lock) {
            try {
                KeyStore keyStore = loadKeyStore();
                Key key = keyStore.getKey(KEY_ALIAS, null);
                if (!(key instanceof PrivateKey)) {
                    throw new DeviceKeyException("Device key is missing", null, true);
                }
                Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
                signature.initSign((PrivateKey) key);
                signature.update(challenge.getBytes(StandardCharsets.UTF_8));
                return Base64.encodeToString(signature.sign(), Base64.NO_WRAP);
            } catch (UnrecoverableKeyException e) {
                // The entry can never be used again: drop it so a fresh key is created next time.
                Log.e(TAG, "Device key is unrecoverable; discarding it", e);
                resetKeyLocked();
                throw new DeviceKeyException("Device key could not be recovered", e, true);
            } catch (GeneralSecurityException | IOException e) {
                Log.e(TAG, "Unable to sign the challenge", e);
                throw new DeviceKeyException("Unable to sign the challenge", e);
            }
        }
    }

    /**
     * True if the key pair currently exists. Unlike {@link #getOrCreateIdentity()} this never
     * creates a key, so it can detect a key that vanished (reinstall, restored backup).
     */
    @WorkerThread
    public boolean hasKeyPair() throws DeviceKeyException {
        synchronized (lock) {
            try {
                KeyStore keyStore = loadKeyStore();
                return keyStore.containsAlias(KEY_ALIAS) && readPublicKey(keyStore) != null;
            } catch (GeneralSecurityException | IOException e) {
                Log.e(TAG, "Unable to inspect the Keystore", e);
                throw new DeviceKeyException("Unable to inspect the Keystore", e);
            }
        }
    }

    /** This phone's 64-character device identifier (no key is created). */
    @NonNull
    public String getDeviceUuid() throws DeviceKeyException {
        try {
            return computeDeviceUuid();
        } catch (NoSuchAlgorithmException e) {
            throw new DeviceKeyException("Unable to compute the device identifier", e);
        }
    }

    /**
     * Deletes the key pair and the registration mark. The next login creates a new key and
     * registers it again (the server will ask an administrator to re-approve the device).
     */
    public void resetKey() {
        synchronized (lock) {
            resetKeyLocked();
        }
    }

    // ------------------------------------------------------------------
    // Registration mark
    // ------------------------------------------------------------------

    /** True if the server is known to hold the key with this fingerprint. */
    public boolean isKeyRegistered(@NonNull String keyFingerprint) {
        return keyFingerprint.equals(prefs.getString(PREF_REGISTERED_KEY_FP, null));
    }

    public void markKeyRegistered(@NonNull String keyFingerprint) {
        prefs.edit().putString(PREF_REGISTERED_KEY_FP, keyFingerprint).apply();
    }

    /** Forces the next login to register again before requesting a challenge. */
    public void clearRegistrationMark() {
        prefs.edit().remove(PREF_REGISTERED_KEY_FP).apply();
    }

    // ------------------------------------------------------------------
    // Key pair handling
    // ------------------------------------------------------------------

    @NonNull
    private PublicKey ensureKeyPair() throws GeneralSecurityException, IOException {
        KeyStore keyStore = loadKeyStore();
        PublicKey publicKey = keyStore.containsAlias(KEY_ALIAS) ? readPublicKey(keyStore) : null;
        if (publicKey == null) {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS); // corrupt entry
            }
            generateKeyPair();
            publicKey = readPublicKey(keyStore);
            if (publicKey == null) {
                throw new KeyStoreException("Generated key could not be read back");
            }
            // A brand-new key has never been registered.
            clearRegistrationMark();
        }
        return publicKey;
    }

    @Nullable
    private PublicKey readPublicKey(@NonNull KeyStore keyStore) throws KeyStoreException {
        Certificate certificate = keyStore.getCertificate(KEY_ALIAS);
        return certificate != null ? certificate.getPublicKey() : null;
    }

    private void generateKeyPair() throws GeneralSecurityException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                initializeAndGenerate(true);
                return;
            } catch (ProviderException | InvalidAlgorithmParameterException e) {
                // StrongBox not present on this device: use the regular secure hardware / TEE.
                Log.i(TAG, "StrongBox unavailable, falling back to standard Keystore", e);
            }
        }
        initializeAndGenerate(false);
    }

    private void initializeAndGenerate(boolean strongBox) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE);
        generator.initialize(buildKeySpec(strongBox));
        generator.generateKeyPair();
    }

    @NonNull
    private KeyGenParameterSpec buildKeySpec(boolean strongBox) {
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false);
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true);
        }
        return builder.build();
    }

    private void resetKeyLocked() {
        try {
            KeyStore keyStore = loadKeyStore();
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Unable to delete the device key", e);
        }
        clearRegistrationMark();
    }

    @NonNull
    private static KeyStore loadKeyStore() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }

    // ------------------------------------------------------------------
    // Device UUID
    // ------------------------------------------------------------------

    @NonNull
    private String computeDeviceUuid() throws NoSuchAlgorithmException {
        String source = UUID_NAMESPACE + resolveUuidSource();
        return sha256Hex(source.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressLint("HardwareIds")
    @NonNull
    private String resolveUuidSource() {
        String androidId = Settings.Secure.getString(
                appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (isUsableAndroidId(androidId)) {
            return "android_id:" + androidId;
        }
        String fallback = prefs.getString(PREF_FALLBACK_SOURCE, null);
        if (fallback == null) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            fallback = toHex(random);
            prefs.edit().putString(PREF_FALLBACK_SOURCE, fallback).apply();
        }
        return "fallback:" + fallback;
    }

    private static boolean isUsableAndroidId(@Nullable String id) {
        return id != null
                && !id.trim().isEmpty()
                && !KNOWN_BROKEN_ANDROID_ID.equalsIgnoreCase(id.trim());
    }

    // ------------------------------------------------------------------
    // Hex helpers
    // ------------------------------------------------------------------

    @NonNull
    private static String sha256Hex(@NonNull byte[] input) throws NoSuchAlgorithmException {
        return toHex(MessageDigest.getInstance("SHA-256").digest(input));
    }

    @NonNull
    private static String toHex(@NonNull byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}