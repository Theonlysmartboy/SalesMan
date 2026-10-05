package com.js.salesman.repository;

import android.content.Context;

import com.js.salesman.interfaces.NotificationDao;
import com.js.salesman.models.AppNotification;
import com.js.salesman.utils.database.AppDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationRepository {
    private static volatile NotificationRepository INSTANCE;
    private final NotificationDao notificationDao;
    private final ExecutorService executor;

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

    public void getNotifications(Callback<List<HashMap<String, String>>> callback) {
        executor.execute(() -> {
            List<HashMap<String, String>> result = getNotificationsSync();
            if (callback != null) callback.onResult(result);
        });
    }

    public List<HashMap<String, String>> getNotificationsSync() {
        List<AppNotification> list = notificationDao.getUnarchivedNotifications();
        List<HashMap<String, String>> result = new ArrayList<>();
        for (AppNotification notif : list) {
            HashMap<String, String> map = new HashMap<>();
            map.put("id", String.valueOf(notif.getId()));
            map.put("title", notif.getTitle() != null ? notif.getTitle() : "");
            map.put("message", notif.getMessage() != null ? notif.getMessage() : "");
            map.put("type", notif.getType() != null ? notif.getType() : "");
            map.put("is_read", notif.isRead() ? "1" : "0");
            map.put("created_at", notif.getCreatedAt() != null ? notif.getCreatedAt() : "");
            map.put("payload", notif.getPayload() != null ? notif.getPayload() : "");
            result.add(map);
        }
        return result;
    }

    public void getUnreadNotificationsCount(Callback<Integer> callback) {
        executor.execute(() -> {
            int count = notificationDao.getUnreadNotificationsCount();
            if (callback != null) callback.onResult(count);
        });
    }

    public int getUnreadNotificationsCountSync() {
        return notificationDao.getUnreadNotificationsCount();
    }

    public void markNotificationAsRead(String id, Runnable onComplete) {
        executor.execute(() -> {
            try {
                long longId = Long.parseLong(id);
                notificationDao.markNotificationAsRead(longId);
            } catch (NumberFormatException ignored) {}
            if (onComplete != null) onComplete.run();
        });
    }

    public void archiveNotification(String id, Runnable onComplete) {
        executor.execute(() -> {
            try {
                long longId = Long.parseLong(id);
                notificationDao.archiveNotification(longId);
            } catch (NumberFormatException ignored) {}
            if (onComplete != null) onComplete.run();
        });
    }

    public void deleteNotification(String id, Runnable onComplete) {
        executor.execute(() -> {
            try {
                long longId = Long.parseLong(id);
                notificationDao.deleteNotification(longId);
            } catch (NumberFormatException ignored) {}
            if (onComplete != null) onComplete.run();
        });
    }

    public void getNotificationDetails(String id, Callback<HashMap<String, String>> callback) {
        executor.execute(() -> {
            HashMap<String, String> details = getNotificationDetailsSync(id);
            if (callback != null) callback.onResult(details);
        });
    }

    public HashMap<String, String> getNotificationDetailsSync(String id) {
        HashMap<String, String> map = new HashMap<>();
        try {
            long longId = Long.parseLong(id);
            AppNotification notif = notificationDao.getNotificationById(longId);
            if (notif != null) {
                map.put("id", String.valueOf(notif.getId()));
                map.put("title", notif.getTitle() != null ? notif.getTitle() : "");
                map.put("message", notif.getMessage() != null ? notif.getMessage() : "");
                map.put("type", notif.getType() != null ? notif.getType() : "");
                map.put("created_at", notif.getCreatedAt() != null ? notif.getCreatedAt() : "");
                map.put("payload", notif.getPayload() != null ? notif.getPayload() : "");
            }
        } catch (NumberFormatException ignored) {}
        return map;
    }
}
