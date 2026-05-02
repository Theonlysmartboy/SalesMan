package com.js.salesman.utils;

import com.js.salesman.models.AlternateUnit;
import com.js.salesman.models.Product;

public class PricingHelper {

    public static double getPrice(Product product, String category) {
        String selectedPrice = null;
        
        if (category != null) {
            switch (category.toLowerCase()) {
                case "same town":
                    selectedPrice = product.getSalesmanPrice1();
                    break;
                case "near":
                    selectedPrice = product.getSalesmanPrice2();
                    break;
                case "far":
                    selectedPrice = product.getSalesmanPrice3();
                    break;
            }
        }
        
        double price = parseDouble(selectedPrice);
        
        // Fallback if null or 0.00
        if (price <= 0) {
            price = parseDouble(product.getProduct_Selling_Price());
        }
        
        return price;
    }

    public static double getAlternatePrice(AlternateUnit unit, String category) {
        String selectedPrice = null;
        
        if (category != null) {
            switch (category.toLowerCase()) {
                case "same town":
                    selectedPrice = unit.getAlternatePrice1();
                    break;
                case "near":
                    selectedPrice = unit.getAlternatePrice2();
                    break;
                case "far":
                    selectedPrice = unit.getAlternatePrice3();
                    break;
            }
        }
        
        double price = parseDouble(selectedPrice);
        
        // Fallback if null or 0.00
        if (price <= 0) {
            price = parseDouble(unit.getAlternatePrice());
        }
        
        return price;
    }

    private static double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
