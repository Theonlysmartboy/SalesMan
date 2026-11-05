package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;
import com.js.salesman.viewmodels.SyncStatusViewModel;

import es.dmoral.toasty.Toasty;

public class SyncStatusFragment extends Fragment {

    private static final long FIRST_REFRESH_MS = 1500;
    private static final long SECOND_REFRESH_MS = 5000;

    private SyncStatusViewModel viewModel;
    private TextView tvPendingVisits, tvFailedVisits, tvPendingOrders, tvFailedOrders,
            tvPendingTracking;
    private MaterialButton btnRetry;

    private final Runnable refreshCounts = () -> {
        if (viewModel != null) viewModel.refreshCounts();
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sync_status, container, false);
        tvPendingVisits = view.findViewById(R.id.tvPendingVisitsCount);
        tvFailedVisits = view.findViewById(R.id.tvFailedVisitsCount);
        tvPendingOrders = view.findViewById(R.id.tvPendingOrdersCount);
        tvFailedOrders = view.findViewById(R.id.tvFailedOrdersCount);
        tvPendingTracking = view.findViewById(R.id.tvPendingTrackingCount);
        btnRetry = view.findViewById(R.id.btnRetrySyncNow);
        viewModel = new ViewModelProvider(this).get(SyncStatusViewModel.class);
        viewModel.getPendingVisits().observe(getViewLifecycleOwner(),
                cnt -> tvPendingVisits.setText(getString(R.string.pending_visits, cnt)));
        viewModel.getFailedVisits().observe(getViewLifecycleOwner(),
                cnt -> tvFailedVisits.setText(getString(R.string.failed_visits, cnt)));
        viewModel.getPendingOrders().observe(getViewLifecycleOwner(),
                cnt -> tvPendingOrders.setText(getString(R.string.pending_orders, cnt)));
        viewModel.getFailedOrders().observe(getViewLifecycleOwner(),
                cnt -> tvFailedOrders.setText(getString(R.string.failed_orders, cnt)));
        viewModel.getPendingTracking().observe(getViewLifecycleOwner(),
                cnt -> tvPendingTracking.setText(
                        getString(R.string.pending_tracking_points, cnt)));
        btnRetry.setOnClickListener(v -> {
            btnRetry.setEnabled(false);
            viewModel.triggerSyncNow();
            Toasty.info(requireContext(), "Sync triggered.",
                    Toasty.LENGTH_SHORT, true).show();
            v.postDelayed(refreshCounts, FIRST_REFRESH_MS);
            v.postDelayed(refreshCounts, SECOND_REFRESH_MS);
            v.postDelayed(() -> btnRetry.setEnabled(true), FIRST_REFRESH_MS);
        });
        viewModel.refreshCounts();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.refreshCounts();
    }

    @Override
    public void onDestroyView() {
        if (btnRetry != null) btnRetry.removeCallbacks(refreshCounts);
        btnRetry = null;
        super.onDestroyView();
    }
}