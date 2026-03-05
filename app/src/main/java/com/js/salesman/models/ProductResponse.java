package com.js.salesman.models;

public class ProductResponse {

    private final boolean success;
    private final Product data;

    public ProductResponse(boolean success, Product data) {
        this.success = success;
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public Product getData() {
        return data;
    }
}