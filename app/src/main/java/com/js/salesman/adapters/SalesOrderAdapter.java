package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.SalesOrderItem;
import com.js.salesman.utils.CurrencyFormatter;

import java.util.ArrayList;
import java.util.List;

public class SalesOrderAdapter extends RecyclerView.Adapter<SalesOrderAdapter.ViewHolder> {

    private final List<SalesOrderItem> items = new ArrayList<>();

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView product;
        TextView qty;
        TextView price;
        TextView total;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            product = itemView.findViewById(R.id.txtProduct);
            qty = itemView.findViewById(R.id.txtQty);
            price = itemView.findViewById(R.id.txtPrice);
            total = itemView.findViewById(R.id.txtTotal);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sales_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SalesOrderItem item = items.get(position);
        holder.product.setText(item.getName());
        holder.qty.setText(String.valueOf(item.getQuantity()));
        holder.price.setText(CurrencyFormatter.format(item.getPrice(), "Ksh"));
        holder.total.setText(CurrencyFormatter.format(item.getLineTotal()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void addItem(SalesOrderItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    public void removeItem(int position) {
        items.remove(position);
        notifyItemRemoved(position);
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public List<SalesOrderItem> getItems() {
        return items;
    }
}
