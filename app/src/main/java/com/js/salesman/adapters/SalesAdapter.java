package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.Order;

import java.util.ArrayList;
import java.util.List;

public class SalesAdapter extends RecyclerView.Adapter<SalesAdapter.ViewHolder> {

    private List<Order> orderList = new ArrayList<>();

    public void setOrders(List<Order> orders) {
        this.orderList = orders;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orderList.get(position);
        holder.tvOrderNo.setText("Order #" + order.getOrderNo());
        holder.tvCustomerName.setText(order.getCustomerName());
        holder.tvProductName.setText(order.getProductName() + " (Qty: " + order.getQuantity() + ")");
        holder.tvOrderDate.setText("Date: " + order.getOrderDate());
        holder.tvTotalAmount.setText("KES " + order.getTotalAmount());
        holder.tvStatus.setText(order.getStatus());
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderNo, tvCustomerName, tvProductName, tvOrderDate, tvTotalAmount, tvStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderNo = itemView.findViewById(R.id.tvOrderNo);
            tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            tvStatus = itemView.findViewById(R.id.tvOrderStatus);
        }
    }
}
