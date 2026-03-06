package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.models.AlternateUnit;

import java.util.List;

public class AlternateUnitAdapter extends RecyclerView.Adapter<AlternateUnitAdapter.ViewHolder> {

    private final List<AlternateUnit> unitList;

    public AlternateUnitAdapter(List<AlternateUnit> unitList) {
        this.unitList = unitList;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView unitName, unitQty, unitPrice;

        public ViewHolder(View view) {
            super(view);
            unitName = view.findViewById(R.id.unitName);
            unitQty = view.findViewById(R.id.unitQty);
            unitPrice = view.findViewById(R.id.unitPrice);
        }
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
        holder.unitQty.setText(unit.getPrimaryQty() + " " + "DZN");
        holder.unitPrice.setText("Ksh " + unit.getAlternatePrice());
    }

    @Override
    public int getItemCount() {
        return unitList.size();
    }
}
