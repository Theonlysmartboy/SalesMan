package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Objects;

@Entity(tableName = "products")
public class Product {

    @PrimaryKey
    @NonNull
    private String ProductCode;
    private String ProductName;
    private String ProductUnit;
    private String Product_Selling_Price;
    
    @SerializedName("SalesmanPrice1")
    private String salesmanPrice1;
    @SerializedName("SalesmanPrice2")
    private String salesmanPrice2;
    @SerializedName("SalesmanPrice3")
    private String salesmanPrice3;
    private int isActive = 1; // Default to active
    private String Product_Qty;
    private String img_src;
    private List<AlternateUnit> alternate_units;

    public Product(@NonNull String ProductCode, String ProductName, String ProductUnit,
                    String Product_Selling_Price, String salesmanPrice1,
                    String salesmanPrice2, String salesmanPrice3, int isActive,
                    String Product_Qty, String img_src, List<AlternateUnit> alternate_units) {
        this.ProductCode = ProductCode;
        this.ProductName = ProductName;
        this.ProductUnit = ProductUnit;
        this.Product_Selling_Price = Product_Selling_Price;
        this.salesmanPrice1 = salesmanPrice1;
        this.salesmanPrice2 = salesmanPrice2;
        this.salesmanPrice3 = salesmanPrice3;
        // If isActive is 0, it's not active. But if it's a new product from API that doesn't
        // have the field,
        // it might be 0. But we should respect 0 if it's explicitly for deletion.
        this.isActive = isActive;
        this.Product_Qty = Product_Qty;
        this.img_src = img_src;
        this.alternate_units = alternate_units;
    }

    @NonNull
    public String getProductCode() { return ProductCode; }

    public void setProductCode(@NonNull String productCode) { ProductCode = productCode; }

    public String getProductName() { return ProductName; }

    public void setProductName(String productName) { ProductName = productName; }

    public String getProductUnit() { return ProductUnit; }

    public void setProductUnit(String productUnit) { ProductUnit = productUnit; }

    public String getProduct_Selling_Price() { return Product_Selling_Price; }

    public void setProduct_Selling_Price(String product_Selling_Price) { Product_Selling_Price = product_Selling_Price; }

    public String getSalesmanPrice1() { return salesmanPrice1; }

    public void setSalesmanPrice1(String salesmanPrice1) { this.salesmanPrice1 = salesmanPrice1; }

    public String getSalesmanPrice2() { return salesmanPrice2; }

    public void setSalesmanPrice2(String salesmanPrice2) { this.salesmanPrice2 = salesmanPrice2; }

    public String getSalesmanPrice3() { return salesmanPrice3; }

    public void setSalesmanPrice3(String salesmanPrice3) { this.salesmanPrice3 = salesmanPrice3; }

    public int getIsActive() { return isActive; }

    public void setIsActive(int isActive) { this.isActive = isActive; }

    public String getProduct_Qty() { return Product_Qty; }

    public void setProduct_Qty(String product_Qty) { Product_Qty = product_Qty; }

    public String getImg_src() { return img_src; }

    public void setImg_src(String img_src) { this.img_src = img_src; }

    public List<AlternateUnit> getAlternate_units() { return alternate_units; }

    public void setAlternate_units(List<AlternateUnit> alternate_units) { this.alternate_units = alternate_units; }

    public String getProductQuantity() { return Product_Qty; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(ProductCode, product.ProductCode) &&
                Objects.equals(ProductName, product.ProductName) &&
                Objects.equals(Product_Selling_Price, product.Product_Selling_Price) &&
                Objects.equals(Product_Qty, product.Product_Qty);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ProductCode, ProductName, Product_Selling_Price, Product_Qty);
    }

    @NonNull
    @Override
    public String toString() {
        return ProductName + " (" + ProductCode + ")";
    }

    public interface OnProductClickListener {
        void onProductClick(String productCode);
        void onAddToOrderClick(Product product);
    }
}
