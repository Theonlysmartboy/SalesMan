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

    public Customer(String srNo, String customerCode, String customerName) {
        this.srNo = srNo;
        this.customerCode = customerCode;
        this.customerName = customerName;
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

    @NonNull
    @Override
    public String toString() {
        if (customerName == null || customerName.isEmpty()) return "Select Customer";
        return customerName + (customerCode != null && !customerCode.isEmpty() ? " (" + customerCode + ")" : "");
    }
}
