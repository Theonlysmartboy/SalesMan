package com.js.salesman.utils;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;

import com.js.salesman.R;

import es.dmoral.toasty.Toasty;

public class NetworkUtil {
    private static android.app.AlertDialog currentDialog;
    private static ConnectivityManager.NetworkCallback networkCallback;
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    public static void showNoInternetDialog(final Context context, boolean allowExit) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        android.view.View view = android.view.LayoutInflater.from(context)
                .inflate(R.layout.dialog_no_internet, null);
        builder.setView(view);
        builder.setCancelable(false);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        // 🔹 Buttons
        com.google.android.material.button.MaterialButton btnRetry = view.findViewById(R.id.btnRetry);
        com.google.android.material.button.MaterialButton btnEnable = view.findViewById(R.id.btnEnableInternet);
        com.google.android.material.button.MaterialButton btnExit = view.findViewById(R.id.btnExit);
        // Show exit only when allowed
        if (allowExit) {
            btnExit.setVisibility(android.view.View.VISIBLE);
        }
        // ✅ Retry
        btnRetry.setOnClickListener(v -> {
            if (isNetworkAvailable(context)) {
                Toasty.success(context, "Connected!", Toasty.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toasty.error(context, "Still no internet", Toasty.LENGTH_SHORT).show();
            }
        });

        // ✅ Enable Internet (Wi-Fi + Mobile Data entry point)
        btnEnable.setOnClickListener(v -> {
            try {
                context.startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            } catch (Exception e) {
                context.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });

        // ✅ Exit
        btnExit.setOnClickListener(v -> {
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).finish();
            }
        });
    }
    //Listen for network changes dynamically
    public static void registerNetworkCallback(Context context, ConnectivityManager.NetworkCallback callback) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            manager.registerDefaultNetworkCallback(callback);
        }
    }
}
