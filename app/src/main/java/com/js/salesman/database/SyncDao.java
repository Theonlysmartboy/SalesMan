package com.js.salesman.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SyncDao {
    @Query("SELECT lastSyncTimestamp FROM sync_metadata WHERE syncType = :type LIMIT 1")
    String getLastSyncTimestamp(String type);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void updateLastSyncTimestamp(SyncMetadata metadata);
}
