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
package com.android2.calculator3;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android2.calculator3.CalculatorExpressionEvaluator.EvaluateCallback;
import com.android2.calculator3.view.CalculatorEditText;
import com.android2.calculator3.view.DisplayOverlay;
import com.android2.calculator3.view.GraphView;
import com.android2.calculator3.view.ResizingEditText.OnTextSizeChangeListener;
import com.xlythe.floatingview.AnimationFinishedListener;
import com.xlythe.math.Base;
import com.xlythe.math.GraphModule;

import java.util.Locale;

/**
 * Adds graphing and base switching to the basic calculator.
 * */
public class GraphingCalculator extends BasicCalculator
        implements OnTextSizeChangeListener, EvaluateCallback, OnLongClickListener {

    // instance state keys
    private static final String KEY_BASE = NAME + "_base";

    private DisplayOverlay mDisplayView;
    private CalculatorEditText mFormulaEditText;
    private CalculatorEditText mResultEditText;

    private TextView mInfoView;
    private boolean mShowBaseDetails;
    private boolean mShowTrigDetails;
    private NumberBaseManager mBaseManager;

    private String mX;
    private GraphController mGraphController;
    private GraphView mMiniGraph;
    private AsyncTask mGraphTask;

    @Override
    protected void initialize(Bundle savedInstanceState) {
        super.initialize(savedInstanceState);
        mX = getString(R.string.var_x);
        mInfoView = (TextView) findViewById(R.id.info);
        mDisplayView = (DisplayOverlay) findViewById(R.id.display);
        mFormulaEditText = (CalculatorEditText) findViewById(R.id.formula);
        mResultEditText = (CalculatorEditText) findViewById(R.id.result);
        mMiniGraph = (GraphView) findViewById(R.id.mini_graph);

        Base base = Base.DECIMAL;
        int baseOrdinal = savedInstanceState.getInt(KEY_BASE, -1);
        if (baseOrdinal != -1) {
            base = Base.values()[baseOrdinal];
        }
        mBaseManager = new NumberBaseManager(base);
        invalidateSelectedBase(base);

        mGraphController = new GraphController(new GraphModule(getEvaluator().getSolver()), mMiniGraph);

        mShowBaseDetails = !mBaseManager.getNumberBase().equals(Base.DECIMAL);
        mShowTrigDetails = false;

        updateDetails();
    }

    private void transitionToGraph() {
        if (mResultEditText.getVisibility() == View.GONE) {
            return;
        }

        mGraphController.lock();

        setState(CalculatorState.GRAPHING);
        mDisplayView.transitionToGraph(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                mGraphController.unlock();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_BASE, mBaseManager.getNumberBase().ordinal());
    }


    private void transitionToDisplay() {
        if (mResultEditText.getVisibility() == View.VISIBLE) {
            return;
        }

        mDisplayView.transitionToDisplay(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                setState(CalculatorState.INPUT);
            }
        });
    }

    @Override
    public void onButtonClick(View view) {
        switch (view.getId()) {
            case R.id.fun_cos:
            case R.id.fun_acos:
            case R.id.fun_sin:
            case R.id.fun_asin:
            case R.id.fun_tan:
            case R.id.fun_atan:
            case R.id.fun_csc:
            case R.id.fun_sec:
            case R.id.fun_cot:
            case R.id.fun_acsc:
            case R.id.fun_asec:
            case R.id.fun_acot:
                mShowTrigDetails = true;
                updateDetails();
                break;
            case R.id.hex:
                setBase(Base.HEXADECIMAL);
                return;
            case R.id.bin:
                setBase(Base.BINARY);
                return;
            case R.id.dec:
                setBase(Base.DECIMAL);
                return;
        }
        super.onButtonClick(view);
    }

    @Override
    public void onEvaluate(String expr, String result, int errorResourceId) {
        if (getState() == CalculatorState.EVALUATE && expr.contains(mX)) {
            saveHistory(expr, result, false);
            incrementGroupId();
            mDisplayView.scrollToMostRecent();
            onResult(result);
        } else {
            super.onEvaluate(expr, result, errorResourceId);
        }

        if (expr.contains(mX)) {
            transitionToGraph();
            if (mGraphTask != null) {
                mGraphTask.cancel(true);
            }
            mGraphTask = mGraphController.startGraph(mFormulaEditText.getCleanText());
        } else {
            transitionToDisplay();
        }
    }

    private void setBase(Base base) {
        // Update the BaseManager, which handles restricting which buttons to show
        mBaseManager.setNumberBase(base);
        mShowBaseDetails = true;

        // Update the evaluator, which handles the math
        getEvaluator().setBase(mFormulaEditText.getCleanText(), base, new EvaluateCallback() {
            @Override
            public void onEvaluate(String expr, String result, int errorResourceId) {
                if (errorResourceId != INVALID_RES_ID) {
                    onError(errorResourceId);
                } else {
                    mResultEditText.setText(result);
                    onResult(result);
                }
            }
        });
        invalidateSelectedBase(base);
    }

    private void invalidateSelectedBase(Base base) {
        setSelectedBaseButton(base);

        // Disable any buttons that are not relevant to the current base
        for (int resId : mBaseManager.getViewIds()) {
            View view = findViewById(resId);
            if (view != null) {
                view.setEnabled(!mBaseManager.isViewDisabled(resId));
            }
        }

        updateDetails();
    }

    private void setSelectedBaseButton(Base base) {
        findViewById(R.id.hex).setSelected(base.equals(Base.HEXADECIMAL));
        findViewById(R.id.bin).setSelected(base.equals(Base.BINARY));
        findViewById(R.id.dec).setSelected(base.equals(Base.DECIMAL));
    }

    private void updateDetails() {
        if(mInfoView != null) {
            String text = "";
            String units = CalculatorSettings.useRadians(getBaseContext()) ?
                    getString(R.string.radians) : getString(R.string.degrees);
            String base = "";
            switch(mBaseManager.getNumberBase()) {
                case HEXADECIMAL:
                    base = getString(R.string.hex).toUpperCase(Locale.getDefault());
                    break;
                case BINARY:
                    base = getString(R.string.bin).toUpperCase(Locale.getDefault());
                    break;
                case DECIMAL:
                    base = getString(R.string.dec).toUpperCase(Locale.getDefault());
                    break;
            }
            if(mShowBaseDetails) text += base;
            if(mShowTrigDetails) {
                if(!text.isEmpty()) text += " | ";
                text += units;
            }

            mInfoView.setMovementMethod(LinkMovementMethod.getInstance());
            mInfoView.setText(text, TextView.BufferType.SPANNABLE);

            if(mShowBaseDetails) {
                setClickableSpan(mInfoView, base, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final int DEC = 0;
                        final int HEX = 1;
                        final int BIN = 2;
                        final PopupMenu popupMenu = new PopupMenu(getBaseContext(), mInfoView);
                        final Menu menu = popupMenu.getMenu();
                        menu.add(0, DEC, menu.size(), R.string.desc_dec);
                        menu.add(0, HEX, menu.size(), R.string.desc_hex);
                        menu.add(0, BIN, menu.size(), R.string.desc_bin);
                        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                switch (item.getItemId()) {
                                    case DEC:
                                        setBase(Base.DECIMAL);
                                        break;
                                    case HEX:
                                        setBase(Base.HEXADECIMAL);
                                        break;
                                    case BIN:
                                        setBase(Base.BINARY);
                                        break;
                                }
                                return true;
                            }
                        });
                        popupMenu.show();
                    }
                });
            }
            if(mShowTrigDetails) {
                setClickableSpan(mInfoView, units, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final int RAD = 0;
                        final int DEG = 1;
                        final PopupMenu popupMenu = new PopupMenu(getBaseContext(), mInfoView);
                        final Menu menu = popupMenu.getMenu();
                        menu.add(0, RAD, menu.size(), R.string.radians);
                        menu.add(0, DEG, menu.size(), R.string.degrees);
                        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                switch (item.getItemId()) {
                                    case RAD:
                                        CalculatorSettings.setRadiansEnabled(getBaseContext(), true);
                                        break;
                                    case DEG:
                                        CalculatorSettings.setRadiansEnabled(getBaseContext(), false);
                                        break;
                                }
                                updateDetails();
                                if (getState() != CalculatorState.GRAPHING) {
                                    setState(CalculatorState.INPUT);
                                }
                                getEvaluator().evaluate(mFormulaEditText.getCleanText(), GraphingCalculator.this);
                                return true;
                            }
                        });
                        popupMenu.show();
                    }
                });
            }
        }
    }

    private void setClickableSpan(TextView textView, final String word, final View.OnClickListener listener) {
        final Spannable spans = (Spannable) textView.getText();
        String text = spans.toString();
        ClickableSpan span = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Selection.setSelection(spans, 0);
                listener.onClick(null);
            }

            public void updateDrawState(TextPaint ds) {}
        };
        spans.setSpan(span, text.indexOf(word), text.indexOf(word) + word.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
