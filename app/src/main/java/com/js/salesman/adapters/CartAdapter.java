package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

    private final List<HashMap<String, String>> cartItems;
    private final OnCartItemChangeListener listener;

    public interface OnCartItemChangeListener {
        void onQuantityChanged(String productCode, int newQuantity);
        void onItemRemoved(String productCode);
        void onMoveToParked(String productCode);
    }

    public CartAdapter(List<HashMap<String, String>> cartItems, OnCartItemChangeListener listener) {
        this.cartItems = cartItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HashMap<String, String> item = cartItems.get(position);
        String code = item.get("product_code");
        String name = item.get("product_name");
        double price = Double.parseDouble(item.get("unit_price"));
        int qty = Integer.parseInt(item.get("quantity"));

        holder.tvProductName.setText(name);
        holder.tvUnitPrice.setText(String.format(Locale.getDefault(), "Unit Price: KES %.2f", price));
        holder.tvQuantity.setText(String.valueOf(qty));
        holder.tvLineTotal.setText(String.format(Locale.getDefault(), "Total: KES %.2f", price * qty));

        holder.btnPlus.setOnClickListener(v -> listener.onQuantityChanged(code, qty + 1));
        holder.btnMinus.setOnClickListener(v -> {
            if (qty > 1) {
                listener.onQuantityChanged(code, qty - 1);
            }
        });
        holder.btnRemove.setOnClickListener(v -> listener.onItemRemoved(code));
        holder.btnMoveToParked.setOnClickListener(v -> listener.onMoveToParked(code));
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvUnitPrice, tvLineTotal, tvQuantity;
        ImageButton btnPlus, btnMinus, btnRemove;
        MaterialButton btnMoveToParked;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvUnitPrice = itemView.findViewById(R.id.tvUnitPrice);
            tvLineTotal = itemView.findViewById(R.id.tvLineTotal);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            btnPlus = itemView.findViewById(R.id.btnPlus);
            btnMinus = itemView.findViewById(R.id.btnMinus);
            btnRemove = itemView.findViewById(R.id.btnRemove);
            btnMoveToParked = itemView.findViewById(R.id.btnMoveToParked);
        }
    }
}
