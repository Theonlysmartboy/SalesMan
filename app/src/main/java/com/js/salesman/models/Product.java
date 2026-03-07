package com.js.salesman.models;

import java.util.List;

public class Product {

    private final String ProductCode;
    private final String ProductName;
    private final String ProductUnit;
    private final String Product_Selling_Price;
    private final String Product_VAT_Code;
    private final int isStockItem;
    private final int isActive;
    private final String stockQty;
    private final String Modified;
    private final String img_src;
    private final List<AlternateUnit> alternate_units;

    public Product(String productCode, String productName, String productUnit,
                   String product_Selling_Price, String product_VAT_Code, int isStockItem,
                   int isActive, String stockQty, String modified, String img_src, List<AlternateUnit> alternateUnits) {
        ProductCode = productCode;
        ProductName = productName;
        ProductUnit = productUnit;
        Product_Selling_Price = product_Selling_Price;
        Product_VAT_Code = product_VAT_Code;
        this.isStockItem = isStockItem;
        this.isActive = isActive;
        this.stockQty = stockQty;
        Modified = modified;
        this.img_src = img_src;
        alternate_units = alternateUnits;
    }
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
    public List<AlternateUnit> getAlternate_units() {
        return alternate_units;
    }
    public interface OnProductClickListener {
        void onProductClick(String productCode);
    }
}
