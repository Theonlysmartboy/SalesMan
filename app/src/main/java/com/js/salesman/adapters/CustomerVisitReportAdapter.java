package com.js.salesman.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.CustomerVisit;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomerVisitReportAdapter extends RecyclerView.Adapter<CustomerVisitReportAdapter.VisitViewHolder> {

    private final List<CustomerVisit> visits = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    public void setVisits(List<CustomerVisit> newVisits) {
        this.visits.clear();
        if (newVisits != null) {
            this.visits.addAll(newVisits);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VisitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer_visit_report, parent, false);
        return new VisitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VisitViewHolder holder, int position) {
        CustomerVisit visit = visits.get(position);
        holder.bind(visit, dateFormat);
    }

    @Override
    public int getItemCount() {
        return visits.size();
    }

    static class VisitViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvBusinessName;
        private final TextView tvVisitStatus;
        private final TextView tvVisitId;
        private final TextView tvTimes;
        private final TextView tvDuration;
        private final TextView tvLocation;
        private final TextView tvNotes;

        public VisitViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBusinessName = itemView.findViewById(R.id.tvBusinessName);
            tvVisitStatus = itemView.findViewById(R.id.tvVisitStatus);
            tvVisitId = itemView.findViewById(R.id.tvVisitId);
            tvTimes = itemView.findViewById(R.id.tvTimes);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvNotes = itemView.findViewById(R.id.tvNotes);
        }

        public void bind(CustomerVisit visit, SimpleDateFormat dateFormat) {
            tvBusinessName.setText(visit.businessName != null && !visit.businessName.isEmpty() ? visit.businessName : "Customer Visit");

            String status = visit.visitStatus != null ? visit.visitStatus : "UNKNOWN";
            tvVisitStatus.setText(status);
            if ("SCHEDULED".equalsIgnoreCase(status)) {
                tvVisitStatus.setBackgroundColor(Color.parseColor("#0288D1"));
            } else if ("COMPLETED".equalsIgnoreCase(status)) {
                tvVisitStatus.setBackgroundColor(Color.parseColor("#2E7D32"));
            } else if ("CANCELLED".equalsIgnoreCase(status)) {
                tvVisitStatus.setBackgroundColor(Color.parseColor("#C62828"));
            } else {
                tvVisitStatus.setBackgroundColor(Color.parseColor("#757575"));
            }

            String displayId = (visit.serverId != null && !visit.serverId.isEmpty()) ? visit.serverId : visit.visitId;
            tvVisitId.setText(String.format("Visit ID: %s", displayId != null ? displayId : "Unassigned"));

            if ("SCHEDULED".equalsIgnoreCase(status)) {
                long schedTime = visit.scheduledAt != null ? visit.scheduledAt : visit.startedAt;
                tvTimes.setText(String.format("Scheduled: %s", schedTime > 0 ? dateFormat.format(new Date(schedTime)) : "N/A"));
                tvDuration.setVisibility(View.GONE);
            } else {
                String startStr = visit.startedAt > 0 ? dateFormat.format(new Date(visit.startedAt)) : "N/A";
                String endStr = (visit.endedAt != null && visit.endedAt > 0) ? dateFormat.format(new Date(visit.endedAt)) : "N/A";
                tvTimes.setText(String.format("Started: %s  ·  Ended: %s", startStr, endStr));

                if (visit.durationSeconds > 0) {
                    tvDuration.setVisibility(View.VISIBLE);
                    long mins = visit.durationSeconds / 60;
                    long secs = visit.durationSeconds % 60;
                    tvDuration.setText(String.format(Locale.US, "Duration: %d min %d sec", mins, secs));
                } else {
                    tvDuration.setVisibility(View.GONE);
                }
            }

            StringBuilder locText = new StringBuilder();
            if (visit.startLatitude != 0.0 || visit.startLongitude != 0.0) {
                locText.append(String.format(Locale.US, "Start Loc: %.5f, %.5f", visit.startLatitude, visit.startLongitude));
            }
            if (visit.endLatitude != null && visit.endLongitude != null && (visit.endLatitude != 0.0 || visit.endLongitude != 0.0)) {
                if (locText.length() > 0) locText.append(" | ");
                locText.append(String.format(Locale.US, "End Loc: %.5f, %.5f", visit.endLatitude, visit.endLongitude));
            }
            if (locText.length() > 0) {
                tvLocation.setVisibility(View.VISIBLE);
                tvLocation.setText(locText.toString());
            } else {
                tvLocation.setVisibility(View.GONE);
            }

            String notesStr = visit.notes != null ? visit.notes : "";
            if (visit.cancelReason != null && !visit.cancelReason.isEmpty()) {
                notesStr = "Reason: " + visit.cancelReason + (notesStr.isEmpty() ? "" : " | " + notesStr);
            }
            if (!notesStr.isEmpty()) {
                tvNotes.setVisibility(View.VISIBLE);
                tvNotes.setText("Notes" + notesStr);
            } else {
                tvNotes.setVisibility(View.GONE);
            }
        }
    }
}
