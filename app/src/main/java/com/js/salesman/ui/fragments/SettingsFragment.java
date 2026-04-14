package com.js.salesman.ui.fragments;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.js.salesman.R;
import com.js.salesman.SalesManApp;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.session.SessionManager;
import com.js.salesman.ui.activities.auth.LockActivity;
import com.js.salesman.utils.LogManager;
import com.js.salesman.utils.SettingsManager;

import java.io.File;
import java.util.Objects;

import es.dmoral.toasty.Toasty;

public class SettingsFragment extends Fragment {

    private SettingsManager settingsManager;
    private TextView tvAutoLockValue, tvStorageUsage, tvApiUrl, tvAppVersion, tvDarkModeValue, tvServerStatus;
    private SwitchMaterial switchAuthOrder;

    public SettingsFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        settingsManager = new SettingsManager(requireContext());
        initViews(view);
        loadSettings();
        return view;
    }

    private void initViews(View view) {
        tvAutoLockValue = view.findViewById(R.id.tvAutoLockValue);
        switchAuthOrder = view.findViewById(R.id.switchAuthOrder);
        tvStorageUsage = view.findViewById(R.id.tvStorageUsage);
        tvApiUrl = view.findViewById(R.id.tvApiUrl);
        tvAppVersion = view.findViewById(R.id.tvAppVersion);
        tvDarkModeValue = view.findViewById(R.id.tvDarkModeValue);
        tvServerStatus = view.findViewById(R.id.tvServerStatus);

        view.findViewById(R.id.layoutAutoLock).setOnClickListener(v -> showAutoLockDialog());
        switchAuthOrder.setOnCheckedChangeListener((buttonView, isChecked) -> settingsManager.setRequireAuthForOrder(isChecked));

        view.findViewById(R.id.btnClearCache).setOnClickListener(v -> authenticateAction(this::clearCache));
        view.findViewById(R.id.layoutApiEndpoint).setOnClickListener(v -> showApiEndpointDialog());
        tvServerStatus.setOnClickListener(v -> checkServerStatus());

        view.findViewById(R.id.btnViewLogs).setOnClickListener(v -> showLastApiDialog());
        view.findViewById(R.id.btnExportLogs).setOnClickListener(v -> exportLogs());
        view.findViewById(R.id.btnCopyDeviceInfo).setOnClickListener(v -> copyDeviceInfo());
        view.findViewById(R.id.layoutDarkMode).setOnClickListener(v -> showDarkModeDialog());
    }

    private void loadSettings() {
        updateAutoLockText(settingsManager.getAutoLockTime());
        switchAuthOrder.setChecked(settingsManager.isAuthRequiredForOrder());
        tvApiUrl.setText(settingsManager.getApiBaseUrl(ApiClient.getBaseUrl()));
        tvStorageUsage.setText(getStorageUsage());
        tvAppVersion.setText(getAppVersionInfo());
        updateDarkModeText(settingsManager.getDarkMode());
    }

    private void showAutoLockDialog() {
        String[] options = {getString(R.string.off), "1 Min", "3 Min", "5 Min", "10 Min", "30 Min"};
        int[] values = {0, 1, 3, 5, 10, 30};
        
        int current = settingsManager.getAutoLockTime();
        int selectedIndex = 2; // Default 3
        for(int i=0; i<values.length; i++) if(values[i] == current) selectedIndex = i;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.auto_lock_timer)
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    settingsManager.setAutoLockTime(values[which]);
                    updateAutoLockText(values[which]);
                    dialog.dismiss();
                    Toasty.success(requireContext(), "Setting updated").show();
                })
                .show();
    }

    private void updateAutoLockText(int mins) {
        if (mins == 0) tvAutoLockValue.setText(R.string.off);
        else tvAutoLockValue.setText(getString(R.string.auto_lock_minutes, mins));
    }

    private void authenticateAction(Runnable onAuthenticated) {
        BiometricPrompt biometricPrompt = new BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(requireContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        onAuthenticated.run();
                    }

                    @Override
                    public void onAuthenticationError(
                            int errorCode,
                            @NonNull CharSequence errString) {
                        // Handle fallback cases
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                                errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
                            pendingAction = onAuthenticated;
                            Intent intent = new Intent(requireContext(), LockActivity.class);
                            intent.putExtra("is_auth_for_action", true);
                            authLauncher.launch(intent);
                        } else {
                            Toasty.error(requireContext(),
                                    "Auth Error: " + errString).show();
                        }
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.authentication_required))
                        .setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build();
        biometricPrompt.authenticate(promptInfo);
    }

    private Runnable pendingAction;

    private final ActivityResultLauncher<Intent> authLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            if (pendingAction != null) {
                                pendingAction.run();
                                pendingAction = null;
                            }
                        } else {
                            pendingAction = null;
                        }
                    });

    private void clearCache() {
        try {
            File cacheDir = requireContext().getCacheDir();
            deleteDir(cacheDir);
            LogManager.clearLogs(requireContext());
            LogManager.log(requireContext(), "CACHE_CLEAR", "User cleared app cache and logs");
            tvStorageUsage.setText(getStorageUsage());
            Toasty.success(requireContext(), "Cache and logs cleared successfully").show();
        } catch (Exception e) {
            Toasty.error(requireContext(), "Failed to clear cache").show();
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    private String getStorageUsage() {
        long size = getFolderSize(requireContext().getCacheDir()) + getFolderSize(requireContext().getFilesDir());
        return android.text.format.Formatter.formatShortFileSize(requireContext(), size);
    }

    private long getFolderSize(File file) {
        long size = 0;
        if (file.isDirectory()) {
            for (File child : Objects.requireNonNull(file.listFiles())) {
                size += getFolderSize(child);
            }
        } else {
            size = file.length();
        }
        return size;
    }

    private String getAppVersionInfo() {
        try {
            PackageInfo pInfo = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0);
            return pInfo.versionName + " (" + pInfo.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }

    private void showApiEndpointDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        EditText input = view.findViewById(R.id.editText);
        input.setText(settingsManager.getApiBaseUrl(ApiClient.getBaseUrl()));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.api_endpoint)
                .setView(view)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        settingsManager.setApiBaseUrl(url);
                        tvApiUrl.setText(url);
                        Toasty.warning(requireContext(), "Restart app to apply new API settings").show();
                    }
                })
                .setNegativeButton(R.string.back, null)
                .show();
    }

    private void checkServerStatus() {
        tvServerStatus.setText(R.string.checking);
        tvServerStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        
        ApiClient.getApi(requireContext()).syncProducts("sync", "2000-01-01", 1, 0)
                .enqueue(new retrofit2.Callback<>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<com.js.salesman.models.ProductListResponse> call, @NonNull retrofit2.Response<com.js.salesman.models.ProductListResponse> response) {
                        if (isAdded()) {
                            tvServerStatus.setText(R.string.online);
                            tvServerStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<com.js.salesman.models.ProductListResponse> call, @NonNull Throwable t) {
                        if (isAdded()) {
                            tvServerStatus.setText(R.string.offline);
                            tvServerStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                        }
                    }
                });
    }

    private void showLastApiDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_logs, null);
        TextView tvReq = view.findViewById(R.id.tvLastRequest);
        TextView tvRes = view.findViewById(R.id.tvLastResponse);
        tvReq.setText(LogManager.getLastRequest());
        tvRes.setText(LogManager.getLastResponse());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.view_last_api)
                .setView(view)
                .setPositiveButton("OK", null)
                .show();
    }

    private void exportLogs() {
        File logFile = LogManager.getLogFile(requireContext());
        if (!logFile.exists()) {
            Toasty.info(requireContext(), "No logs available").show();
            return;
        }

        Uri contentUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", logFile);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, contentUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Export Logs"));
    }

    private void copyDeviceInfo() {
        String info = "Device: " + Build.MODEL + "\n" +
                     "Android: " + Build.VERSION.RELEASE + "\n" +
                     "App Version: " + getAppVersionInfo() + "\n" +
                     "User ID: " + new SessionManager(requireContext()).getUserId();
        
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Device Info", info);
        clipboard.setPrimaryClip(clip);
        Toasty.success(requireContext(), "Device info copied to clipboard").show();
    }

    private void showDarkModeDialog() {
        String[] modes = {getString(R.string.system_default), getString(R.string.light), getString(R.string.dark)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dark_mode)
                .setSingleChoiceItems(modes, settingsManager.getDarkMode(), (dialog, which) -> {
                    settingsManager.setDarkMode(which);
                    updateDarkModeText(which);
                    ((SalesManApp) requireActivity().getApplication()).applyDarkMode();
                    dialog.dismiss();
                })
                .show();
    }

    private void updateDarkModeText(int mode) {
        String[] modes = {getString(R.string.system_default), getString(R.string.light), getString(R.string.dark)};
        tvDarkModeValue.setText(modes[mode]);
    }
}
