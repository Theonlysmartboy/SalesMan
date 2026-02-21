package com.js.salesman.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.js.salesman.R;
import com.js.salesman.adapters.OnboardingAdapter;
import com.js.salesman.utils.PrefsManager;
import com.tbuonomo.viewpagerdotsindicator.DotsIndicator;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Button btnNext;
    private TextView btnSkip;
    private PrefsManager prefManager;
    private OnboardingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        prefManager = new PrefsManager(this);

        viewPager = findViewById(R.id.onboardingViewPager);
        btnNext = findViewById(R.id.btnNext);
        btnSkip = findViewById(R.id.btnSkip);
        DotsIndicator dotsIndicator = findViewById(R.id.dotsIndicator);

        adapter = new OnboardingAdapter();
        viewPager.setAdapter(adapter);
        dotsIndicator.setViewPager2(viewPager);

        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                finishOnboarding();
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());

        // Change button text on last slide
        viewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);

                        if (position == adapter.getItemCount() - 1) {
                            btnNext.setText("Finish");
                        } else {
                            btnNext.setText("Next");
                        }
                    }
                }
        );
    }

    private void finishOnboarding() {
        prefManager.setFirstLaunch(false);

        startActivity(new Intent(OnboardingActivity.this, MainActivity.class));
        finish();
    }
}