package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

/**
 * One page of visit history, already converted to {@link CustomerVisit}.
 */
public final class VisitHistoryPage {

    private final List<CustomerVisit> visits;
    private final int returned;
    private final int total;
    private final boolean hasMore;
    private final Map<String, Integer> counts;

    public VisitHistoryPage(@NonNull List<CustomerVisit> visits, int returned, int total,
                            boolean hasMore, @Nullable Map<String, Integer> counts) {
        this.visits = visits;
        this.returned = returned;
        this.total = total;
        this.hasMore = hasMore;
        this.counts = counts;
    }

    @NonNull
    public List<CustomerVisit> getVisits() {
        return visits;
    }

    /** Rows the server sent for this page; the next page starts this many rows further on. */
    public int getReturned() {
        return returned;
    }

    /** Number of visits matching the filter across all pages. */
    public int getTotal() {
        return total;
    }

    public boolean hasMore() {
        return hasMore;
    }

    /** Visits per status ("COMPLETED", "CANCELLED") within the date range; null after the first page. */
    @Nullable
    public Map<String, Integer> getCounts() {
        return counts;
    }
}