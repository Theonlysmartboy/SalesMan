package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
    private OnItemRemovedListener listener;

    public interface OnItemRemovedListener {
        void onItemRemoved(SalesOrderItem item, int position);
    }

    public void setOnItemRemovedListener(OnItemRemovedListener listener) {
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView product, code, qty, price, total;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            product = itemView.findViewById(R.id.txtProduct);
            code = itemView.findViewById(R.id.txtProductCode);
            qty = itemView.findViewById(R.id.txtQty);
            price = itemView.findViewById(R.id.txtPrice);
            total = itemView.findViewById(R.id.txtTotal);
            btnDelete = itemView.findViewById(R.id.btnDelete);
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
        holder.code.setText(item.getCode());
        holder.qty.setText(String.format("%s %s", item.getQuantity(), item.getUnit()));
        holder.price.setText(CurrencyFormatter.format(item.getPrice()));
        holder.total.setText(CurrencyFormatter.format(item.getLineTotal()));
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onItemRemoved(item, pos);
                }
            }
        });
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
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize);
        }
    }

    public List<SalesOrderItem> getItems() {
        return items;
    }
}