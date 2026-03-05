package com.js.salesman.models;

import java.util.List;

public class ProductListResponse {

    private boolean success;
    private int count;
    private List<Product> data;

    public boolean isSuccess() { return success; }
    public int getCount() { return count; }
    public List<Product> getData() { return data; }
}
