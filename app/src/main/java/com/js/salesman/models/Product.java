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
    @SerializedName(value = "ProductCode", alternate = {"product_code", "productCode", "code", "id"})
    private String ProductCode = "";

    @SerializedName(value = "ProductName", alternate = {"product_name", "productName", "name", "title"})
    private final String ProductName;

    @SerializedName(value = "ProductUnit", alternate = {"product_unit", "productUnit", "unit", "uom"})
    private final String ProductUnit;

    @SerializedName(value = "Product_Selling_Price", alternate = {"product_selling_price", "selling_price", "price", "ProductSellingPrice"})
    private final String Product_Selling_Price;

    @SerializedName(value = "SalesmanPrice1", alternate = {"salesmanPrice1", "salesman_price_1", "price1"})
    private String salesmanPrice1;

    @SerializedName(value = "SalesmanPrice2", alternate = {"salesmanPrice2", "salesman_price_2", "price2"})
    private String salesmanPrice2;

    @SerializedName(value = "SalesmanPrice3", alternate = {"salesmanPrice3", "salesman_price_3", "price3"})
    private String salesmanPrice3;

    @SerializedName(value = "isActive", alternate = {"is_active", "active", "status"})
    private final int isActive;

    @SerializedName(value = "Product_Qty", alternate = {"product_qty", "productQty", "quantity", "qty", "stock"})
    private final String Product_Qty;

    @SerializedName(value = "img_src", alternate = {"image_src", "image", "img", "picture"})
    private final String img_src;

    @SerializedName(value = "alternate_units", alternate = {"alternateUnits", "units"})
    private final List<AlternateUnit> alternate_units;

    public Product(@NonNull String ProductCode, String ProductName, String ProductUnit,
                    String Product_Selling_Price, String salesmanPrice1,
                    String salesmanPrice2, String salesmanPrice3, int isActive,
                    String Product_Qty, String img_src, List<AlternateUnit> alternate_units) {
        this.ProductCode = ProductCode != null ? ProductCode : "";
        this.ProductName = ProductName;
        this.ProductUnit = ProductUnit;
        this.Product_Selling_Price = Product_Selling_Price;
        this.salesmanPrice1 = salesmanPrice1;
        this.salesmanPrice2 = salesmanPrice2;
        this.salesmanPrice3 = salesmanPrice3;
        this.isActive = isActive;
        this.Product_Qty = Product_Qty;
        this.img_src = img_src;
        this.alternate_units = alternate_units;
    }

    @NonNull
    public String getProductCode() {
        return ProductCode != null ? ProductCode : "";
    }

    public void setProductCode(@NonNull String productCode) {
        ProductCode = productCode != null ? productCode : "";
    }

    public String getProductName() {
        return ProductName != null ? ProductName : "";
    }

    public String getProductUnit() {
        return ProductUnit != null ? ProductUnit : "";
    }

    public String getProduct_Selling_Price() {
        return Product_Selling_Price != null ? Product_Selling_Price : "0.00";
    }

    public String getSalesmanPrice1() { return salesmanPrice1; }

    public String getSalesmanPrice2() { return salesmanPrice2; }

    public String getSalesmanPrice3() { return salesmanPrice3; }

    public int getIsActive() { return isActive; }

    public String getProduct_Qty() {
        return Product_Qty != null ? Product_Qty : "0";
    }

    public String getImg_src() { return img_src; }

    public List<AlternateUnit> getAlternate_units() { return alternate_units; }

    public String getProductQuantity() {
        return Product_Qty != null ? Product_Qty : "0";
    }

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
        return getProductName() + " (" + getProductCode() + ")";
    }

    public interface OnProductClickListener {
        void onProductClick(String productCode);
        void onAddToOrderClick(Product product);
    }
}
