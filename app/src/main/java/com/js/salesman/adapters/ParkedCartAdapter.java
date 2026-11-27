package com.js.salesman.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ParkedCartAdapter extends RecyclerView.Adapter<ParkedCartAdapter.ViewHolder> {

    private final List<HashMap<String, String>> parkedCarts;
    private final OnParkedCartInteractionListener listener;

    public interface OnParkedCartInteractionListener {
        void onRestore(long cartId);
        void onDelete(long cartId);
    }

    public ParkedCartAdapter(List<HashMap<String, String>> parkedCarts, OnParkedCartInteractionListener listener) {
        this.parkedCarts = parkedCarts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_parked_cart, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HashMap<String, String> cart = parkedCarts.get(position);
        long id = Long.parseLong(Objects.requireNonNull(cart.get("id")));
        String name = cart.get("name");
        String date = cart.get("created_at");
        int count = Integer.parseInt(Objects.requireNonNull(cart.get("item_count")));
        String totalRaw = cart.get("total_amount");
        double total = (totalRaw != null && !totalRaw.isEmpty())
                ? Double.parseDouble(totalRaw)
                : 0d;
        Context ctx = holder.itemView.getContext();
        holder.tvCartName.setText(
                (name != null && !name.isEmpty())
                        ? name
                        : ctx.getString(R.string.parked_cart_name_fallback, id));
        holder.tvCreatedAt.setText(date != null ? date : "");
        holder.tvItemCount.setText(ctx.getString(R.string.parked_cart_items_format, count));
        holder.tvTotalAmount.setText(ctx.getString(R.string.parked_cart_total_format, total));
        holder.btnRestore.setOnClickListener(v -> listener.onRestore(id));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(id));
    }

    @Override
    public int getItemCount() {
        return parkedCarts.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCartName, tvCreatedAt, tvItemCount, tvTotalAmount;
        MaterialButton btnRestore, btnDelete;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCartName = itemView.findViewById(R.id.tvCartName);
            tvCreatedAt = itemView.findViewById(R.id.tvCreatedAt);
            tvItemCount = itemView.findViewById(R.id.tvItemCount);
            tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            btnRestore = itemView.findViewById(R.id.btnRestore);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
