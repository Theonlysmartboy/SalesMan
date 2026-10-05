package com.js.salesman.models;

import androidx.room.ColumnInfo;

public class ParkedCartSummary {
    public long id;
    public String name;

    @ColumnInfo(name = "customer_code")
    public String customerCode;

    @ColumnInfo(name = "customer_json")
    public String customerJson;

    @ColumnInfo(name = "created_at")
    public String createdAt;

    @ColumnInfo(name = "item_count")
    public int itemCount;

    @ColumnInfo(name = "total_amount")
    public double totalAmount;
}
