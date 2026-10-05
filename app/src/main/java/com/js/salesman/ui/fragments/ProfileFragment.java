package com.js.salesman.ui.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.js.salesman.R;
import com.js.salesman.repository.UserRepository;
import com.js.salesman.ui.activities.auth.AuthGateActivity;
import com.js.salesman.utils.managers.GPSManager;
import com.js.salesman.utils.managers.SessionManager;

import java.util.HashMap;
import java.util.Objects;

public class ProfileFragment extends Fragment {

    private TextView tvHeaderFullName, tvHeaderUsername;
    private TextView tvFullName, tvUsername, tvPinStatus, tvToken;
    private Chip chipRole;
    private MaterialButton btnCopyToken;
    private SessionManager session;
    private UserRepository userRepository;
    private String rawToken;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                                Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        session = new SessionManager(requireContext());
        userRepository = UserRepository.getInstance(requireContext());
        initViews(view);
        loadUserProfile();
    }

    private void initViews(View view) {
        tvHeaderFullName = view.findViewById(R.id.tvHeaderFullName);
        tvHeaderUsername = view.findViewById(R.id.tvHeaderUsername);
        tvFullName       = view.findViewById(R.id.tvFullName);
        tvUsername       = view.findViewById(R.id.tvUsername);
        tvPinStatus      = view.findViewById(R.id.tvPinStatus);
        tvToken          = view.findViewById(R.id.tvToken);
        chipRole         = view.findViewById(R.id.chipRole);
        btnCopyToken     = view.findViewById(R.id.btnCopyToken);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);
        btnCopyToken.setOnClickListener(v -> copyTokenToClipboard());
        btnLogout.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> logoutUser())
                .setNegativeButton(R.string.no, null)
                .show());
    }

    private void loadUserProfile() {
        String userId   = session.getUserId();
        String fullName = session.getFullName();
        String userName = session.getUsername();
        String role     = session.getRole();
        String token    = session.getToken();
        HashMap<String, String> userDb = userRepository.getUserDetailsSync(userId);
        if (fullName == null && userDb.containsKey("fullName")) fullName = userDb.get("fullName");
        if (userName == null && userDb.containsKey("userName")) userName = userDb.get("userName");
        if (role     == null && userDb.containsKey("role"))     role     = userDb.get("role");
        if (token    == null && userDb.containsKey("token"))    token    = userDb.get("token");
        int hasPin = 0;
        if (userDb.containsKey("has_pin")) {
            try {
                hasPin = Integer.parseInt(Objects.requireNonNull(userDb.get("has_pin")));
            } catch (NumberFormatException ignored) {}
        }
        // Keep the real token for copy; show the masked form on screen
        rawToken = token;
        tvHeaderFullName.setText(fullName != null ? fullName : "N/A");
        tvHeaderUsername.setText(userName != null ? "@" + userName : "N/A");
        tvFullName.setText(fullName   != null ? fullName : "N/A");
        tvUsername.setText(userName   != null ? userName : "N/A");
        chipRole.setText(role         != null ? role     : "N/A");
        tvPinStatus.setText(getPinStatus(hasPin));
        tvToken.setText(maskToken(token));
        // Disable copy if there's nothing real to copy
        btnCopyToken.setEnabled(token != null && !token.isEmpty());
    }

    private void copyTokenToClipboard() {
        if (rawToken == null || rawToken.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_token_to_copy, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("session_token", rawToken));
            Toast.makeText(requireContext(), R.string.token_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "********";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 3);
    }

    private String getPinStatus(int hasPin) {
        return hasPin == 1 ? getString(R.string.set) : getString(R.string.not_set);
    }

    protected void logoutUser() {
        GPSManager.stopTracking(requireActivity());
        WorkManager.getInstance(requireActivity()).cancelAllWorkByTag("gps_restart");
        session.clearSession();
        Intent intent = new Intent(requireActivity(), AuthGateActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}