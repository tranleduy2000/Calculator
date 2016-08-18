package com.android2.calculator3.view;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class CalculatorRecyclerView extends RecyclerView {

    public CalculatorRecyclerView(Context context) {
        this(context, null);
    }

    public CalculatorRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return isEnabled() && super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return !isEnabled() || super.onInterceptTouchEvent(event);
    }
}
