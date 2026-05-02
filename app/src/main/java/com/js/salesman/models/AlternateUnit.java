package com.js.salesman.models;

import com.google.gson.annotations.SerializedName;

public class AlternateUnit {

    @SerializedName("AlternetUnit")
    private String alternateUnit;

    @SerializedName("AlternetQty")
    private String alternateQty;

    @SerializedName("PrimaryQty")
    private String primaryQty;

    @SerializedName("AlternetCostPrice")
    private String alternateCostPrice;

    @SerializedName("AlternetMrgn")
    private String alternateMrgn;

    @SerializedName("AlternetTradePrice")
    private String alternateTradePrice;

    @SerializedName("AlternetPrice")
    private String alternatePrice;
    
    @SerializedName("AlternetPrice1")
    private String alternatePrice1;
    
    @SerializedName("AlternetPrice2")
    private String alternatePrice2;
    
    @SerializedName("AlternetPrice3")
    private String alternatePrice3;

    public String getAlternateUnit() {
        return alternateUnit;
    }

    public String getAlternateQty() {
        return alternateQty;
    }

    public String getPrimaryQty() {
        return primaryQty;
    }

    public String getAlternateCostPrice() {
        return alternateCostPrice;
    }

    public String getAlternateMrgn() {
        return alternateMrgn;
    }

    public String getAlternateTradePrice() {
        return alternateTradePrice;
    }

    public String getAlternatePrice() {
        return alternatePrice;
    }

    public String getAlternatePrice1() {
        return alternatePrice1;
    }

    public String getAlternatePrice2() {
        return alternatePrice2;
    }

    public String getAlternatePrice3() {
        return alternatePrice3;
    }
}
