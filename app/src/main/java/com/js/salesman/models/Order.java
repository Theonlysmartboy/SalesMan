package com.js.salesman.models;

import com.google.gson.annotations.SerializedName;

public class Order {
    private int id;
    @SerializedName("OrderNo")
    private String orderNo;
    @SerializedName("CustomerCode")
    private String customerCode; // This seems to be the SrNo in the response based on previous context
    @SerializedName("sales_man_id")
    private int salesManId;
    @SerializedName("OrderDate")
    private String orderDate;
    @SerializedName("Status")
    private String status;
    @SerializedName("TotalAmount")
    private String totalAmount;
    @SerializedName("Created")
    private String created;

    // These fields might come from joined data in a real scenario, 
    // but the sample response only shows IDs/Codes.
    // I'll add them to make the adapter work as requested.
    private String customerName;
    private String productName;
    private int quantity;

    public int getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public String getCustomerCode() { return customerCode; }
    public int getSalesManId() { return salesManId; }
    public String getOrderDate() { return orderDate; }
    public String getStatus() { return status; }
    public String getTotalAmount() { return totalAmount; }
    public String getCreated() { return created; }

    public String getCustomerName() { return customerName != null ? customerName : "Customer #" + customerCode; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getProductName() { return productName != null ? productName : "Multiple Items"; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
