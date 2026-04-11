package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.Product;

import java.util.ArrayList;
import java.util.List;

public class ProductSelectAdapter extends RecyclerView.Adapter<ProductSelectAdapter.ViewHolder> {

    private final List<Product> productList = new ArrayList<>();
    private final OnProductSelectedListener listener;

    public interface OnProductSelectedListener {
        void onProductSelected(Product product);
    }

    public ProductSelectAdapter(OnProductSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.name.setText(product.getProductName());
        holder.code.setText(product.getProductCode());
        holder.itemView.setOnClickListener(v -> listener.onProductSelected(product));
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public void addProducts(List<Product> products) {
        int start = productList.size();
        productList.addAll(products);
        notifyItemRangeInserted(start, products.size());
    }

    public void setProducts(List<Product> products) {
        productList.clear();
        productList.addAll(products);
        notifyDataSetChanged();
    }

    public void clear() {
        productList.clear();
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, code;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvProductName);
            code = itemView.findViewById(R.id.tvProductCode);
        }
    }
}
