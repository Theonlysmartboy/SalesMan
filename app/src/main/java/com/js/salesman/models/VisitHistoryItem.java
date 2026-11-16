package com.js.salesman.models;

import com.google.gson.annotations.SerializedName;

/**
 * One row of {@code customer_visits.php?action=history}, exactly as the server sends it.
 * Dates are MySQL strings ("yyyy-MM-dd HH:mm:ss"); numeric columns may arrive as strings.
 * Use {@code VisitHistoryMapper} to turn it into a {@link CustomerVisit}.
 */
public class VisitHistoryItem {
    public String id;
    @SerializedName("visit_id")
    public String visitId;
    @SerializedName("user_id")
    public String userId;
    @SerializedName("customer_id")
    public String customerId;
    @SerializedName("customer_type")
    public String customerType;
    @SerializedName("business_name")
    public String businessName;
    @SerializedName("customer_name")
    public String customerName;
    @SerializedName("scheduled_at")
    public String scheduledAt;
    @SerializedName("started_at")
    public String startedAt;
    @SerializedName("ended_at")
    public String endedAt;
    @SerializedName("duration_seconds")
    public Long durationSeconds;
    @SerializedName("start_latitude")
    public Double startLatitude;
    @SerializedName("start_longitude")
    public Double startLongitude;
    @SerializedName("end_latitude")
    public Double endLatitude;
    @SerializedName("end_longitude")
    public Double endLongitude;
    @SerializedName("visit_status")
    public String visitStatus;
    @SerializedName("visit_source")
    public String visitSource;
    public String notes;
}