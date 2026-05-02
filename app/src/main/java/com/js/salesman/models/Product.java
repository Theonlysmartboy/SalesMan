package com.js.salesman.models;

import androidx.annotation.NonNull;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Product {

    private final String ProductCode;
    private final String ProductName;
    private final String ProductUnit;
    private final String Product_Selling_Price;
    
    @SerializedName("SalesmanPrice1")
    private final String salesmanPrice1;
    @SerializedName("SalesmanPrice2")
    private final String salesmanPrice2;
    @SerializedName("SalesmanPrice3")
    private final String salesmanPrice3;

    private final String Product_VAT_Code;
    private final int isStockItem;
    private final int isActive;
    private final String stockQty;
    private final String Product_Qty;
    private final String Modified;
    private final String img_src;
    private final List<AlternateUnit> alternate_units;

    public Product(String productCode, String productName, String productUnit,
                   String product_Selling_Price, String salesmanPrice1, String salesmanPrice2, 
                   String salesmanPrice3, String product_VAT_Code, int isStockItem,
                   int isActive, String stockQty, String Product_Qty, String modified, 
                   String img_src, List<AlternateUnit> alternateUnits) {
        ProductCode = productCode;
        ProductName = productName;
        ProductUnit = productUnit;
        Product_Selling_Price = product_Selling_Price;
        this.salesmanPrice1 = salesmanPrice1;
        this.salesmanPrice2 = salesmanPrice2;
        this.salesmanPrice3 = salesmanPrice3;
        Product_VAT_Code = product_VAT_Code;
        this.isStockItem = isStockItem;
        this.isActive = isActive;
        this.stockQty = stockQty;
        this.Product_Qty = Product_Qty;
        Modified = modified;
        this.img_src = img_src;
        alternate_units = alternateUnits;
    }

    public String getProductCode() { return ProductCode; }
    public String getProductName() { return ProductName; }
    public String getProductUnit() { return ProductUnit; }
    public String getProduct_Selling_Price() { return Product_Selling_Price; }
    
    public String getSalesmanPrice1() { return salesmanPrice1; }
    public String getSalesmanPrice2() { return salesmanPrice2; }
    public String getSalesmanPrice3() { return salesmanPrice3; }

    public String getProduct_VAT_Code() { return Product_VAT_Code; }
    public int getIsStockItem() { return isStockItem; }
    public int getIsActive() { return isActive; }
    public String getStockQty() { return stockQty; }
    public String getProductQuantity() { return Product_Qty; }
    public String getModified() { return Modified; }
    public String getImg_src() { return img_src; }
    public List<AlternateUnit> getAlternate_units() {
        return alternate_units;
    }

    @NonNull
    @Override
    public String toString() {
        return ProductName + " (" + ProductCode + ")";
    }

    public interface OnProductClickListener {
        void onProductClick(String productCode);
    }
}
