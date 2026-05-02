package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.AlternateUnit;
import com.js.salesman.models.Customer;
import com.js.salesman.utils.PricingHelper;
import com.js.salesman.utils.managers.SessionManager;

import java.util.List;
import java.util.Locale;

public class AlternateUnitAdapter extends RecyclerView.Adapter<AlternateUnitAdapter.ViewHolder> {

    private final List<AlternateUnit> unitList;

    public AlternateUnitAdapter(List<AlternateUnit> unitList) {
        this.unitList = unitList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alternate_unit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AlternateUnit unit = unitList.get(position);
        holder.unitName.setText(unit.getAlternateUnit());
        holder.unitQty.setText(unit.getPrimaryQty());
        
        Customer customer = new SessionManager(holder.itemView.getContext()).getSelectedCustomer();
        String category = customer != null ? customer.getCategory() : null;
        double price = PricingHelper.getAlternatePrice(unit, category);
        
        holder.unitPrice.setText(holder.itemView.getContext().getString(R.string.product_unit_price,
                        String.format(Locale.getDefault(), "%.2f", price), 
                        unit.getAlternateUnit()));
    }

    @Override
    public int getItemCount() {
        return unitList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView unitName, unitQty, unitPrice;

        public ViewHolder(@NonNull View view) {
            super(view);
            unitName = view.findViewById(R.id.unitName);
            unitQty = view.findViewById(R.id.unitQty);
            unitPrice = view.findViewById(R.id.unitPrice);
        }
    }
}
