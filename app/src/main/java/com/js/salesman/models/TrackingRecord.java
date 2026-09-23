package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "tracking_records")
public class TrackingRecord {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SYNCING = "SYNCING";
    public static final String STATUS_SYNCED = "SYNCED";
    public static final String STATUS_FAILED = "FAILED";

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "tracking_id")
    private String trackingId;

    @ColumnInfo(name = "user_id")
    private String userId;

    private double latitude;

    private double longitude;

    private long timestamp;

    @NonNull
    private String status;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "retry_count")
    private int retryCount;

    @ColumnInfo(name = "last_error")
    private String lastError;

    public TrackingRecord() {
        this.trackingId = UUID.randomUUID().toString();
        this.status = STATUS_PENDING;
        this.createdAt = System.currentTimeMillis();
        this.retryCount = 0;
    }

    public TrackingRecord(String userId, double latitude, double longitude, long timestamp) {
        this();
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(@NonNull String trackingId) {
        this.trackingId = trackingId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    public void setStatus(@NonNull String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
