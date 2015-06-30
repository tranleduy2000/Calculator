/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android2.calculator3.view;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android2.calculator3.NumberBaseManager;
import com.android2.calculator3.R;
import com.xlythe.floatingview.AnimationFinishedListener;

import io.codetail.animation.SupportAnimator;
import io.codetail.animation.ViewAnimationUtils;

public class CalculatorPadViewExtended extends CalculatorPadView implements View.OnClickListener {
    private View mAdvancedPad;
    private View mTrigPad;
    private View mHexPad;
    private View mMatrixPad;

    public CalculatorPadViewExtended(Context context) {
        super(context);
    }

    public CalculatorPadViewExtended(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CalculatorPadViewExtended(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CalculatorPadViewExtended(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        findViewById(R.id.btn_advanced).setOnClickListener(this);
        findViewById(R.id.btn_trig).setOnClickListener(this);
        findViewById(R.id.btn_hex).setOnClickListener(this);
        findViewById(R.id.btn_matrix).setOnClickListener(this);
        mAdvancedPad = findViewById(R.id.pad_advanced);
        mTrigPad = findViewById(R.id.pad_trig);
        mHexPad = findViewById(R.id.pad_hex);
        mMatrixPad = findViewById(R.id.pad_matrix);
        show(mAdvancedPad);
    }

    @Override
    public void onClick(View v) {
        View layout = null;
        switch (v.getId()) {
            case R.id.btn_advanced:
                layout = mAdvancedPad;
                break;
            case R.id.btn_trig:
                layout = mTrigPad;
                break;
            case R.id.btn_hex:
                layout = mHexPad;
                break;
            case R.id.btn_matrix:
                layout = mMatrixPad;
                break;
        }
        show(layout);
        showFab();
        hideTray();
    }

    private void show(View layout) {
        FrameLayout baseOverlay = getBaseOverlay();
        for (int i = 0; i < baseOverlay.getChildCount(); i++) {
            View child = baseOverlay.getChildAt(i);
            if (child != layout) {
                child.setVisibility(View.GONE);
            } else {
                child.setVisibility(View.VISIBLE);
            }
        }
    }
}