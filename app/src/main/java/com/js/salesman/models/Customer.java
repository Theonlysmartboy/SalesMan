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
    @SerializedName("Address1")
    private String address;
    @SerializedName("City")
    private String city;
    @SerializedName("Phone")
    private String phone;
    @SerializedName("Email")
    private String email;
    @SerializedName("Category")
    private String category;

    public Customer(String srNo, String customerCode, String customerName, String category) {
        this.srNo = srNo;
        this.customerCode = customerCode;
        this.customerName = customerName;
        this.category = category;
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

    @NonNull
    @Override
    public String toString() {
        if (customerName == null || customerName.isEmpty()) return "Select Customer";
        return customerName + (customerCode != null && !customerCode.isEmpty() ? " (" + customerCode + ")" : "");
    }
}
