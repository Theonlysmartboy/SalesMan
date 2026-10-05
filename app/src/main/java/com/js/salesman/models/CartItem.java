package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cart_items")
public class CartItem {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "product_code")
    private String productCode;

    @ColumnInfo(name = "product_name")
    private String productName;

    @ColumnInfo(name = "unit_price")
    private double unitPrice;

    private int quantity;

    public CartItem(@NonNull String productCode, String productName, double unitPrice, int quantity) {
        this.productCode = productCode;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    @NonNull
    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(@NonNull String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
