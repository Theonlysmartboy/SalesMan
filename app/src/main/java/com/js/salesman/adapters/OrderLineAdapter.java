package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.OrderLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderLineAdapter extends RecyclerView.Adapter<OrderLineAdapter.ViewHolder> {

    private List<OrderLine> lines = new ArrayList<>();

    public void setLines(List<OrderLine> lines) {
        this.lines = lines;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_line, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OrderLine line = lines.get(position);
        holder.tvProductName.setText(line.getProductName());
        holder.tvProductCode.setText(String.format("(%s)", line.getProductCode()));
        holder.tvQuantity.setText(String.format(Locale.getDefault(), "Qty: %s", line.getQuantity()));
        holder.tvUnitPrice.setText(String.format(Locale.getDefault(), "Price: %s", line.getUnitPrice()));
        holder.tvLineTotal.setText(String.format(Locale.getDefault(), "Total: KES %s", line.getLineTotal()));
        
        if (line.getVatAmountDouble() > 0) {
            holder.tvVat.setVisibility(View.VISIBLE);
            holder.tvVat.setText(String.format(Locale.getDefault(), "VAT: %s (%s%%)", line.getVatAmount(), line.getVatRate()));
        } else {
            holder.tvVat.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvProductCode, tvQuantity, tvUnitPrice, tvLineTotal, tvVat;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvProductCode = itemView.findViewById(R.id.tvProductCode);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvUnitPrice = itemView.findViewById(R.id.tvUnitPrice);
            tvLineTotal = itemView.findViewById(R.id.tvLineTotal);
            tvVat = itemView.findViewById(R.id.tvVat);
        }
    }
}
