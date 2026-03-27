package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.Customer;

import java.util.ArrayList;
import java.util.List;

public class CustomerSelectAdapter extends RecyclerView.Adapter<CustomerSelectAdapter.ViewHolder> {

    private final List<Customer> customerList = new ArrayList<>();
    private final OnCustomerSelectedListener listener;

    public interface OnCustomerSelectedListener {
        void onCustomerSelected(Customer customer);
    }

    public CustomerSelectAdapter(OnCustomerSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_customer_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Customer customer = customerList.get(position);
        holder.name.setText(customer.getCustomerName());
        holder.code.setText(customer.getCustomerCode());
        holder.itemView.setOnClickListener(v -> listener.onCustomerSelected(customer));
    }

    @Override
    public int getItemCount() {
        return customerList.size();
    }

    public void addCustomers(List<Customer> customers) {
        int start = customerList.size();
        customerList.addAll(customers);
        notifyItemRangeInserted(start, customers.size());
    }

    public void setCustomers(List<Customer> customers) {
        customerList.clear();
        customerList.addAll(customers);
        notifyDataSetChanged();
    }

    public void clear() {
        customerList.clear();
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, code;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvCustomerName);
            code = itemView.findViewById(R.id.tvCustomerCode);
        }
    }
}
