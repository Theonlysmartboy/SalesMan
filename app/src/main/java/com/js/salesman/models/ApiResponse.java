package com.js.salesman.models;

import java.util.List;

public class ApiResponse<T> {
    private List<T> data;
    public List<T> getData() {
        return data;
    }
}