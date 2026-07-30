package com.js.salesman.models;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public class Customer {
    @SerializedName("SrNo")
    private String srNo;
    @SerializedName("CustomerCode")
    private String customerCode;
    @SerializedName("CustomerName")
    private String customerName;
    @SerializedName("Category")
    private String category;
    @SerializedName("CreditLimit")
    private double creditLimit;
    @SerializedName("Outstanding")
    private double outstanding;
    @SerializedName("CreditDays")
    private int creditDays;


    public Customer(String srNo, String customerCode, String customerName, String category,
                    double creditLimit, double outstanding, int creditDays) {
        this.srNo = srNo;
        this.customerCode = customerCode;
        this.customerName = customerName;
        this.category = category;
        this.creditLimit = creditLimit;
        this.outstanding = outstanding;
        this.creditDays = creditDays;
    }

    public String getSrNo() {
        return srNo;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCategory() {
        return category;
    }

    public double getCreditLimit() {
        return creditLimit;
    }
    public double getOutstanding() {
        return outstanding;
    }
    public int getCreditDays() {
        return creditDays;
    }

    @NonNull
    @Override
    public String toString() {
        if (customerName == null || customerName.isEmpty()) return "Select Customer";
        return customerName + (customerCode != null && !customerCode.isEmpty() ? " (" + customerCode + ")" : "");
    }
}
