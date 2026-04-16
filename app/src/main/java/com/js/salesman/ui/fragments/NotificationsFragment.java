package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.js.salesman.R;
import com.js.salesman.ui.adapters.NotificationsAdapter;
import com.js.salesman.utils.Db;

import java.util.HashMap;
import java.util.List;

public class NotificationsFragment extends Fragment implements NotificationsAdapter.OnNotificationListener {

    private RecyclerView rvNotifications;
    private LinearLayout emptyState;
    private NotificationsAdapter adapter;
    private List<HashMap<String, String>> notifications;
    private Db db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = new Db(requireContext());
        rvNotifications = view.findViewById(R.id.rvNotifications);
        emptyState = view.findViewById(R.id.emptyState);

        rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        setupSwipeActions();
        loadNotifications();
    }

    private void loadNotifications() {
        notifications = db.getNotifications();
        if (notifications.isEmpty()) {
            rvNotifications.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            rvNotifications.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            adapter = new NotificationsAdapter(notifications, this);
            rvNotifications.setAdapter(adapter);
        }
        updateBadgeInActivity();
    }

    private void setupSwipeActions() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                String id = notifications.get(position).get("id");

                if (direction == ItemTouchHelper.LEFT) {
                    // Delete
                    db.deleteNotification(id);
                } else {
                    // Archive
                    db.archiveNotification(id);
                }
                
                notifications.remove(position);
                adapter.notifyItemRemoved(position);
                
                if (notifications.isEmpty()) {
                    rvNotifications.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                }
                updateBadgeInActivity();
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(rvNotifications);
    }

    @Override
    public void onNotificationClick(String id) {
        db.markNotificationAsRead(id);
        updateBadgeInActivity();
        
        NotificationDetailsFragment fragment = NotificationDetailsFragment.newInstance(id);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onNotificationLongClick(String id) {
        String[] options = {"Mark as read", "Archive", "Delete"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Notification Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) db.markNotificationAsRead(id);
                    else if (which == 1) db.archiveNotification(id);
                    else if (which == 2) db.deleteNotification(id);
                    loadNotifications();
                    updateBadgeInActivity();
                })
                .show();
    }

    private void updateBadgeInActivity() {
        if (getActivity() instanceof com.js.salesman.ui.activities.MainActivity) {
            getActivity().invalidateOptionsMenu();
        }
    }
}
