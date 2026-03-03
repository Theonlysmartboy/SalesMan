package com.js.salesman.models;

public class Product {

    private String ProductCode;
    private String ProductName;
    private String ProductUnit;
    private String Product_Selling_Price;
    private String Product_VAT_Code;
    private int isStockItem;
    private int isActive;
    private String stockQty;
    private String Modified;
    private String img_src;

    // Getters

    public String getProductCode() { return ProductCode; }
    public String getProductName() { return ProductName; }
    public String getProductUnit() { return ProductUnit; }
    public String getProduct_Selling_Price() { return Product_Selling_Price; }
    public String getProduct_VAT_Code() { return Product_VAT_Code; }
    public int getIsStockItem() { return isStockItem; }
    public int getIsActive() { return isActive; }
    public String getStockQty() { return stockQty; }
    public String getModified() { return Modified; }
    public String getImg_src() { return img_src; }
}
