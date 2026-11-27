package com.js.salesman.models.responses;

import com.google.gson.annotations.SerializedName;
import com.js.salesman.models.items.VisitHistoryItem;

import java.util.List;
import java.util.Map;

/**
 * Response of {@code customer_visits.php?action=history}.
 */
public class VisitHistoryResponse {
    public boolean success;
    public String message;
    public List<VisitHistoryItem> data;
    public Pagination pagination;
    /** Visits per status ("COMPLETED", "CANCELLED"); only present on the first page. */
    public Map<String, Integer> counts;

    public static class Pagination {
        public int limit;
        public int offset;
        public int returned;
        public int total;
        @SerializedName("has_more")
        public boolean hasMore;
    }
}