package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.Order;
import com.js.salesman.utils.DiffCallBack;

import java.util.ArrayList;
import java.util.List;

public class SalesAdapter extends RecyclerView.Adapter<SalesAdapter.ViewHolder> {

    private List<Order> orderList = new ArrayList<>();

    public void setOrders(List<Order> newOrders) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallBack(this.orderList, newOrders));
        this.orderList = newOrders;
        diffResult.dispatchUpdatesTo(this);
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
        holder.tvOrderNo.setText(
                holder.itemView.getContext().getString(R.string.order_number,
                        order.getOrderNo()));
        holder.tvCustomerName.setText(order.getCustomerName());
        holder.tvProductName.setText(
                holder.itemView.getContext().getString(R.string.product_with_qty,
                        order.getProductName(), order.getLineCount()));
        holder.tvOrderDate.setText(
                holder.itemView.getContext().getString(R.string.order_date,
                        order.getOrderDate()));
        holder.tvTotalAmount.setText(
                holder.itemView.getContext().getString(R.string.currency_kes,
                        order.getTotalAmount()));
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
