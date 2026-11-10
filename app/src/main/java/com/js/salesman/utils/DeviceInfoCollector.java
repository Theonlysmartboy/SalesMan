package com.js.salesman.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Collects descriptive device metadata for registration. Every value is trimmed to the
 * length of its {@code user_devices} column so a strict-mode MySQL never rejects the insert.
 */
public final class DeviceInfoCollector {

    private static final int MAX_DEVICE_NAME = 150;
    private static final int MAX_MANUFACTURER = 100;
    private static final int MAX_MODEL = 100;
    private static final int MAX_ANDROID_VERSION = 50;
    private static final int MAX_APP_VERSION = 50;
    private static final int MAX_FINGERPRINT = 255;

    private DeviceInfoCollector() {
    }

    /** User-chosen device name when available, otherwise "Manufacturer Model". */
    @NonNull
    public static String deviceName(@NonNull Context context) {
        String name = null;
        try {
            name = Settings.Global.getString(context.getContentResolver(), "device_name");
        } catch (RuntimeException ignored) {
            // some OEM builds restrict this setting; fall back below
        }
        if (name == null || name.trim().isEmpty()) {
            name = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        }
        return limit(name, MAX_DEVICE_NAME);
    }

    @NonNull
    public static String manufacturer() {
        return limit(Build.MANUFACTURER, MAX_MANUFACTURER);
    }

    @NonNull
    public static String model() {
        return limit(Build.MODEL, MAX_MODEL);
    }

    @NonNull
    public static String androidVersion() {
        return limit("Android " + Build.VERSION.RELEASE, MAX_ANDROID_VERSION);
    }

    /** Version name of this app installation. */
    @NonNull
    public static String appVersion(@NonNull Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = pm.getPackageInfo(context.getPackageName(),
                        PackageManager.PackageInfoFlags.of(0));
            } else {
                //noinspection deprecation
                info = pm.getPackageInfo(context.getPackageName(), 0);
            }
            return limit(info.versionName, MAX_APP_VERSION);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return "unknown";
        }
    }

    /** Android build fingerprint (describes the OS build, not a unique hardware ID). */
    @NonNull
    public static String buildFingerprint() {
        return limit(Build.FINGERPRINT, MAX_FINGERPRINT);
    }

    @NonNull
    private static String limit(@Nullable String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
