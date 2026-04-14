package com.js.salesman.utils;

import com.js.salesman.models.ReportEntry;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ReportUtils {

    public static List<ReportEntry> normalizeMonthlyData(List<ReportEntry> apiData) {
        List<ReportEntry> normalized = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        // Create a map for quick lookup
        Map<String, ReportEntry> dataMap = new HashMap<>();
        if (apiData != null) {
            for (ReportEntry entry : apiData) {
                dataMap.put(entry.getLabel(), entry);
            }
        }

        // Generate last 12 months
        for (int i = 11; i >= 0; i--) {
            Calendar mCal = (Calendar) cal.clone();
            mCal.add(Calendar.MONTH, -i);
            String monthKey = sdf.format(mCal.getTime());

            if (dataMap.containsKey(monthKey)) {
                normalized.add(dataMap.get(monthKey));
            } else {
                normalized.add(new ReportEntry(monthKey, 0, 0.0));
            }
        }

        return normalized;
    }

    public static String getMonthName(String yyyyMM) {
        try {
            SimpleDateFormat inSdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            SimpleDateFormat outSdf = new SimpleDateFormat("MMM", Locale.getDefault());
            return outSdf.format(Objects.requireNonNull(inSdf.parse(yyyyMM)));
        } catch (Exception e) {
            return yyyyMM;
        }
    }
}
