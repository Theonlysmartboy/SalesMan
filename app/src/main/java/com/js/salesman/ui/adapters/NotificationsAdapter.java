package com.js.salesman.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;

import java.util.HashMap;
import java.util.List;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {

    private final List<HashMap<String, String>> notifications;
    private final OnNotificationListener listener;

    public interface OnNotificationListener {
        void onNotificationClick(String id);
        void onNotificationLongClick(String id);
    }

    public NotificationsAdapter(List<HashMap<String, String>> notifications, OnNotificationListener listener) {
        this.notifications = notifications;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HashMap<String, String> item = notifications.get(position);
        holder.tvTitle.setText(item.get("title"));
        holder.tvMessage.setText(item.get("message"));
        holder.tvTime.setText(item.get("created_at"));

        boolean isRead = "1".equals(item.get("is_read"));
        holder.unreadIndicator.setVisibility(isRead ? View.GONE : View.VISIBLE);
        holder.tvTitle.setAlpha(isRead ? 0.6f : 1.0f);
        holder.tvMessage.setAlpha(isRead ? 0.6f : 1.0f);

        holder.itemView.setOnClickListener(v -> listener.onNotificationClick(item.get("id")));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onNotificationLongClick(item.get("id"));
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMessage, tvTime;
        View unreadIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            unreadIndicator = itemView.findViewById(R.id.unreadIndicator);
        }
    }
}
