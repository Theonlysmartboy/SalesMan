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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.viewmodels.ScheduledVisitsViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import es.dmoral.toasty.Toasty;

public class ScheduledVisitsFragment extends Fragment {

    private ScheduledVisitsViewModel viewModel;
    private ScheduledAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scheduled_visits, container,
                false);
        RecyclerView recyclerView = view.findViewById(R.id.rvScheduledVisits);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new ScheduledAdapter();
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(ScheduledVisitsViewModel.class);

        viewModel.getScheduledVisits().observe(getViewLifecycleOwner(),
                visits -> adapter.setVisits(visits));

        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty()) {
                Toasty.error(requireContext(), err, Toasty.LENGTH_LONG).show();
            }
        });

        viewModel.getOperationSuccess().observe(getViewLifecycleOwner(), ok -> {
            if (Boolean.TRUE.equals(ok)) {
                Toasty.success(requireContext(), "Scheduled visit updated.",
                        Toasty.LENGTH_SHORT).show();
            }
        });

        viewModel.loadScheduledVisits();
        return view;
    }

    private class ScheduledAdapter extends RecyclerView.Adapter<ScheduledAdapter.ViewHolder> {

        private List<CustomerVisit> list = new ArrayList<>();

        public void setVisits(List<CustomerVisit> visits) {
            this.list = visits != null ? visits : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout
                    .simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CustomerVisit visit = list.get(position);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            String timeStr = visit.scheduledAt != null ? sdf.format(new Date(visit
                                                                .scheduledAt)) : "No date";

            boolean isOverdue = visit.scheduledAt != null && visit.scheduledAt < System
                    .currentTimeMillis();
            String title = (visit.businessName != null ? visit.businessName : "Customer") +
                    (isOverdue ? " [OVERDUE]" : "");

            holder.text1.setText(title);
            holder.text2.setText(String.format("Scheduled: %s", timeStr));
            holder.itemView.setOnClickListener(v -> viewModel.startScheduledVisit(visit
                    .visitId, visit.startLatitude, visit.startLongitude));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
    }
}
