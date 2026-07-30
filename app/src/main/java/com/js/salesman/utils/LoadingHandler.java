package com.js.salesman.utils;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.js.salesman.R;
import com.js.salesman.utils.managers.LogManager;

public class LoadingHandler {

    /**
     * Shows the loading overlay.
     */
    public static void showLoading(Context context, TrailingDotsLoader loader, FrameLayout overlay) {
        if (loader == null || overlay == null) {
            LogManager.log(context, "LoadingHandler", "showLoading: loader or overlay is null");
            return;
        }
        if (loader.getParent() != null) {
            LogManager.log(context, "LoadingHandler", "showLoading: loader already has a parent");
            return;
        }

        LogManager.log(context, "LoadingHandler", "showLoading: Displaying loader");
        // Apply configuration from AppConstants
        loader.setPrimaryColor(Color.parseColor(AppConstants.loaderPrimaryColor));
        loader.setSecondaryColor(Color.parseColor(AppConstants.loaderSecondaryColor));
        loader.setDotCount(AppConstants.loaderDotsCount);
        loader.setDotRadius(AppConstants.loaderDotsRadius);
        loader.setAnimationDuration(AppConstants.loaderAnimationDuration);

        int size = context.getResources().getDimensionPixelSize(R.dimen._80dp);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.CENTER;
        loader.setLayoutParams(params);

        overlay.removeAllViews();
        overlay.addView(loader);
        overlay.setVisibility(View.VISIBLE);
    }

    /**
     * Hides the loading overlay.
     */
    public static void hideLoading(FrameLayout overlay) {
        if (overlay == null) return;
        overlay.setVisibility(View.GONE);
        overlay.removeAllViews();
    }
}
