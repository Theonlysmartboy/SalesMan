package com.js.salesman.ui.controllers;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.js.salesman.R;
import com.js.salesman.adapters.CustomerVisitReportAdapter;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.models.VisitHistoryFilter;
import com.js.salesman.models.VisitHistoryPage;
import com.js.salesman.models.responses.VisitHistoryResponse;
import com.js.salesman.repositories.CustomerVisitRepository;
import com.js.salesman.repositories.VisitHistoryRepository;
import com.js.salesman.utils.managers.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;

/**
 * Drives the "Past visits" tab of the reports screen: finished visits of the signed-in salesman,
 * loaded page by page from the server as the user scrolls, with a status filter (all / completed /
 * canceled) and an optional From / To date range.
 * <p>
 * If the server cannot be reached while loading the first page, it falls back to the visits stored
 * on the phone (no paging) so the tab is never empty just because the user is offline.
 * <p>
 * Requires the views added to {@code fragment_reports.xml} (see visits_container_replacement.xml).
 */
public final class VisitHistoryController {

    private static final int PAGE_SIZE = 20;
    /** Start loading the next page when this many rows from the end are visible. */
    private static final int PREFETCH_DISTANCE = 3;
    /** After a failed "load more", wait this long before scrolling can trigger another attempt. */
    private static final long LOAD_MORE_RETRY_DELAY_MS = 3000L;
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private final Fragment fragment;
    private final SessionManager session;
    private final VisitHistoryRepository repository;
    private final CustomerVisitReportAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final View filterContainer;
    private final Chip chipAll;
    private final Chip chipCompleted;
    private final Chip chipCancelled;
    private final MaterialButton btnFrom;
    private final MaterialButton btnTo;
    private final MaterialButton btnClearDates;
    private final TextView tvSummary;
    private final TextView tvEmpty;
    private final ProgressBar progress;
    private final ProgressBar loadMoreProgress;
    private final RecyclerView recycler;
    private final RecyclerView.OnScrollListener scrollListener;
    private final List<CustomerVisit> loaded = new ArrayList<>();
    private final Set<String> loadedIds = new HashSet<>();
    private VisitHistoryFilter filter = VisitHistoryFilter.DEFAULT;
    private Call<VisitHistoryResponse> inFlight;
    private int generation = 0;
    private int nextOffset = 0;
    private int total = 0;
    private boolean hasMore = true;
    private boolean loading = false;
    private boolean visible = false;
    private boolean showingCached = false;
    private long lastLoadMoreFailureAt = 0L;

    public VisitHistoryController(@NonNull Fragment fragment, @NonNull View root,
                                @NonNull SessionManager session,
                                @NonNull CustomerVisitReportAdapter adapter) {
        this.fragment = fragment;
        this.session = session;
        this.adapter = adapter;
        this.repository = new VisitHistoryRepository(fragment.requireContext());
        filterContainer = root.findViewById(R.id.visitFilterContainer);
        ChipGroup statusChips = root.findViewById(R.id.visitStatusChipGroup);
        chipAll = root.findViewById(R.id.chipVisitAll);
        chipCompleted = root.findViewById(R.id.chipVisitCompleted);
        chipCancelled = root.findViewById(R.id.chipVisitCancelled);
        btnFrom = root.findViewById(R.id.btnVisitFrom);
        btnTo = root.findViewById(R.id.btnVisitTo);
        btnClearDates = root.findViewById(R.id.btnVisitClearDates);
        tvSummary = root.findViewById(R.id.tvVisitsSummary);
        tvEmpty = root.findViewById(R.id.tvVisitsEmpty);
        progress = root.findViewById(R.id.visitsProgressBar);
        loadMoreProgress = root.findViewById(R.id.visitsLoadMoreProgress);
        recycler = root.findViewById(R.id.visitsRecyclerView);
        statusChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            VisitHistoryFilter.Status status = VisitHistoryFilter.Status.ALL;
            if (!checkedIds.isEmpty()) {
                int id = checkedIds.get(0);
                if (id == R.id.chipVisitCompleted) status = VisitHistoryFilter.Status.COMPLETED;
                else if (id == R.id.chipVisitCancelled) status = VisitHistoryFilter.Status.CANCELLED;
            }
            if (status != filter.getStatus()) {
                filter = filter.withStatus(status);
                load(true);
            }
        });
        btnFrom.setOnClickListener(v -> pickDate(true));
        btnTo.setOnClickListener(v -> pickDate(false));
        btnClearDates.setOnClickListener(v -> {
            filter = filter.withFrom(null).withTo(null);
            updateDateButtons();
            load(true);
        });
        scrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || !visible) return;
                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                if (!(lm instanceof LinearLayoutManager)) return;
                int lastVisible = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
                if (lastVisible >= lm.getItemCount() - PREFETCH_DISTANCE) {
                    load(false);
                }
            }
        };
        recycler.addOnScrollListener(scrollListener);
        updateDateButtons();
    }

    // ------------------------------------------------------------------
    // Called by the reports screen
    // ------------------------------------------------------------------
    /** The "Past visits" tab was selected: show the filters and load the first page. */
    public void show() {
        visible = true;
        filterContainer.setVisibility(View.VISIBLE);
        load(true);
    }

    /** Another tab was selected: stop loading and hide the filters. */
    public void hide() {
        visible = false;
        cancelInFlight();
        filterContainer.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        loadMoreProgress.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
    }

    /** The screen's view is being destroyed. */
    public void release() {
        visible = false;
        cancelInFlight();
        mainHandler.removeCallbacksAndMessages(null);
        recycler.removeOnScrollListener(scrollListener);
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------
    /**
     * @param reset true to start again from the first page (filter changed), false to append the next page
     */
    private void load(boolean reset) {
        if (!visible || isNotUsable()) return;
        if (reset) {
            cancelInFlight();
            nextOffset = 0;
            hasMore = true;
            showingCached = false;
            clearLoaded();
            tvEmpty.setVisibility(View.GONE);
            tvSummary.setText("");
            progress.setVisibility(View.VISIBLE);
        } else {
            if (loading || !hasMore || showingCached) return;
            if (SystemClock.elapsedRealtime() - lastLoadMoreFailureAt < LOAD_MORE_RETRY_DELAY_MS) return;
            loadMoreProgress.setVisibility(View.VISIBLE);
        }
        loading = true;
        final int myGeneration = ++generation;
        final int requestOffset = reset ? 0 : nextOffset;
        inFlight = repository.loadPage(session.getUserId(), filter, requestOffset, PAGE_SIZE,
                new VisitHistoryRepository.PageCallback() {
                    @Override
                    public void onSuccess(@NonNull VisitHistoryPage page) {
                        if (myGeneration != generation || isNotUsable()) return;
                        onPageLoaded(page, reset);
                    }

                    @Override
                    public void onError(@NonNull String message, boolean networkError) {
                        if (myGeneration != generation || isNotUsable()) return;
                        onPageFailed(message, networkError, reset);
                    }
                });
    }

    private void onPageLoaded(@NonNull VisitHistoryPage page, boolean reset) {
        loading = false;
        progress.setVisibility(View.GONE);
        loadMoreProgress.setVisibility(View.GONE);
        appendDeduplicated(page.getVisits());
        nextOffset += page.getReturned();
        hasMore = page.hasMore();
        total = page.getTotal();
        if (reset && page.getCounts() != null) {
            updateChipCounts(page.getCounts());
        }
        publish();
    }

    private void onPageFailed(@NonNull String message, boolean networkError, boolean reset) {
        loading = false;
        progress.setVisibility(View.GONE);
        loadMoreProgress.setVisibility(View.GONE);
        if (!reset) {
            lastLoadMoreFailureAt = SystemClock.elapsedRealtime();
            Toasty.error(fragment.requireContext(), message, Toasty.LENGTH_SHORT).show();
            return;
        }
        if (networkError) {
            showCachedFallback();
            return;
        }
        Toasty.error(fragment.requireContext(), message, Toasty.LENGTH_SHORT).show();
        publish();
    }

    private void cancelInFlight() {
        generation++; // anything still on its way is ignored
        if (inFlight != null) {
            inFlight.cancel();
            inFlight = null;
        }
        loading = false;
    }

    // ------------------------------------------------------------------
    // Offline fallback: the visits stored on this phone
    // ------------------------------------------------------------------
    private void showCachedFallback() {
        final int myGeneration = ++generation;
        progress.setVisibility(View.VISIBLE);
        CustomerVisitRepository.getInstance(fragment.requireContext()).getVisitsByUser(
                session.getUserId(), new CustomerVisitRepository.VisitListCallback() {
                    @Override
                    public void onSuccess(List<CustomerVisit> visits) {
                        mainHandler.post(() -> {
                            if (myGeneration != generation || isNotUsable()) return;
                            progress.setVisibility(View.GONE);
                            showingCached = true;
                            hasMore = false;
                            clearLoaded();
                            List<CustomerVisit> matching = new ArrayList<>();
                            if (visits != null) {
                                for (CustomerVisit v : visits) {
                                    if (matchesFilter(v)) matching.add(v);
                                }
                            }
                            matching.sort((a, b) -> Long.compare(eventTime(b), eventTime(a)));
                            appendDeduplicated(matching);
                            total = loaded.size();
                            publish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        mainHandler.post(() -> {
                            if (myGeneration != generation || isNotUsable()) return;
                            progress.setVisibility(View.GONE);
                            Toasty.error(fragment.requireContext(), message, Toasty.LENGTH_SHORT).show();
                            publish();
                        });
                    }
                });
    }

    private boolean matchesFilter(@NonNull CustomerVisit v) {
        String status = v.visitStatus != null ? v.visitStatus : "";
        boolean isFinished = "COMPLETED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status);
        if (!isFinished) return false;
        VisitHistoryFilter.Status wanted = filter.getStatus();
        if (wanted != VisitHistoryFilter.Status.ALL && !wanted.name().equalsIgnoreCase(status)) {
            return false;
        }
        long time = eventTime(v);
        if (filter.getFrom() != null) {
            long from = startOfDayMillis(filter.getFrom(), 0);
            if (from > 0 && time < from) return false;
        }
        if (filter.getTo() != null) {
            long toExclusive = startOfDayMillis(filter.getTo(), 1);
            return toExclusive <= 0 || time < toExclusive;
        }
        return true;
    }

    /** When the visit happened: ended, else started, else scheduled (same rule as the server). */
    private static long eventTime(@NonNull CustomerVisit v) {
        if (v.endedAt != null && v.endedAt > 0) return v.endedAt;
        if (v.startedAt > 0) return v.startedAt;
        return v.scheduledAt != null ? v.scheduledAt : 0L;
    }

    private static long startOfDayMillis(@NonNull String date, int addDays) {
        Date parsed = parseDate(date);
        if (parsed == null) return 0L;
        Calendar cal = Calendar.getInstance();
        cal.setTime(parsed);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, addDays);
        return cal.getTimeInMillis();
    }

    // ------------------------------------------------------------------
    // Result list and screen state
    // ------------------------------------------------------------------
    private void clearLoaded() {
        loaded.clear();
        loadedIds.clear();
        total = 0;
        adapter.setVisits(new ArrayList<>());
    }

    /** Adds visits that are not on the list yet (a visit can shift between pages while loading). */
    private void appendDeduplicated(@NonNull List<CustomerVisit> visits) {
        for (CustomerVisit v : visits) {
            String key = v.visitId != null ? v.visitId : "row-" + loaded.size();
            if (loadedIds.add(key)) loaded.add(v);
        }
    }

    private void publish() {
        adapter.setVisits(new ArrayList<>(loaded));
        tvEmpty.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE);
        if (loaded.isEmpty()) {
            tvSummary.setText("");
        } else if (showingCached) {
            tvSummary.setText(R.string.visits_showing_cached);
        } else {
            tvSummary.setText(fragment.getString(R.string.visits_showing_format, loaded.size(), total));
        }
    }

    private void updateChipCounts(@NonNull Map<String, Integer> counts) {
        int completed = valueOf(counts.get("COMPLETED"));
        int cancelled = valueOf(counts.get("CANCELLED"));
        chipAll.setText(fragment.getString(R.string.visits_filter_all_count, completed + cancelled));
        chipCompleted.setText(fragment.getString(R.string.visits_filter_completed_count, completed));
        chipCancelled.setText(fragment.getString(R.string.visits_filter_cancelled_count, cancelled));
    }

    private static int valueOf(@Nullable Integer value) {
        return value != null ? value : 0;
    }

    // ------------------------------------------------------------------
    // Date range
    // ------------------------------------------------------------------
    private void pickDate(boolean isFrom) {
        if (isNotUsable()) return;
        Calendar cal = Calendar.getInstance();
        Date current = parseDate(isFrom ? filter.getFrom() : filter.getTo());
        if (current != null) cal.setTime(current);
        new DatePickerDialog(fragment.requireContext(),
                (view, year, month, day) -> onDatePicked(isFrom, year, month, day),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void onDatePicked(boolean isFrom, int year, int month, int day) {
        if (isNotUsable()) return;
        String picked = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
        String from = isFrom ? picked : filter.getFrom();
        String to = isFrom ? filter.getTo() : picked;
        // yyyy-MM-dd strings sort in date order
        if (from != null && to != null && from.compareTo(to) > 0) {
            Toasty.warning(fragment.requireContext(),
                    fragment.getString(R.string.visits_date_range_invalid),
                    Toasty.LENGTH_SHORT).show();
            return;
        }
        filter = filter.withFrom(from).withTo(to);
        updateDateButtons();
        load(true);
    }

    private void updateDateButtons() {
        Context context = fragment.getContext();
        if (context == null) return;
        btnFrom.setText(filter.getFrom() != null
                ? context.getString(R.string.visits_from_format, filter.getFrom())
                : context.getString(R.string.visits_from_any));
        btnTo.setText(filter.getTo() != null
                ? context.getString(R.string.visits_to_format, filter.getTo())
                : context.getString(R.string.visits_to_any));
        btnClearDates.setEnabled(filter.hasDateRange());
    }

    @Nullable
    private static Date parseDate(@Nullable String value) {
        if (value == null) return null;
        try {
            SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN, Locale.US);
            format.setLenient(false);
            return format.parse(value);
        } catch (ParseException e) {
            return null;
        }
    }

    private boolean isNotUsable() {
        return !(fragment.isAdded() && fragment.getView() != null);
    }
}