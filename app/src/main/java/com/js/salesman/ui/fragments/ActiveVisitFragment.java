package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.viewmodels.ActiveVisitViewModel;

import java.util.Locale;

import es.dmoral.toasty.Toasty;

public class ActiveVisitFragment extends Fragment {

    private ActiveVisitViewModel viewModel;
    private TextView tvBusinessName, tvVisitId, tvTimer;
    private EditText etNotes;
    private MaterialButton btnEnd, btnCancel;
    private CustomerVisit currentVisit;
    private boolean actionInProgress = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_active_visit, container, false);

        tvBusinessName = view.findViewById(R.id.tvActiveBusinessName);
        tvVisitId = view.findViewById(R.id.tvActiveVisitId);
        tvTimer = view.findViewById(R.id.tvActiveTimer);
        etNotes = view.findViewById(R.id.etVisitNotes);
        btnEnd = view.findViewById(R.id.btnEndVisit);
        btnCancel = view.findViewById(R.id.btnCancelVisit);

        viewModel = new ViewModelProvider(this).get(ActiveVisitViewModel.class);

        viewModel.getActiveVisit().observe(getViewLifecycleOwner(), visit -> {
            this.currentVisit = visit;
            if (visit != null) {
                tvBusinessName.setText(visit.businessName != null ? visit.businessName : "Active Customer Visit");
                String displayId = (visit.serverId != null && !visit.serverId.isEmpty()) ? visit.serverId : (visit.visitId != null ? visit.visitId : visit.clientVisitId);
                tvVisitId.setText(String.format("Visit ID: %s", displayId));
                if (visit.notes != null) {
                    etNotes.setText(visit.notes);
                }
                startTimer();
            } else {
                tvBusinessName.setText(R.string.no_active_visit);
                tvVisitId.setText("");
                tvTimer.setText(R.string._00_00_00);
                stopTimer();
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty()) {
                actionInProgress = false;
                setButtonsEnabled(true);
                Toasty.error(requireContext(), err, Toasty.LENGTH_SHORT).show();
            }
        });
        viewModel.getActionCompleted().observe(getViewLifecycleOwner(), completed -> {
            if (Boolean.TRUE.equals(completed)) {
                Toasty.success(requireContext(), "Visit updated successfully.",
                        Toasty.LENGTH_SHORT).show();
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });
        btnEnd.setOnClickListener(v -> {
            if (currentVisit != null && !actionInProgress) {
                actionInProgress = true;
                setButtonsEnabled(false);
                String notes = etNotes.getText().toString().trim();
                String targetId = (currentVisit.visitId != null && !currentVisit.visitId.isEmpty()) ? currentVisit.visitId : currentVisit.clientVisitId;
                viewModel.endVisit(targetId, currentVisit.startLatitude,
                        currentVisit.startLongitude, notes);
            }
        });
        btnCancel.setOnClickListener(v -> {
            if (currentVisit != null && !actionInProgress) {
                actionInProgress = true;
                setButtonsEnabled(false);
                String reason = etNotes.getText().toString().trim();
                String targetId = (currentVisit.visitId != null && !currentVisit.visitId.isEmpty()) ? currentVisit.visitId : currentVisit.clientVisitId;
                viewModel.cancelVisit(targetId, reason.isEmpty() ? "Cancelled by user" : reason);
            }
        });
        viewModel.loadActiveVisit();
        return view;
    }

    private void setButtonsEnabled(boolean enabled) {
        if (btnEnd != null) btnEnd.setEnabled(enabled);
        if (btnCancel != null) btnCancel.setEnabled(enabled);
    }

    private void startTimer() {
        stopTimer();
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateTimer() {
        if (currentVisit == null || currentVisit.startedAt <= 0) {
            tvTimer.setText(R.string._00_00_00);
            return;
        }
        long elapsedMs = Math.max(0, System.currentTimeMillis() - currentVisit.startedAt);
        long seconds = (elapsedMs / 1000) % 60;
        long minutes = (elapsedMs / (1000 * 60)) % 60;
        long hours = elapsedMs / (1000 * 60 * 60);
        tvTimer.setText(String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
    }
}
