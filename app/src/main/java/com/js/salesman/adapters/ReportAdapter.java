package com.js.salesman.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.js.salesman.R;
import com.js.salesman.models.ReportEntry;
import java.util.List;
import java.util.Locale;

public class ReportAdapter extends ArrayAdapter<ReportEntry> {
    private final LayoutInflater inflater;

    public ReportAdapter(@NonNull Context context, @NonNull List<ReportEntry> objects) {
        super(context, 0, objects);
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_report, parent, false);
        }

        ReportEntry entry = getItem(position);
        TextView tvLabel = convertView.findViewById(R.id.tvLabel);
        TextView tvOrders = convertView.findViewById(R.id.tvOrders);
        TextView tvAmount = convertView.findViewById(R.id.tvAmount);

        if (entry != null) {
            tvLabel.setText(entry.getLabel());
            tvOrders.setText(String.valueOf(entry.getTotalOrders()));
            tvAmount.setText(String.format(Locale.getDefault(), "%.2f", entry.getTotalAmount()));
        }

        return convertView;
    }
}
