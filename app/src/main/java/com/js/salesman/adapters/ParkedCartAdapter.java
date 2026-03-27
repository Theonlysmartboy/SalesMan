package com.js.salesman.adapters;

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
import java.util.Locale;

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
        long id = Long.parseLong(cart.get("id"));
        String name = cart.get("name");
        String date = cart.get("created_at");
        int count = Integer.parseInt(cart.get("item_count"));
        double total = Double.parseDouble(cart.get("total_amount") != null ? cart.get("total_amount") : "0");

        holder.tvCartName.setText(name != null && !name.isEmpty() ? name : "Parked Cart #" + id);
        holder.tvCreatedAt.setText(date);
        holder.tvItemCount.setText("Items: " + count);
        holder.tvTotalAmount.setText(String.format(Locale.getDefault(), "KES %.2f", total));

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
