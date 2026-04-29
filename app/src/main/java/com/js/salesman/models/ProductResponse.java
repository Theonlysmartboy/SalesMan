package com.js.salesman.models;

public class ProductResponse {

    private boolean success;
    private String message;
    private Product data;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Product getData() {
        return data;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setData(Product data) {
        this.data = data;
    }
}
