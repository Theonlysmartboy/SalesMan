package com.js.salesman.models;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "customer_visits",
        indices = {
                @Index(value = {"visit_id"}, unique = true),
                @Index(value = {"server_id"}),
                @Index(value = {"client_visit_id"}),
                @Index(value = {"user_id"}),
                @Index(value = {"customer_id"}),
                @Index(value = {"started_at"}),
                @Index(value = {"scheduled_at"}),
                @Index(value = {"visit_status"}),
                @Index(value = {"sync_status"})
        })

public class CustomerVisit {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "visit_id")
    public String visitId;

    @Nullable
    @ColumnInfo(name = "server_id")
    public String serverId;

    @Nullable
    @ColumnInfo(name = "client_visit_id")
    public String clientVisitId;

    @ColumnInfo(name = "user_id")
    public String userId;

    @Nullable
    @ColumnInfo(name = "customer_id")
    public String customerId;

    @ColumnInfo(name = "customer_type")
    public String customerType;

    @Nullable
    @ColumnInfo(name = "business_name")
    public String businessName;

    @ColumnInfo(name = "started_at")
    public long startedAt;

    @Nullable
    @ColumnInfo(name = "scheduled_at")
    public Long scheduledAt;

    @Nullable
    @ColumnInfo(name = "ended_at")
    public Long endedAt;

    @ColumnInfo(name = "duration_seconds", defaultValue = "0")
    public long durationSeconds = 0;

    @ColumnInfo(name = "start_latitude")
    public double startLatitude;

    @ColumnInfo(name = "start_longitude")
    public double startLongitude;

    @Nullable
    @ColumnInfo(name = "end_latitude")
    public Double endLatitude;

    @Nullable
    @ColumnInfo(name = "end_longitude")
    public Double endLongitude;

    @ColumnInfo(name = "visit_status")
    public String visitStatus;

    @ColumnInfo(name = "visit_source")
    public String visitSource;

    @Nullable
    @ColumnInfo(name = "notes")
    public String notes;

    @Nullable
    @ColumnInfo(name = "cancel_reason")
    public String cancelReason;

    @ColumnInfo(name = "sync_status", defaultValue = "'PENDING'")
    public String syncStatus = "PENDING";

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @Nullable
    @ColumnInfo(name = "updated_at")
    public Long updatedAt;

    @Nullable
    @ColumnInfo(name = "last_sync_attempt")
    public Long lastSyncAttempt;

    @Nullable
    @ColumnInfo(name = "sync_error")
    public String syncError;
}
