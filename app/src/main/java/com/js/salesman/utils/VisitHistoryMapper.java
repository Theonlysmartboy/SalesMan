package com.js.salesman.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.js.salesman.models.CustomerVisit;
import com.js.salesman.models.VisitHistoryItem;
import com.js.salesman.models.VisitHistoryPage;
import com.js.salesman.models.responses.VisitHistoryResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Converts the history response into the {@link CustomerVisit} objects the report list shows.
 * <p>
 * Server times are plain "yyyy-MM-dd HH:mm:ss" strings with no time zone. They are read in the
 * phone's own time zone, so the list shows the same clock time the server stored.
 */
public final class VisitHistoryMapper {

    private static final String SERVER_DATE_TIME = "yyyy-MM-dd HH:mm:ss";

    private VisitHistoryMapper() {
    }

    @NonNull
    public static VisitHistoryPage toPage(@NonNull VisitHistoryResponse response) {
        List<CustomerVisit> visits = new ArrayList<>();
        if (response.data != null) {
            for (VisitHistoryItem item : response.data) {
                if (item != null) visits.add(toCustomerVisit(item));
            }
        }
        VisitHistoryResponse.Pagination p = response.pagination;
        int returned = p != null ? p.returned : visits.size();
        int total = p != null ? p.total : visits.size();
        boolean hasMore = p != null && p.hasMore;
        return new VisitHistoryPage(visits, returned, total, hasMore, response.counts);
    }

    @NonNull
    public static CustomerVisit toCustomerVisit(@NonNull VisitHistoryItem item) {
        CustomerVisit visit = new CustomerVisit();
        visit.visitId = item.visitId;
        visit.userId = item.userId;
        visit.customerId = item.customerId;
        visit.customerType = item.customerType;
        visit.businessName = firstNonEmpty(item.businessName, item.customerName);
        Long started = parseMillis(item.startedAt);
        visit.startedAt = started != null ? started : 0L;
        visit.scheduledAt = parseMillis(item.scheduledAt);
        visit.endedAt = parseMillis(item.endedAt);
        visit.createdAt = visit.startedAt;
        visit.durationSeconds = item.durationSeconds != null ? item.durationSeconds : 0L;
        visit.startLatitude = item.startLatitude != null ? item.startLatitude : 0.0;
        visit.startLongitude = item.startLongitude != null ? item.startLongitude : 0.0;
        visit.endLatitude = item.endLatitude;
        visit.endLongitude = item.endLongitude;
        visit.visitStatus = item.visitStatus;
        visit.visitSource = item.visitSource;
        // Cancellation reason is stored in "notes" on the server, so it shows as a note.
        visit.notes = item.notes;
        visit.syncStatus = "SYNCED";
        return visit;
    }

    @Nullable
    private static Long parseMillis(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            SimpleDateFormat format = new SimpleDateFormat(SERVER_DATE_TIME, Locale.US);
            format.setLenient(false);
            Date date = format.parse(value.trim());
            return date != null ? date.getTime() : null;
        } catch (ParseException e) {
            return null; // e.g. MySQL's zero date "0000-00-00 00:00:00"
        }
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String first, @Nullable String second) {
        if (first != null && !first.trim().isEmpty()) return first;
        if (second != null && !second.trim().isEmpty()) return second;
        return null;
    }
}