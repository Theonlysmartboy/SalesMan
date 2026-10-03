package com.js.salesman.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.js.salesman.models.TrackingRecord;

import java.util.Collections;
import java.util.List;

@Dao
public interface TrackingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TrackingRecord record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TrackingRecord> records);

    @Query("SELECT * FROM tracking_records WHERE id IN (:ids)")
    List<TrackingRecord> getRecordsByIds(List<Long> ids);

    @Query("SELECT id FROM tracking_records WHERE status = 'PENDING' ORDER BY timestamp ASC LIMIT :limit")
    List<Long> getPendingIds(int limit);

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

    @Query("UPDATE tracking_records SET status = :status, sync_started_at = :now, lease_expires_at = :leaseExpiresAt, batch_id = :batchId WHERE id IN (:ids)")
    void updateClaimedRecords(List<Long> ids, String status, long now, long leaseExpiresAt, String batchId);

    @Query("UPDATE tracking_records SET lease_expires_at = :newLeaseExpiry WHERE batch_id = :batchId AND status = 'SYNCING'")
    int renewLease(String batchId, long newLeaseExpiry);

    @Query("UPDATE tracking_records SET status = 'PENDING', lease_expires_at = 0, batch_id = NULL WHERE status = 'SYNCING' AND lease_expires_at < :now")
    int recoverExpiredLeases(long now);

    @Query("UPDATE tracking_records SET status = 'SYNCED', lease_expires_at = 0 WHERE batch_id = :batchId AND status = 'SYNCING'")
    int updateSuccessForBatch(String batchId);

    @Query("UPDATE tracking_records SET status = :status, retry_count = retry_count + 1, last_error = :error, lease_expires_at = 0 WHERE batch_id = :batchId AND status = 'SYNCING'")
    int updateFailureForBatch(String batchId, String status, String error);

    @Query("UPDATE tracking_records SET status = :status WHERE id IN (:ids)")
    void updateStatusForIds(List<Long> ids, String status);

    @Query("UPDATE tracking_records SET status = :status, retry_count = retry_count + 1, last_error = :error WHERE id IN (:ids)")
    void updateFailureForIds(List<Long> ids, String status, String error);

    @Query("UPDATE tracking_records SET status = 'PENDING', lease_expires_at = 0, batch_id = NULL WHERE status = 'SYNCING'")
    void resetSyncingToPending();

    @Query("UPDATE tracking_records SET visit_id = :newVisitId WHERE visit_id = :oldVisitId")
    int updateVisitId(String oldVisitId, String newVisitId);

    @Query("DELETE FROM tracking_records WHERE status = 'SYNCED' AND created_at < :olderThanTimestamp")
    void deleteOldSyncedRecords(long olderThanTimestamp);

    @Query("DELETE FROM tracking_records WHERE id IN (:ids)")
    void deleteByIds(List<Long> ids);

    @Update
    void update(TrackingRecord record);

    @Transaction
    default List<TrackingRecord> claimPendingRecords(int limit, String batchId, long now, long leaseDurationMs) {
        recoverExpiredLeases(now);
        List<Long> pendingIds = getPendingIds(limit);
        if (pendingIds == null || pendingIds.isEmpty()) {
            return Collections.emptyList();
        }
        long leaseExpiry = now + leaseDurationMs;
        updateClaimedRecords(pendingIds, TrackingRecord.STATUS_SYNCING, now, leaseExpiry, batchId);
        return getRecordsByIds(pendingIds);
    }
}
