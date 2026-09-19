package com.js.salesman.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_metadata")
public class SyncMetadata {
    @PrimaryKey
    @NonNull
    public String syncType;
    public String lastSyncTimestamp;

    public SyncMetadata(@NonNull String syncType, String lastSyncTimestamp) {
        this.syncType = syncType;
        this.lastSyncTimestamp = lastSyncTimestamp;
    }
}
