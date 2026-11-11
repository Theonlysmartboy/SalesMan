package com.js.salesman.models.responses;

import com.js.salesman.models.Customer;
import com.js.salesman.models.Product;
import com.js.salesman.models.data.ReportData;

import java.util.List;

public class ReportResponse {
    public boolean success;
    public List<ReportData> data;
    public List<Customer> customers;
    public List<Product> products;
}
