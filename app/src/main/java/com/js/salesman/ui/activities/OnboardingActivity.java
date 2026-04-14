package com.js.salesman.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;
import com.js.salesman.adapters.OnboardingAdapter;
import com.js.salesman.utils.PrefsManager;
import com.tbuonomo.viewpagerdotsindicator.DotsIndicator;

public class OnboardingActivity extends BaseActivity {

    private ViewPager2 viewPager;
    private MaterialButton btnNext, btnConfig;
    private PrefsManager prefManager;
    private OnboardingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        prefManager = new PrefsManager(this);
        viewPager = findViewById(R.id.onboardingViewPager);
        btnNext = findViewById(R.id.btnNext);
        btnConfig = findViewById(R.id.btn_configure);
        TextView btnSkip = findViewById(R.id.btnSkip);
        DotsIndicator dotsIndicator = findViewById(R.id.dotsIndicator);
        adapter = new OnboardingAdapter();
        viewPager.setAdapter(adapter);
        dotsIndicator.attachTo(viewPager);
        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                finishOnboarding();
            }
        });
        //configure button click listener
        btnConfig.setOnClickListener(view -> {
            //SAVE a boolean value to show that user has already seen the welcome screen
            prefManager.setFirstLaunch(false);
            Intent config = new Intent(getApplicationContext(), ConfigActivity.class);
            startActivity(config);
            finish();
        });
        btnSkip.setOnClickListener(v -> finishOnboarding());
        // Change button text on last slide
        viewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);
                        if (position == adapter.getItemCount() - 1) {
                            btnNext.setText(R.string.finish);
                            btnConfig.setVisibility(View.VISIBLE);
                        } else {
                            btnNext.setText(R.string.next);
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