package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the visit history shows: which kind of visits and which days. Immutable; the
 * {@code with...} methods return a modified copy.
 */
public final class VisitHistoryFilter {

    public enum Status {
        /** Every finished visit (completed and cancelled). */
        ALL,
        COMPLETED,
        CANCELLED
    }

    public static final VisitHistoryFilter DEFAULT = new VisitHistoryFilter(Status.ALL, null, null);

    private final Status status;
    private final String from;
    private final String to;

    private VisitHistoryFilter(@NonNull Status status, @Nullable String from, @Nullable String to) {
        this.status = status;
        this.from = from;
        this.to = to;
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    /** First day to include as "yyyy-MM-dd", or null for no lower limit. */
    @Nullable
    public String getFrom() {
        return from;
    }

    /** Last day to include as "yyyy-MM-dd" (the whole day counts), or null for no upper limit. */
    @Nullable
    public String getTo() {
        return to;
    }

    public boolean hasDateRange() {
        return from != null || to != null;
    }

    /** Value of the {@code status} query parameter, or null to let the server use its default. */
    @Nullable
    public String statusParam() {
        return status == Status.ALL ? null : status.name();
    }

    @NonNull
    public VisitHistoryFilter withStatus(@NonNull Status newStatus) {
        return new VisitHistoryFilter(newStatus, from, to);
    }

    @NonNull
    public VisitHistoryFilter withFrom(@Nullable String newFrom) {
        return new VisitHistoryFilter(status, newFrom, to);
    }

    @NonNull
    public VisitHistoryFilter withTo(@Nullable String newTo) {
        return new VisitHistoryFilter(status, from, newTo);
    }
}