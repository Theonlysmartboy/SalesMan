package com.js.salesman.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.js.salesman.interfaces.NotificationDao;
import com.js.salesman.models.AppNotification;
import com.js.salesman.utils.database.AppDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationRepository {
    private static final String TAG = "NotificationRepository";
    private static volatile NotificationRepository INSTANCE;
    private final NotificationDao notificationDao;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private NotificationRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.notificationDao = db.notificationDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static NotificationRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (NotificationRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new NotificationRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    private interface IdAction {
        void run(long id);
    }

   public void getNotifications(@Nullable Callback<List<HashMap<String, String>>> callback) {
        read("getNotifications", this::loadNotifications, Collections.emptyList(), callback);
    }

    @WorkerThread
    public List<HashMap<String, String>> getNotificationsSync() {
        return loadNotifications();
    }

    public void getUnreadNotificationsCount(@Nullable Callback<Integer> callback) {
        read("getUnreadNotificationsCount", notificationDao::getUnreadNotificationsCount, 0, callback);
    }

    @WorkerThread
    public int getUnreadNotificationsCountSync() {
        return notificationDao.getUnreadNotificationsCount();
    }

    public void getNotificationDetails(String id, @Nullable Callback<HashMap<String, String>> callback) {
        read("getNotificationDetails", () -> loadDetails(id), new HashMap<>(), callback);
    }

    @WorkerThread
    public HashMap<String, String> getNotificationDetailsSync(String id) {
        return loadDetails(id);
    }

    public void markNotificationAsRead(String id, @Nullable Runnable onComplete) {
        write("markNotificationAsRead", id, notificationDao::markNotificationAsRead, onComplete);
    }

    public void archiveNotification(String id, @Nullable Runnable onComplete) {
        write("archiveNotification", id, notificationDao::archiveNotification, onComplete);
    }

    public void deleteNotification(String id, @Nullable Runnable onComplete) {
        write("deleteNotification", id, notificationDao::deleteNotification, onComplete);
    }

    private List<HashMap<String, String>> loadNotifications() {
        List<AppNotification> notifications = notificationDao.getUnarchivedNotifications();
        List<HashMap<String, String>> result = new ArrayList<>();
        if (notifications == null) return result;
        for (AppNotification notification : notifications) {
            result.add(toMap(notification));
        }
        return result;
    }

    private HashMap<String, String> loadDetails(String id) {
        Long longId = parseId(id);
        if (longId == null) {
            Log.w(TAG, "loadDetails: invalid notification id='" + id + "'");
            return new HashMap<>();
        }
        AppNotification notification = notificationDao.getNotificationById(longId);
        return notification != null ? toMap(notification) : new HashMap<>();
    }

    private static HashMap<String, String> toMap(@NonNull AppNotification notification) {
        HashMap<String, String> map = new HashMap<>();
        map.put("id", String.valueOf(notification.getId()));
        map.put("title", orEmpty(notification.getTitle()));
        map.put("message", orEmpty(notification.getMessage()));
        map.put("type", orEmpty(notification.getType()));
        map.put("is_read", notification.isRead() ? "1" : "0");
        map.put("created_at", orEmpty(notification.getCreatedAt()));
        map.put("payload", orEmpty(notification.getPayload()));
        return map;
    }

    @Nullable
    private static Long parseId(@Nullable String id) {
        if (id == null) return null;
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String orEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    private void write(@NonNull String operation, @Nullable String id,
                        @NonNull IdAction action, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            Long longId = parseId(id);
            if (longId == null) {
                Log.w(TAG, operation + ": invalid notification id='" + id + "'");
            } else {
                try {
                    action.run(longId);
                } catch (Exception e) {
                    Log.e(TAG, operation + ": failed for id=" + longId, e);
                }
            }
            if (onComplete != null) mainHandler.post(onComplete);
        });
    }

    private <T> void read(@NonNull String operation, @NonNull Callable<T> query, T fallback,
                            @Nullable Callback<T> callback) {
        executor.execute(() -> {
            T result;
            try {
                result = query.call();
            } catch (Exception e) {
                Log.e(TAG, operation + ": failed", e);
                result = fallback;
            }
            if (callback != null) {
                final T delivered = result;
                mainHandler.post(() -> callback.onResult(delivered));
            }
        });
    }
}