package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "customers")
public class Customer {

    @PrimaryKey
    @NonNull
    @SerializedName("SrNo")
    @ColumnInfo(name = "sr_no")
    private String srNo;

    @SerializedName("CustomerCode")
    @ColumnInfo(name = "customer_code")
    private String customerCode;

    @SerializedName("CustomerName")
    @ColumnInfo(name = "customer_name")
    private String customerName;

    @SerializedName("Category")
    @ColumnInfo(name = "category")
    private String category;

    @SerializedName("CreditLimit")
    @ColumnInfo(name = "credit_limit")
    private double creditLimit;

    @SerializedName("CreditAmount")
    @ColumnInfo(name = "outstanding")
    private double outstanding;

    @SerializedName("CreditDays")
    @ColumnInfo(name = "credit_days")
    private int creditDays;

    public Customer(@NonNull String srNo, String customerCode, String customerName, String category,
                    double creditLimit, double outstanding, int creditDays) {
        this.srNo = srNo;
        this.customerCode = customerCode;
        this.customerName = customerName;
        this.category = category;
        this.creditLimit = creditLimit;
        this.outstanding = outstanding;
        this.creditDays = creditDays;
    }

    @NonNull
    public String getSrNo() {
        return srNo;
    }

    public void setSrNo(@NonNull String srNo) {
        this.srNo = srNo;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(double creditLimit) {
        this.creditLimit = creditLimit;
    }

    public double getOutstanding() {
        return outstanding;
    }

    public void setOutstanding(double outstanding) {
        this.outstanding = outstanding;
    }

    public int getCreditDays() {
        return creditDays;
    }

    public void setCreditDays(int creditDays) {
        this.creditDays = creditDays;
    }

    @NonNull
    @Override
    public String toString() {
        if (customerName == null || customerName.isEmpty()) return "Select Customer";
        return customerName + (customerCode != null && !customerCode.isEmpty() ?
                " (" + customerCode + ")" : "");
    }
}
