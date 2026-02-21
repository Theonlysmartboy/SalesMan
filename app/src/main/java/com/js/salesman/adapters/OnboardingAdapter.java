package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.js.salesman.R;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {

    // slide data
    private final String[] animations = {
            "dashboard.json",
            "customers.json",
            "orders.json",
            "reports.json"
    };

    private final String[] titles = {
            "Sales Dashboard",
            "Customer Management",
            "Order Tracking",
            "Smart Reports"
    };

    private final String[] descriptions = {
            "Monitor your sales performance in real time.",
            "Manage customers and leads efficiently.",
            "Track orders and payments seamlessly.",
            "Generate powerful business insights."
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.animation.setAnimation(animations[position]);
        holder.animation.playAnimation();
        holder.title.setText(titles[position]);
        holder.description.setText(descriptions[position]);
    }

    @Override
    public int getItemCount() {
        return titles.length;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LottieAnimationView animation;
        TextView title, description;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            animation = itemView.findViewById(R.id.slideAnimation);
            title = itemView.findViewById(R.id.slideTitle);
            description = itemView.findViewById(R.id.slideDescription);
        }
    }
}