package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_orders",
        indices = {
            @Index(value = {"order_uuid"}, unique = true),
            @Index(value = {"sync_status"})
        })
public class PendingOrder {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "order_uuid")
    public String orderUuid;

    @NonNull
    @ColumnInfo(name = "user_id")
    public String userId;

    @NonNull
    @ColumnInfo(name = "customer_id")
    public String customerId;

    @Nullable
    @ColumnInfo(name = "visit_id")
    public String visitId;

    @ColumnInfo(name = "total_amount")
    public double totalAmount;

    @ColumnInfo(name = "vat_amount")
    public double vatAmount;

    @ColumnInfo(name = "discount_amount")
    public double discountAmount;

    @Nullable
    public Double latitude;

    @Nullable
    public Double longitude;

    @NonNull
    @ColumnInfo(name = "lines_json")
    public String linesJson;

    @NonNull
    @ColumnInfo(name = "order_date")
    public String orderDate;

    @NonNull
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

    public PendingOrder() {
        this.createdAt = System.currentTimeMillis();
        this.syncStatus = "PENDING";
        this.linesJson = "";
        this.orderDate = "";
        this.customerId = "";
        this.orderUuid = "";
        this.userId = "";
    }
}
