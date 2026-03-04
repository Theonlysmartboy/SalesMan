package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.js.salesman.R;
import com.js.salesman.models.Product;

import java.util.ArrayList;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    private List<Product> productList;

    public ProductAdapter() {
        this.productList = new ArrayList<>();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView name, unitPrice, stock;
        ImageView image;

        public ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.productName);
            unitPrice = itemView.findViewById(R.id.productUnitPrice);
            stock = itemView.findViewById(R.id.productStock);
            image = itemView.findViewById(R.id.productImage);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        Product product = productList.get(position);

        holder.name.setText(product.getProductName());
        holder.unitPrice.setText(product.getProductUnit() + " | " + product.getProduct_Selling_Price()
        );
        holder.stock.setText("Stock: " + product.getStockQty());

        Glide.with(holder.itemView.getContext())
                .load(product.getImg_src())
                .placeholder(R.drawable.ic_product_placeholder)
                .into(holder.image);
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public void addProducts(List<Product> newProducts) {
        int start = productList.size();
        productList.addAll(newProducts);
        notifyItemRangeInserted(start, newProducts.size());
    }

    public void clearProducts() {
        productList.clear();
        notifyDataSetChanged();
    }
    public void setProducts(List<Product> products) {
        this.productList = products;
        notifyDataSetChanged();
    }
}
