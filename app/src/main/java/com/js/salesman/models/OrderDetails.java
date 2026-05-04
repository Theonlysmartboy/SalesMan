package com.js.salesman.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OrderDetails {
    @SerializedName("OrderNo")
    private String orderNo;
    @SerializedName("CustomerName")
    private String customerName;
    @SerializedName("OrderDate")
    private String orderDate;
    @SerializedName("Status")
    private String status;
    @SerializedName("TotalAmount")
    private String totalAmount;
    @SerializedName("line_count")
    private int lineCount;
    @SerializedName("lines")
    private List<OrderLine> lines;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(String orderDate) {
        this.orderDate = orderDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(String totalAmount) {
        this.totalAmount = totalAmount;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public void setLines(List<OrderLine> lines) {
        this.lines = lines;
    }
}
