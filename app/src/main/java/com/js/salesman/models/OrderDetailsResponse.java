package com.js.salesman.models;

import com.google.gson.annotations.SerializedName;

public class OrderDetailsResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("data")
    private OrderDetails data;
    @SerializedName("message")
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public OrderDetails getData() {
        return data;
    }

    public void setData(OrderDetails data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
