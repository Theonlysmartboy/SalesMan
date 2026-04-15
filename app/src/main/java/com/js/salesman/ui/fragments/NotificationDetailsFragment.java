package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;
import com.js.salesman.ui.activities.MainActivity;
import com.js.salesman.utils.Db;

import java.util.HashMap;

public class NotificationDetailsFragment extends Fragment {

    private static final String ARG_NOTIFICATION_ID = "notification_id";
    private String notificationId;
    private Db db;

    public static NotificationDetailsFragment newInstance(String notificationId) {
        NotificationDetailsFragment fragment = new NotificationDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NOTIFICATION_ID, notificationId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            notificationId = getArguments().getString(ARG_NOTIFICATION_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notification_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = new Db(requireContext());

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvTime = view.findViewById(R.id.tvTime);
        TextView tvMessage = view.findViewById(R.id.tvMessage);
        MaterialButton btnDelete = view.findViewById(R.id.btnDelete);

        HashMap<String, String> details = db.getNotificationDetails(notificationId);
        if (!details.isEmpty()) {
            tvTitle.setText(details.get("title"));
            tvTime.setText(details.get("created_at"));
            tvMessage.setText(details.get("message"));
        }

        btnDelete.setOnClickListener(v -> {
            db.deleteNotification(notificationId);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateNotificationBadge();
            }
            requireActivity().getSupportFragmentManager().popBackStack();
        });
    }
}
