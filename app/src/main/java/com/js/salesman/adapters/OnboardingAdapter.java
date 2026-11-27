package com.js.salesman.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.js.salesman.R;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {

    private static final class Slide {
        final String animation, title, description;
        Slide(String animation, String title, String description) {
            this.animation = animation;
            this.title = title;
            this.description = description;
        }
    }

    private static final Slide[] SLIDES = {
            new Slide("dashboard.json", "Sales Dashboard",
                    "Monitor your sales performance in real time."),
            new Slide("customers.json", "Customer Management",
                    "Manage customers and leads efficiently."),
            new Slide("orders.json",    "Order Tracking",
                    "Track orders and payments seamlessly."),
            new Slide("reports.json",   "Smart Reports",
                    "Generate powerful business insights.")
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
        Slide slide = SLIDES[position];
        holder.animation.setAnimation(slide.animation);
        holder.animation.playAnimation();
        holder.title.setText(slide.title);
        holder.description.setText(slide.description);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.animation.cancelAnimation();
    }

    @Override
    public int getItemCount() {
        return SLIDES.length;
    }

    // public so the public adapter signature can expose it
    public static class ViewHolder extends RecyclerView.ViewHolder {
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