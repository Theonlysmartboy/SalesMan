package com.js.salesman.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.js.salesman.interfaces.ConfigDao;
import com.js.salesman.models.AppConfig;
import com.js.salesman.utils.database.AppDatabase;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigRepository {
    private static final String TAG = "ConfigRepository";
    private static final String KEY_URL = "url";

    private static volatile ConfigRepository INSTANCE;

    private final ConfigDao configDao;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Cached value of the "url" config entry. Only valid when {@link #cacheLoaded} is true. */
    private volatile String cachedUrl;
    private volatile boolean cacheLoaded;

    private ConfigRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.configDao = db.configDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static ConfigRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ConfigRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ConfigRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /** Callback invoked on the main thread. */
    public interface Callback<T> {
        void onResult(T result);
    }

    public void preload() {
        executor.execute(() -> {
            try {
                ensureLoaded();
            } catch (Exception e) {
                Log.e(TAG, "preload: failed to load configuration", e);
            }
        });
    }

    public void isConfigured(@NonNull Callback<Boolean> callback) {
        executor.execute(() -> {
            boolean configured;
            try {
                configured = hasValue(ensureLoaded());
            } catch (Exception e) {
                Log.e(TAG, "isConfigured: failed to read configuration", e);
                configured = false;
            }
            deliver(callback, configured);
        });
    }

    public void getConfigMap(@NonNull Callback<HashMap<String, String>> callback) {
        executor.execute(() -> {
            HashMap<String, String> map;
            try {
                map = toMap(ensureLoaded());
            } catch (Exception e) {
                Log.e(TAG, "getConfigMap: failed to read configuration", e);
                map = new HashMap<>();
            }
            deliver(callback, map);
        });
    }

    public void storeConfig(String path, @Nullable Callback<Boolean> callback) {
        executor.execute(() -> {
            boolean success = storeInternal(path);
            if (callback != null) deliver(callback, success);
        });
    }

    public void deleteConfig(@Nullable Runnable onComplete) {
        executor.execute(() -> {
            try {
                deleteInternal();
            } catch (Exception e) {
                Log.e(TAG, "deleteConfig: failed to delete configuration", e);
            }
            if (onComplete != null) mainHandler.post(onComplete);
        });
    }

    @WorkerThread
    public boolean storeConfigSync(String path) {
        return storeInternal(path);
    }

    @WorkerThread
    public boolean isConfiguredSync() {
        return hasValue(ensureLoaded());
    }

    @WorkerThread
    public HashMap<String, String> getConfigMapSync() {
        return toMap(ensureLoaded());
    }

    @WorkerThread
    public void deleteConfigSync() {
        deleteInternal();
    }

    @Nullable
    private String ensureLoaded() {
        if (!cacheLoaded) {
            synchronized (this) {
                if (!cacheLoaded) {
                    cachedUrl = configDao.getConfigValue(KEY_URL);
                    cacheLoaded = true;
                }
            }
        }
        return cachedUrl;
    }

    private boolean storeInternal(String path) {
        try {
            configDao.insertConfig(new AppConfig(KEY_URL, path));
            synchronized (this) {
                cachedUrl = path;
                cacheLoaded = true;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "storeConfig: failed to store configuration", e);
            return false;
        }
    }

    private void deleteInternal() {
        configDao.deleteConfigByKey(KEY_URL);
        synchronized (this) {
            cachedUrl = null;
            cacheLoaded = true;
        }
    }

    private static boolean hasValue(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static HashMap<String, String> toMap(@Nullable String url) {
        HashMap<String, String> map = new HashMap<>();
        if (url != null) {
            map.put(KEY_URL, url);
        }
        return map;
    }

    private <T> void deliver(@NonNull Callback<T> callback, T result) {
        mainHandler.post(() -> callback.onResult(result));
    }
}