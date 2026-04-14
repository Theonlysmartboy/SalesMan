package com.js.salesman.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SalesReportResponse {
    @SerializedName("success")
    private boolean success;
    
    @SerializedName("data")
    private ReportData data;

    public boolean isSuccess() {
        return success;
    }

    public ReportData getData() {
        return data;
    }

    public static class ReportData {
        @SerializedName("monthly")
        private List<ReportEntry> monthly;
        
        @SerializedName("daily")
        private List<ReportEntry> daily;

        public List<ReportEntry> getMonthly() {
            return monthly;
        }

        public List<ReportEntry> getDaily() {
            return daily;
        }
    }
}
