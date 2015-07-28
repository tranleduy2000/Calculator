package com.android2.calculator3.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * Created by Will on 7/28/2015.
 */
public class SolidTray extends CalculatorPadLayout {
    public SolidTray(Context context) {
        super(context);
    }

    public SolidTray(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SolidTray(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        return true;
    }
}
