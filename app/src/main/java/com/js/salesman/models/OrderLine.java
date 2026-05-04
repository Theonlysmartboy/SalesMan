package com.js.salesman.models;

import com.google.gson.annotations.SerializedName;

public class OrderLine {
    @SerializedName("ProductCode")
    private String productCode;
    @SerializedName("ProductName")
    private String productName;
    @SerializedName("Quantity")
    private String quantity;
    @SerializedName("UnitPrice")
    private String unitPrice;
    @SerializedName("LineTotal")
    private String lineTotal;
    @SerializedName("VatRate")
    private String vatRate;
    @SerializedName("VatAmount")
    private String vatAmount;

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

    public String getQuantity() {
        return quantity;
    }

    public double getQuantityDouble() {
        try {
            return Double.parseDouble(quantity);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getUnitPrice() {
        return unitPrice;
    }

    public double getUnitPriceDouble() {
        try {
            return Double.parseDouble(unitPrice);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public void setUnitPrice(String unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getLineTotal() {
        return lineTotal;
    }

    public double getLineTotalDouble() {
        try {
            return Double.parseDouble(lineTotal);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public void setLineTotal(String lineTotal) {
        this.lineTotal = lineTotal;
    }

    public String getVatRate() {
        return vatRate;
    }

    public void setVatRate(String vatRate) {
        this.vatRate = vatRate;
    }

    public String getVatAmount() {
        return vatAmount;
    }

    public double getVatAmountDouble() {
        try {
            return Double.parseDouble(vatAmount);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public void setVatAmount(String vatAmount) {
        this.vatAmount = vatAmount;
    }
}
