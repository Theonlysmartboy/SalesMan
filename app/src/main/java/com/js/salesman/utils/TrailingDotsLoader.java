package com.js.salesman.utils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.js.salesman.R;

public class TrailingDotsLoader extends View {
    private Paint paint;
    private float[] dotScales;
    private int dotCount = 8;
    private float radius;
    private int dotRadius = 10;
    private int primaryColor = Color.parseColor("#336699");
    private int secondaryColor = Color.parseColor("#003366");
    private int animationDuration = 1000;
    private float customCircleRadius = -1;
    private ValueAnimator animator;
    public TrailingDotsLoader(Context context) {
        super(context);
        init(context, null);
    }

    public TrailingDotsLoader(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public TrailingDotsLoader(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TrailingDotsLoader);
            dotCount = a.getInt(R.styleable.TrailingDotsLoader_dotCount, 8);
            dotRadius = a.getDimensionPixelSize(R.styleable.TrailingDotsLoader_dotRadius, 10);
            customCircleRadius = a.getDimension(R.styleable.TrailingDotsLoader_circleRadius, -1);
            primaryColor = a.getColor(R.styleable.TrailingDotsLoader_primaryColor,
                    Color.parseColor("#336699")
            );
            secondaryColor = a.getColor(R.styleable.TrailingDotsLoader_secondaryColor,
                    Color.parseColor("#003366"));
            animationDuration = a.getInt(R.styleable.TrailingDotsLoader_animationDuration,
                    1000);
            a.recycle();
        }
        dotScales = new float[dotCount];
        for (int i = 0; i < dotCount; i++) {
            dotScales[i] = 0.2f + (0.8f * i / dotCount);
        }
    }
    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (customCircleRadius > 0) {
            radius = customCircleRadius;
        } else {
            radius = Math.min(w, h) / 2f - dotRadius;
        }
        startAnimation();
    }
    private void startAnimation() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(animationDuration);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            // Update scales for trailing effect
            for (int i = 0; i < dotCount; i++) {
                float dotProgress = (progress + (float) i / dotCount) % 1f;
                dotScales[i] = 0.2f + 0.8f * (float) Math.sin(dotProgress * Math.PI);
            }
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        for (int i = 0; i < dotCount; i++) {
            float angle = (float) (2 * Math.PI * i / dotCount);
            float x = centerX + (float) (radius * Math.cos(angle));
            float y = centerY + (float) (radius * Math.sin(angle));
            // Interpolate color based on scale
            int color = interpolateColor(secondaryColor, primaryColor, dotScales[i]);
            paint.setColor(color);
            float currentRadius = dotRadius * dotScales[i];
            canvas.drawCircle(x, y, currentRadius, paint);
        }
    }
    private int interpolateColor(int color1, int color2, float factor) {
        float inverseFactor = 1 - factor;
        float a = (Color.alpha(color1) * inverseFactor) + (Color.alpha(color2) * factor);
        float r = (Color.red(color1) * inverseFactor) + (Color.red(color2) * factor);
        float g = (Color.green(color1) * inverseFactor) + (Color.green(color2) * factor);
        float b = (Color.blue(color1) * inverseFactor) + (Color.blue(color2) * factor);
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }

    // Customization methods
    // Update number of dots dynamically
    public void setDotCount(int count) {
        this.dotCount = count;
        if (dotScales == null || dotScales.length != dotCount) {
            dotScales = new float[dotCount];
        }
        // Initialize scales without touching colors or radius
        for (int i = 0; i < dotCount; i++) {
            dotScales[i] = 0.2f + (0.8f * i / dotCount);
        }
        invalidate();
    }

    // Update dot radius dynamically
    public void setDotRadius(int radius) {
        this.dotRadius = radius;
        invalidate();
    }

    // Update primary color dynamically
    public void setPrimaryColor(int color) {
        this.primaryColor = color;
        invalidate();
    }

    // Update secondary color dynamically
    public void setSecondaryColor(int color) {
        this.secondaryColor = color;
        invalidate();
    }

    // Update circle radius dynamically
    public void setCircleRadius(float radius) {
        this.customCircleRadius = radius;
        requestLayout(); // force onSizeChanged to recalc
        invalidate();
    }

    // Update animation duration dynamically
    public void setAnimationDuration(int duration) {
        this.animationDuration = duration;
        startAnimation();
    }
}
