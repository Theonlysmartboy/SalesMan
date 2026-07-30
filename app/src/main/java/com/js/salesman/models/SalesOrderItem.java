package com.js.salesman.models;

public class SalesOrderItem {
    private int id;
    private String code;
    private String name;
    private double quantity;
    private double price;
    private double discount;
    private double vatRate;

    public SalesOrderItem() {
    }

    public SalesOrderItem(int id, String code, String name, double quantity,
                    double price, double discount, double vatRate) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.discount = discount;
        this.vatRate = vatRate;
    }

    public int getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public double getDiscount() {
        return discount;
    }

    public double getVatRate() {
        return vatRate;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    public void setVatRate(double vatRate) {
        this.vatRate = vatRate;
    }

    public double getLineTotal() {
        return (quantity * price) - discount;
    }

    public double getVatAmount() {
        return getLineTotal() * vatRate / 100.0;
    }
}
