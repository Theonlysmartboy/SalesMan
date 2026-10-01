package com.js.salesman.ui.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.js.salesman.R;
import com.js.salesman.models.CustomerVisit;

import java.util.Locale;

public class ActiveVisitBannerView extends FrameLayout {

    private TextView tvBusinessName;
    private TextView tvVisitId;
    private TextView tvElapsedTime;
    private TextView tvSyncStatus;
    private CustomerVisit currentVisit;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            timerHandler.postDelayed(this, 1000);
        }
    };

    public ActiveVisitBannerView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ActiveVisitBannerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ActiveVisitBannerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_active_visit_banner, this, true);
        tvBusinessName = view.findViewById(R.id.tvBannerBusinessName);
        tvVisitId = view.findViewById(R.id.tvBannerVisitId);
        tvElapsedTime = view.findViewById(R.id.tvBannerElapsedTime);
        tvSyncStatus = view.findViewById(R.id.tvBannerSyncStatus);
        setVisibility(GONE);
    }

    public void bindVisit(CustomerVisit visit) {
        this.currentVisit = visit;
        if (visit == null || !"IN_PROGRESS".equals(visit.visitStatus)) {
            setVisibility(GONE);
            stopTimer();
            return;
        }
        setVisibility(VISIBLE);
        tvBusinessName.setText(visit.businessName != null ? visit.businessName : "Active Customer Visit");
        tvVisitId.setText(String.format("Visit #%s", visit.visitId));
        tvSyncStatus.setText(visit.syncStatus != null ? visit.syncStatus : "PENDING");
        startTimer();
    }

    private void startTimer() {
        stopTimer();
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateTimer() {
        if (currentVisit == null || currentVisit.startedAt <= 0) {
            tvElapsedTime.setText(R.string._00_00_00);
            return;
        }
        long elapsedMs = Math.max(0, System.currentTimeMillis() - currentVisit.startedAt);
        long seconds = (elapsedMs / 1000) % 60;
        long minutes = (elapsedMs / (1000 * 60)) % 60;
        long hours = elapsedMs / (1000 * 60 * 60);
        tvElapsedTime.setText(String.format(Locale.US, "%02d:%02d:%02d",
                hours, minutes, seconds));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopTimer();
    }
}
