package com.js.salesman.utils;

import com.js.salesman.models.AlternateUnit;
import com.js.salesman.models.Product;

public class PricingHelper {

    public static double getPrice(Product product, String category) {
        String priceStr = product.getProduct_Selling_Price();
        
        if (category != null) {
            switch (category.toLowerCase()) {
                case "same town":
                    priceStr = product.getSalesmanPrice1();
                    break;
                case "near":
                    priceStr = product.getSalesmanPrice2();
                    break;
                case "far":
                    priceStr = product.getSalesmanPrice3();
                    break;
            }
        }
        
        try {
            return Double.parseDouble(priceStr);
        } catch (Exception e) {
            try {
                return Double.parseDouble(product.getProduct_Selling_Price());
            } catch (Exception ex) {
                return 0.0;
            }
        }
    }

    public static double getAlternatePrice(AlternateUnit unit, String category) {
        String priceStr = unit.getAlternatePrice();
        
        if (category != null) {
            switch (category.toLowerCase()) {
                case "same town":
                    priceStr = unit.getAlternatePrice1();
                    break;
                case "near":
                    priceStr = unit.getAlternatePrice2();
                    break;
                case "far":
                    priceStr = unit.getAlternatePrice3();
                    break;
            }
        }
        
        try {
            return Double.parseDouble(priceStr);
        } catch (Exception e) {
            try {
                return Double.parseDouble(unit.getAlternatePrice());
            } catch (Exception ex) {
                return 0.0;
            }
        }
    }
}
