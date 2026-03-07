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
}