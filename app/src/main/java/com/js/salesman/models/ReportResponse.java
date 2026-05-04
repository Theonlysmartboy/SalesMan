package com.js.salesman.models;

import java.util.List;

public class ReportResponse {
    public boolean success;
    public List<ReportData> data;
    public List<Customer> customers;
    public List<Product> products;
}
