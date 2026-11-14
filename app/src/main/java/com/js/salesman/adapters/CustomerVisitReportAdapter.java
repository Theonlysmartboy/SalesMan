package com.js.salesman.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.CustomerVisit;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CustomerVisitReportAdapter extends RecyclerView.Adapter<CustomerVisitReportAdapter.VisitViewHolder> {

    private final List<CustomerVisit> visits = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    public void setVisits(List<CustomerVisit> newVisits) {
        List<CustomerVisit> incoming = (newVisits != null) ? new ArrayList<>(newVisits) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new VisitDiffCallback(visits, incoming));
        visits.clear();
        visits.addAll(incoming);
        diff.dispatchUpdatesTo(this);
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
        holder.bind(visits.get(position), dateFormat);
    }

    @Override
    public int getItemCount() {
        return visits.size();
    }

    public static class VisitViewHolder extends RecyclerView.ViewHolder {
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
            tvVisitId     = itemView.findViewById(R.id.tvVisitId);
            tvTimes       = itemView.findViewById(R.id.tvTimes);
            tvDuration    = itemView.findViewById(R.id.tvDuration);
            tvLocation    = itemView.findViewById(R.id.tvLocation);
            tvNotes       = itemView.findViewById(R.id.tvNotes);
        }
        public void bind(CustomerVisit visit, SimpleDateFormat dateFormat) {
            final android.content.Context ctx = itemView.getContext();
            tvBusinessName.setText(
                    (visit.businessName != null && !visit.businessName.isEmpty())
                            ? visit.businessName
                            : ctx.getString(R.string.customer_visit));
            String status = (visit.visitStatus != null)
                    ? visit.visitStatus
                    : ctx.getString(R.string.status_unknown);
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
            String displayId = (visit.serverId != null && !visit.serverId.isEmpty())
                    ? visit.serverId
                    : visit.visitId;
            tvVisitId.setText(ctx.getString(
                    R.string.visit_id_format,
                    displayId != null ? displayId : ctx.getString(R.string.unassigned)));
            if ("SCHEDULED".equalsIgnoreCase(status)) {
                long schedTime = (visit.scheduledAt != null) ? visit.scheduledAt : visit.startedAt;
                tvTimes.setText(ctx.getString(
                        R.string.scheduled_format,
                        schedTime > 0 ? dateFormat.format(new Date(schedTime)) : ctx.getString(
                                R.string.na)));
                tvDuration.setVisibility(View.GONE);
            } else {
                String startStr = visit.startedAt > 0
                        ? dateFormat.format(new Date(visit.startedAt))
                        : ctx.getString(R.string.na);
                String endStr = (visit.endedAt != null && visit.endedAt > 0)
                        ? dateFormat.format(new Date(visit.endedAt))
                        : ctx.getString(R.string.na);
                tvTimes.setText(ctx.getString(R.string.started_ended_format, startStr, endStr));
                if (visit.durationSeconds > 0) {
                    tvDuration.setVisibility(View.VISIBLE);
                    long mins = visit.durationSeconds / 60;
                    long secs = visit.durationSeconds % 60;
                    tvDuration.setText(ctx.getString(R.string.duration_format, mins, secs));
                } else {
                    tvDuration.setVisibility(View.GONE);
                }
            }
            StringBuilder locText = new StringBuilder();
            if (visit.startLatitude != 0.0 || visit.startLongitude != 0.0) {
                locText.append(ctx.getString(
                        R.string.start_loc_format, visit.startLatitude, visit.startLongitude));
            }
            if (visit.endLatitude != null && visit.endLongitude != null
                    && (visit.endLatitude != 0.0 || visit.endLongitude != 0.0)) {
                if (locText.length() > 0) locText.append(ctx.getString(R.string.loc_separator));
                locText.append(ctx.getString(
                        R.string.end_loc_format, visit.endLatitude, visit.endLongitude));
            }
            if (locText.length() > 0) {
                tvLocation.setVisibility(View.VISIBLE);
                tvLocation.setText(locText.toString());
            } else {
                tvLocation.setVisibility(View.GONE);
            }
            String notesStr = (visit.notes != null) ? visit.notes : "";
            if (visit.cancelReason != null && !visit.cancelReason.isEmpty()) {
                notesStr = ctx.getString(R.string.reason_format, visit.cancelReason)
                        + (notesStr.isEmpty()
                        ? ""
                        : ctx.getString(R.string.notes_separator) + notesStr);
            }
            if (!notesStr.isEmpty()) {
                tvNotes.setVisibility(View.VISIBLE);
                tvNotes.setText(ctx.getString(R.string.notes_format, notesStr));
            } else {
                tvNotes.setVisibility(View.GONE);
            }
        }
    }

    private static class VisitDiffCallback extends DiffUtil.Callback {
        private final List<CustomerVisit> oldList;
        private final List<CustomerVisit> newList;
        VisitDiffCallback(List<CustomerVisit> oldList, List<CustomerVisit> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int o, int n) {
            CustomerVisit a = oldList.get(o);
            CustomerVisit b = newList.get(n);
            return Objects.equals(a.visitId, b.visitId)
                    && Objects.equals(a.serverId, b.serverId);
        }

        @Override
        public boolean areContentsTheSame(int o, int n) {
            CustomerVisit a = oldList.get(o);
            CustomerVisit b = newList.get(n);
            return Objects.equals(a.businessName, b.businessName)
                    && Objects.equals(a.visitStatus, b.visitStatus)
                    && Objects.equals(a.scheduledAt, b.scheduledAt)
                    && a.startedAt == b.startedAt
                    && Objects.equals(a.endedAt, b.endedAt)
                    && a.durationSeconds == b.durationSeconds
                    && a.startLatitude == b.startLatitude
                    && a.startLongitude == b.startLongitude
                    && Objects.equals(a.endLatitude, b.endLatitude)
                    && Objects.equals(a.endLongitude, b.endLongitude)
                    && Objects.equals(a.notes, b.notes)
                    && Objects.equals(a.cancelReason, b.cancelReason);
        }
    }
}