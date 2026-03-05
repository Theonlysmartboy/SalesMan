package com.js.salesman.adapters;

import android.content.Context;
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
import com.js.salesman.api.client.ApiClient;

import java.util.ArrayList;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    private final List<Product> productList;
    private final Product.OnProductClickListener listener; // listener reference

    public ProductAdapter(Product.OnProductClickListener listener) {
        this.productList = new ArrayList<>();
        this.listener = listener; // assign listener
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView name, code, unitPrice, stock;
        ImageView image, productStatus;

        public ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.productName);
            code = itemView.findViewById(R.id.productCode);
            unitPrice = itemView.findViewById(R.id.productUnitPrice);
            stock = itemView.findViewById(R.id.productStock);
            image = itemView.findViewById(R.id.productImage);
            productStatus = itemView.findViewById(R.id.productStatus);
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
        Context context = holder.itemView.getContext();
        holder.code.setText(context.getString(R.string.product_code_format, product.getProductCode()));
        holder.unitPrice.setText(context.getString(R.string.product_unit_price, product.getProduct_Selling_Price(), product.getProductUnit()));
        holder.stock.setText(context.getString(R.string.product_stock, product.getStockQty()));
        String img = product.getImg_src();
        if (img == null || img.trim().isEmpty()) {
            // Load default image from app
            Glide.with(holder.itemView.getContext())
                    .load(R.drawable.ic_product_placeholder)
                    .into(holder.image);
        } else {
            // Build server URL
            String imageUrl = ApiClient.getBaseUrl() + "assets/uploads/images/" + img;
            Glide.with(holder.itemView.getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_product_placeholder)
                    .into(holder.image);
        }
        if (product.getIsActive() == 1) {
            Glide.with(holder.itemView.getContext())
                    .load(R.drawable.ic_product_active)
                    .into(holder.productStatus);
        } else {
            Glide.with(holder.itemView.getContext())
                    .load(R.drawable.ic_product_placeholder)
                    .into(holder.productStatus);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProductClick(product.getProductCode());
            }
        });
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
        int size = productList.size();
        if (size > 0) {
            productList.clear();
            notifyItemRangeRemoved(0, size);
        }
    }
}
