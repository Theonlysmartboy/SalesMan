package com.js.salesman.interfaces;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.js.salesman.models.CustomerVisit;

import java.util.List;

@Dao
public interface CustomerVisitDao {

    @Insert
    long insert(CustomerVisit visit);

    @Update
    int update(CustomerVisit visit);

    @Delete
    int delete(CustomerVisit visit);

    @Query("SELECT * FROM customer_visits " +
            "WHERE visit_id = :visitId LIMIT 1")
    CustomerVisit getByVisitId(String visitId);

    @Query("SELECT * FROM customer_visits " +
            "WHERE user_id = :userId " +
            "AND visit_status = 'IN_PROGRESS' " +
            "ORDER BY started_at DESC LIMIT 1")
    CustomerVisit getActiveVisit(String userId);

    @Query("SELECT * FROM customer_visits " +
            "WHERE user_id = :userId " +
            "AND visit_status = 'SCHEDULED' " +
            "ORDER BY scheduled_at ASC")
    List<CustomerVisit> getScheduledVisits(String userId);

    @Query("SELECT * FROM customer_visits " +
            "WHERE user_id = :userId " +
            "AND visit_status = 'SCHEDULED' " +
            "AND scheduled_at BETWEEN :fromTimestamp AND :toTimestamp " +
            "ORDER BY scheduled_at ASC")
    List<CustomerVisit> getScheduledVisits(String userId, long fromTimestamp, long toTimestamp);

    @Query("SELECT * FROM customer_visits " +
            "WHERE user_id = :userId " +
            "ORDER BY started_at DESC")
    List<CustomerVisit> getVisitsByUser(String userId);

    @Query("SELECT * FROM customer_visits " +
            "WHERE customer_id = :customerId " +
            "ORDER BY started_at DESC")
    List<CustomerVisit> getVisitsByCustomer(String customerId);

    @Query("SELECT * FROM customer_visits " +
            "WHERE sync_status = 'PENDING' " +
            "ORDER BY created_at ASC")
    List<CustomerVisit> getPendingVisits();

    @Query("SELECT * FROM customer_visits " +
            "WHERE sync_status = 'PENDING' " +
            "ORDER BY created_at ASC " +
            "LIMIT :limit")
    List<CustomerVisit> getPendingVisits(int limit);

    @Query("UPDATE customer_visits " +
            "SET sync_status = 'SYNCED', " +
            "updated_at = :updatedAt " +
            "WHERE visit_id = :visitId")
    int markAsSynced(String visitId, long updatedAt);

    @Query("UPDATE customer_visits " +
            "SET server_id = :serverId, " +
            "sync_status = 'SYNCED', " +
            "updated_at = :updatedAt " +
            "WHERE visit_id = :visitId")
    int markSyncedWithServerId(String visitId, String serverId, long updatedAt);

    @Query("UPDATE customer_visits " +
            "SET sync_status = 'FAILED', " +
            "last_sync_attempt = :now, " +
            "sync_error = :error " +
            "WHERE visit_id = :visitId")
    int markFailed(String visitId, long now, String error);

    @Query("UPDATE customer_visits " +
            "SET sync_status = 'PENDING', " +
            "updated_at = :updatedAt " +
            "WHERE visit_id = :visitId")
    int markAsPending(String visitId, long updatedAt);

    @Query("UPDATE customer_visits " +
            "SET visit_status = 'IN_PROGRESS', " +
            "started_at = :startedAt, " +
            "start_latitude = :latitude, " +
            "start_longitude = :longitude, " +
            "sync_status = 'PENDING', " +
            "updated_at = :updatedAt " +
            "WHERE visit_id = :visitId")
    int startScheduledVisit(String visitId, long startedAt, double latitude, double longitude, long updatedAt);

    @Query("UPDATE customer_visits " +
            "SET visit_status = 'POSTPONED', " +
            "scheduled_at = :scheduledAt, " +
            "notes = :notes, " +
            "sync_status = 'PENDING', " +
            "updated_at = :updatedAt " +
            "WHERE visit_id = :visitId")
    int postponeVisit(String visitId, long scheduledAt, String notes, long updatedAt);

    @Query("UPDATE customer_visits " +
            "SET visit_status = 'COMPLETED', " +
            "ended_at = :endedAt, " +
            "duration_seconds = :durationSeconds, " +
            "end_latitude = :endLatitude, " +
            "end_longitude = :endLongitude, " +
            "sync_status = 'PENDING', " +
            "updated_at = :updatedAt " +
            "WHERE visit_id = :visitId")
    int completeVisit(String visitId,
            long endedAt,
            long durationSeconds,
            Double endLatitude,
            Double endLongitude,
            long updatedAt);

    @Query("UPDATE customer_visits " +
            "SET visit_status = 'CANCELLED', " +
            "ended_at = :endedAt, " +
            "cancel_reason = :reason, " +
            "sync_status = 'PENDING', " +
            "updated_at = :updatedAt " +
            "WHERE visit_id = :visitId")
    int cancelVisitWithReason(String visitId,
            long endedAt,
            String reason,
            long updatedAt);

    @Query("UPDATE customer_visits " +
            "SET customer_id = :customerId, " +
            "customer_type = 'REGISTERED', " +
            "updated_at = :updatedAt " +
            "WHERE visit_id = :visitId")
    int linkToCustomer(String visitId,
            String customerId,
            long updatedAt);

    @Query("DELETE FROM customer_visits " +
            "WHERE sync_status = 'SYNCED' " +
            "AND created_at < :beforeTimestamp")
    int deleteOldSyncedVisits(long beforeTimestamp);

    @Query("SELECT COUNT(*) FROM customer_visits " +
            "WHERE sync_status = 'PENDING'")
    int getPendingVisitCount();

    @Query("SELECT COUNT(*) FROM customer_visits " +
            "WHERE sync_status = 'FAILED'")
    int getFailedVisitCount();

    @Query("SELECT COUNT(*) FROM customer_visits " +
            "WHERE user_id = :userId " +
            "AND visit_status = 'COMPLETED'")
    int getCompletedVisitCount(String userId);
}
