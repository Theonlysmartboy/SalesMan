package com.js.salesman.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.js.salesman.models.AppNotification;

import java.util.List;

@Dao
public interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertNotification(AppNotification notification);

    @Query("SELECT * FROM notifications WHERE is_archived = 0 ORDER BY id DESC")
    List<AppNotification> getUnarchivedNotifications();

    @Query("SELECT COUNT(*) FROM notifications WHERE is_read = 0 AND is_archived = 0")
    int getUnreadNotificationsCount();

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    AppNotification getNotificationById(long id);

    @Query("UPDATE notifications SET is_read = 1 WHERE id = :id")
    void markNotificationAsRead(long id);

    @Query("UPDATE notifications SET is_archived = 1 WHERE id = :id")
    void archiveNotification(long id);

    @Query("DELETE FROM notifications WHERE id = :id")
    void deleteNotification(long id);
}
