package com.js.salesman.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.js.salesman.models.PendingOrder;

import java.util.List;

@Dao
public interface PendingOrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PendingOrder order);

    @Update
    int update(PendingOrder order);

    @Query("SELECT * FROM pending_orders WHERE order_uuid = :orderUuid LIMIT 1")
    PendingOrder getByOrderUuid(String orderUuid);

    @Query("SELECT * FROM pending_orders WHERE sync_status = 'PENDING' ORDER BY created_at ASC")
    List<PendingOrder> getPendingOrders();

    @Query("SELECT * FROM pending_orders WHERE sync_status = 'PENDING' ORDER BY created_at ASC LIMIT :limit")
    List<PendingOrder> getPendingOrders(int limit);

    @Query("UPDATE pending_orders SET sync_status = 'SYNCED', updated_at = :updatedAt WHERE order_uuid = :orderUuid")
    int markSynced(String orderUuid, long updatedAt);

    @Query("UPDATE pending_orders SET visit_id = :newVisitId WHERE visit_id = :oldVisitId")
    int updateVisitId(String oldVisitId, String newVisitId);

    @Query("UPDATE pending_orders SET sync_status = 'FAILED', last_sync_attempt = :now, sync_error = :error WHERE order_uuid = :orderUuid")
    int markFailed(String orderUuid, long now, String error);

    @Query("SELECT COUNT(*) FROM pending_orders WHERE sync_status = 'PENDING'")
    int getPendingCount();

    @Query("SELECT COUNT(*) FROM pending_orders WHERE sync_status = 'FAILED'")
    int getFailedCount();
}
