package com.js.salesman.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.js.salesman.R;
import com.js.salesman.ui.activities.auth.AuthGateActivity;
import com.js.salesman.utils.managers.GPSManager;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.utils.Db;

import java.util.HashMap;
import java.util.Objects;

public class ProfileFragment extends Fragment {

    private TextView tvHeaderFullName, tvHeaderUsername;
    private TextView tvFullName, tvUsername, tvRole, tvPinStatus, tvToken;
    private SessionManager session;
    private Db db;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        session = new SessionManager(requireContext());
        db = new Db(requireContext());
        initViews(view);
        loadUserProfile();
    }

    private void initViews(View view) {
        tvHeaderFullName = view.findViewById(R.id.tvHeaderFullName);
        tvHeaderUsername = view.findViewById(R.id.tvHeaderUsername);
        tvFullName = view.findViewById(R.id.tvFullName);
        tvUsername = view.findViewById(R.id.tvUsername);
        tvRole = view.findViewById(R.id.tvRole);
        tvPinStatus = view.findViewById(R.id.tvPinStatus);
        tvToken = view.findViewById(R.id.tvToken);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Log out")
                .setMessage("Are you sure you want to Log out?")
                .setPositiveButton("Yes", (dialog, which) -> logoutUser())
                .setNegativeButton("No", null)
                .show());
    }

    private void loadUserProfile() {
        // 1. Load from Session
        String userId = session.getUserId();
        String fullName = session.getFullName();
        String userName = session.getUsername();
        String role = session.getRole();
        String token = session.getToken();
        // 2. Load from DB
        // Use DB values as fallback or for fields not in session
        HashMap<String, String> userDb = db.getUserDetails(userId);
        if (fullName == null && userDb.containsKey("fullName")) fullName = userDb.get("fullName");
        if (userName == null && userDb.containsKey("userName")) userName = userDb.get("userName");
        if (role == null && userDb.containsKey("role")) role = userDb.get("role");
        if (token == null && userDb.containsKey("token")) token = userDb.get("token");
        int hasPin = 0;
        if (userDb.containsKey("has_pin")) {
            try {
                hasPin = Integer.parseInt(Objects.requireNonNull(userDb.get("has_pin")));
            } catch (NumberFormatException ignored) {}
        }
        // Bind data to UI
        tvHeaderFullName.setText(fullName != null ? fullName : "N/A");
        tvHeaderUsername.setText(userName != null ? "@" + userName : "N/A");
        tvFullName.setText(fullName != null ? fullName : "N/A");
        tvUsername.setText(userName != null ? userName : "N/A");
        tvRole.setText(role != null ? role : "N/A");
        tvPinStatus.setText(getPinStatus(hasPin));
        tvToken.setText(maskToken(token));
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "********";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 3);
    }

    private String getPinStatus(int hasPin) {
        return hasPin == 1 ? "Set" : "Not Set";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (db != null) db.close();
    }

    protected void logoutUser() {
        GPSManager.stopTracking(requireActivity());
        WorkManager.getInstance(requireActivity()).cancelAllWorkByTag("gps_restart");
        session.clearSession();
        // Redirect to AuthGate for "fast re-entry" (PIN/Biometric) as per requirements
        Intent intent = new Intent(requireActivity(), AuthGateActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
