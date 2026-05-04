package com.js.salesman.models;

public class ReportEntry {
    private final String label;
    private int totalOrders;
    private final double totalAmount;

    public ReportEntry(String label, int totalOrders, double totalAmount) {
        this.label = label;
        this.totalOrders = totalOrders;
        this.totalAmount = totalAmount;
    }

    public ReportEntry(String label, double totalAmount) {
        this.label = label;
        this.totalAmount = totalAmount;
    }

    public String getLabel() {
        return label;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public double getTotalAmount() {
        return totalAmount;
    }
    
    public double getValue() {
        return totalAmount;
    }
}
