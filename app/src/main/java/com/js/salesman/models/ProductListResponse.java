package com.js.salesman.models;

import java.util.List;

public class ProductListResponse {

    private boolean success;
    private String message;
    private int count;
    private List<Product> data;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public int getCount() { return count; }
    public List<Product> getData() { return data; }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setData(List<Product> data) {
        this.data = data;
    }
}
