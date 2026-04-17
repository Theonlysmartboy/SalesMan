package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.js.salesman.R;
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
    }

    private void loadUserProfile() {
        // 1. Load from Session (Primary)
        String userId = session.getUserId();
        String fullName = session.getFullName();
        String userName = session.getUsername();
        String role = session.getRole();
        String token = session.getToken();

        // 2. Load from DB (Secondary/Extra info)
        HashMap<String, String> userDb = db.getUserDetails(userId);
        
        // Use DB values as fallback or for fields not in session
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
}
