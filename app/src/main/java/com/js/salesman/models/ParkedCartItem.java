package com.js.salesman.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "parked_cart_items",
        foreignKeys = @ForeignKey(
                entity = ParkedCart.class,
                parentColumns = "id",
                childColumns = "parked_cart_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("parked_cart_id")})
public class ParkedCartItem {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "parked_cart_id")
    private long parkedCartId;

    @ColumnInfo(name = "product_code")
    private String productCode;

    @ColumnInfo(name = "product_name")
    private String productName;

    @ColumnInfo(name = "unit_price")
    private double unitPrice;

    private int quantity;

    public ParkedCartItem() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getParkedCartId() {
        return parkedCartId;
    }

    public void setParkedCartId(long parkedCartId) {
        this.parkedCartId = parkedCartId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
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
