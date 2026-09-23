package com.js.salesman.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.js.salesman.models.TrackingRecord;

import java.util.List;

@Dao
public interface TrackingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TrackingRecord record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TrackingRecord> records);

    @Query("SELECT * FROM tracking_records WHERE status = 'PENDING' ORDER BY timestamp ASC LIMIT :limit")
    List<TrackingRecord> getPendingRecords(int limit);

    @Query("SELECT * FROM tracking_records WHERE status = 'PENDING' OR status = 'SYNCING' ORDER BY timestamp ASC LIMIT :limit")
    List<TrackingRecord> getPendingAndSyncingRecords(int limit);

    @Query("SELECT * FROM tracking_records WHERE status = :status ORDER BY timestamp ASC")
    List<TrackingRecord> getRecordsByStatus(String status);

    @Query("SELECT COUNT(*) FROM tracking_records WHERE status = 'PENDING'")
    int getPendingCount();

    @Query("SELECT COUNT(*) FROM tracking_records WHERE status = 'PENDING' OR status = 'SYNCING'")
    int getPendingAndSyncingCount();

    @Query("SELECT COUNT(*) FROM tracking_records")
    int getTotalCount();

    @Query("UPDATE tracking_records SET status = :status WHERE id IN (:ids)")
    void updateStatusForIds(List<Long> ids, String status);

    @Query("UPDATE tracking_records SET status = :status, retry_count = retry_count + 1, last_error = :error WHERE id IN (:ids)")
    void updateFailureForIds(List<Long> ids, String status, String error);

    @Query("UPDATE tracking_records SET status = 'PENDING' WHERE status = 'SYNCING'")
    void resetSyncingToPending();

    @Query("DELETE FROM tracking_records WHERE status = 'SYNCED' AND created_at < :olderThanTimestamp")
    void deleteOldSyncedRecords(long olderThanTimestamp);

    @Query("DELETE FROM tracking_records WHERE id IN (:ids)")
    void deleteByIds(List<Long> ids);

    @Update
    void update(TrackingRecord record);
}
