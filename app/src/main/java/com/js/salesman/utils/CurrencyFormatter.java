package com.js.salesman.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public final class CurrencyFormatter {

    private static final DecimalFormat formatter;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        formatter = new DecimalFormat("#,##0.00", symbols);
    }

    public static String format(double amount) {
        return formatter.format(amount);
    }

    public static String format(double amount, String currencySymbol) {
        return currencySymbol + " " + formatter.format(amount);
    }
}