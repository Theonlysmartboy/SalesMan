package com.js.salesman.repository;

import android.content.Context;

import com.js.salesman.interfaces.ConfigDao;
import com.js.salesman.models.AppConfig;
import com.js.salesman.utils.database.AppDatabase;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigRepository {
    private static volatile ConfigRepository INSTANCE;
    private final ConfigDao configDao;
    private final ExecutorService executor;

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

    public interface Callback<T> {
        void onResult(T result);
    }

    public void storeConfig(String path, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                configDao.insertConfig(new AppConfig("url", path));
                if (callback != null) callback.onResult(true);
            } catch (Exception e) {
                if (callback != null) callback.onResult(false);
            }
        });
    }

    public boolean storeConfigSync(String path) {
        try {
            configDao.insertConfig(new AppConfig("url", path));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isConfiguredSync() {
        String val = configDao.getConfigValue("url");
        return val != null && !val.trim().isEmpty();
    }

    public HashMap<String, String> getConfigMapSync() {
        HashMap<String, String> map = new HashMap<>();
        String url = configDao.getConfigValue("url");
        if (url != null) {
            map.put("url", url);
        }
        return map;
    }

    public void deleteConfig(Runnable onComplete) {
        executor.execute(() -> {
            configDao.deleteConfigByKey("url");
            if (onComplete != null) onComplete.run();
        });
    }

    public void deleteConfigSync() {
        configDao.deleteConfigByKey("url");
    }
}
