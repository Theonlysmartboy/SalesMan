package com.js.salesman.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;

import com.js.salesman.R;

import es.dmoral.toasty.Toasty;

public class NetworkUtil {
    private static AlertDialog currentDialog;

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
        // Prevent multiple dialogs
        if (currentDialog != null && currentDialog.isShowing()) {
            return;
        }
        Builder builder = new Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_no_internet, null);
        builder.setView(view);
        builder.setCancelable(false);
        currentDialog = builder.create();
        currentDialog.show();
        var btnRetry = view.findViewById(R.id.btnRetry);
        var btnEnable = view.findViewById(R.id.btnEnableInternet);
        var btnExit = view.findViewById(R.id.btnExit);
        if (allowExit) {
            btnExit.setVisibility(View.VISIBLE);
        }
        // Retry
        btnRetry.setOnClickListener(v -> {
            if (isNetworkAvailable(context)) {
                dismissDialog();
                Toasty.success(context, "Connected!", Toasty.LENGTH_SHORT).show();
            } else {
                Toasty.error(context, "Still no internet", Toasty.LENGTH_SHORT).show();
            }
        });
        // Open settings
        btnEnable.setOnClickListener(v -> {
            try {
                context.startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            } catch (Exception e) {
                context.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });
        btnExit.setOnClickListener(v -> {
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).finish();
            }
        });
        // AUTO DISMISS WHEN NETWORK RETURNS
        registerNetworkCallback(context);
    }
    //Listen for network changes dynamically
    private static void registerNetworkCallback(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return;
        ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                if (isNetworkAvailable(context)) {
                    if (context instanceof Activity) {
                        ((Activity) context).runOnUiThread(() -> {
                            dismissDialog();
                            Toasty.success(context, "Internet connection restored",
                                    Toasty.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        };
        manager.registerDefaultNetworkCallback(networkCallback);
    }
    private static void dismissDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }
    }
}
