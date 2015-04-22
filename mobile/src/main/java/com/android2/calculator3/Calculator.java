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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;

import io.codetail.animation.SupportAnimator;
import io.codetail.animation.ViewAnimationUtils;
import io.codetail.widget.RevealView;

import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android2.calculator3.view.CalculatorPadViewPager;
import com.android2.calculator3.view.EqualsImageButton;
import com.android2.calculator3.view.GraphView;
import com.android2.calculator3.view.display.AdvancedDisplay.OnTextSizeChangeListener;
import com.android2.calculator3.CalculatorExpressionEvaluator.EvaluateCallback;
import com.android2.calculator3.view.display.AdvancedDisplay;
import com.android2.calculator3.view.DisplayOverlay;
import com.android2.calculator3.view.MatrixEditText;
import com.android2.calculator3.view.MatrixInverseView;
import com.android2.calculator3.view.MatrixTransposeView;
import com.android2.calculator3.view.MatrixView;
import com.android2.calculator3.view.EqualsImageButton.State;
import com.xlythe.floatingview.AnimationFinishedListener;
import com.xlythe.math.Base;
import com.xlythe.math.Constants;
import com.xlythe.math.GraphModule;
import com.xlythe.math.History;
import com.xlythe.math.HistoryEntry;
import com.xlythe.math.Persist;
import com.xlythe.math.Solver;

import java.util.Locale;

public class Calculator extends Activity
        implements OnTextSizeChangeListener, EvaluateCallback, OnLongClickListener {

    private static final String NAME = Calculator.class.getName();
    private static final String TAG = Calculator.class.getSimpleName();

    // instance state keys
    private static final String KEY_CURRENT_STATE = NAME + "_currentState";
    private static final String KEY_CURRENT_EXPRESSION = NAME + "_currentExpression";
    private static final String KEY_BASE = NAME + "_base";

    /**
     * Constant for an invalid resource id.
     */
    public static final int INVALID_RES_ID = -1;

    private enum CalculatorState {
        INPUT, EVALUATE, RESULT, ERROR, GRAPHING
    }

    private final TextWatcher mFormulaTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable editable) {
            if (mCurrentState != CalculatorState.GRAPHING) {
                setState(CalculatorState.INPUT);
            }
            mEvaluator.evaluate(editable, Calculator.this);
        }
    };

    private final OnKeyListener mFormulaOnKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        mCurrentButton = mEqualButton;
                        onEquals();
                    }
                    // ignore all other actions
                    return true;
            }
            return false;
        }
    };

    private CalculatorState mCurrentState;
    private CalculatorExpressionTokenizer mTokenizer;
    private CalculatorExpressionEvaluator mEvaluator;
    private DisplayOverlay mDisplayView;
    private ViewGroup mMainDisplay;
    private ViewGroup mCalculationsDisplay;
    private TextView mInfoView;
    private AdvancedDisplay mFormulaEditText;
    private AdvancedDisplay mResultEditText;
    private CalculatorPadViewPager mPadViewPager;
    private View mDeleteButton;
    private EqualsImageButton mEqualButton;
    private View mClearButton;
    private View mCurrentButton;
    private Animator mCurrentAnimator;
    private History mHistory;
    private RecyclerView.Adapter mHistoryAdapter;
    private Persist mPersist;
    private NumberBaseManager mBaseManager;
    private String mX;
    private GraphController mGraphController;
    private final ViewGroup.LayoutParams mLayoutParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
    private boolean mShowBaseDetails;
    private boolean mShowTrigDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        // Rebuild constants. If the user changed their locale, it won't kill the app
        // but it might change a decimal point from . to ,
        Constants.rebuildConstants();
        mX = getString(R.string.X);

        mDisplayView = (DisplayOverlay) findViewById(R.id.display);
        mMainDisplay = (ViewGroup) mDisplayView.findViewById(R.id.main_display);
        mCalculationsDisplay = (ViewGroup) mMainDisplay.findViewById(R.id.calculations);
        mInfoView = (TextView) findViewById(R.id.info);
        mFormulaEditText = (AdvancedDisplay) findViewById(R.id.formula);
        mResultEditText = (AdvancedDisplay) findViewById(R.id.result);
        mPadViewPager = (CalculatorPadViewPager) findViewById(R.id.pad_pager);
        mDeleteButton = findViewById(R.id.del);
        mClearButton = findViewById(R.id.clr);
        mEqualButton = (EqualsImageButton) findViewById(R.id.pad_numeric).findViewById(R.id.eq);

        if (mEqualButton == null || mEqualButton.getVisibility() != View.VISIBLE) {
            mEqualButton = (EqualsImageButton) findViewById(R.id.pad_operator).findViewById(R.id.eq);
        }

        mTokenizer = new CalculatorExpressionTokenizer(this);
        mEvaluator = new CalculatorExpressionEvaluator(mTokenizer);

        savedInstanceState = savedInstanceState == null ? Bundle.EMPTY : savedInstanceState;
        setState(CalculatorState.values()[
                savedInstanceState.getInt(KEY_CURRENT_STATE, CalculatorState.INPUT.ordinal())]);

        mFormulaEditText.setSolver(mEvaluator.getSolver());
        mResultEditText.setSolver(mEvaluator.getSolver());
        mFormulaEditText.setText(mTokenizer.getLocalizedExpression(
                savedInstanceState.getString(KEY_CURRENT_EXPRESSION, "")));
        mFormulaEditText.addTextChangedListener(mFormulaTextWatcher);
        mFormulaEditText.setOnKeyListener(mFormulaOnKeyListener);
        mFormulaEditText.setOnTextSizeChangeListener(this);
        mDeleteButton.setOnLongClickListener(this);
        mResultEditText.setEnabled(false);

        mFormulaEditText.registerComponent(new MatrixView.DisplayComponent());
        mResultEditText.registerComponents(mFormulaEditText.getComponents());

        Base base = Base.DECIMAL;
        int baseOrdinal = savedInstanceState.getInt(KEY_BASE, -1);
        if (baseOrdinal != -1) {
            base = Base.values()[baseOrdinal];
        }
        mBaseManager = new NumberBaseManager(base);
        if (mPadViewPager != null) {
            mPadViewPager.setBaseManager(mBaseManager);
        }
        setSelectedBaseButton(base);

        mDisplayView.bringToFront();

        // Disable IME for this application
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM, WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        Button dot = (Button) findViewById(R.id.dec_point);
        dot.setText(String.valueOf(Constants.DECIMAL_POINT));

        GraphView miniGraph = (GraphView) findViewById(R.id.mini_graph);
        miniGraph.setShowGrid(false);
        miniGraph.setShowInlineNumbers(true);
        miniGraph.setShowOutline(false);
        miniGraph.setPanEnabled(false);
        miniGraph.setZoomEnabled(false);
        miniGraph.setBackgroundColor(getResources().getColor(R.color.graph_background));
        miniGraph.setGridColor(getResources().getColor(R.color.graph_axis));
        miniGraph.setGraphColor(getResources().getColor(R.color.graph_line));
        miniGraph.setTextColor(getResources().getColor(R.color.graph_text));
        GraphModule graphModule = new GraphModule(mEvaluator.getSolver());
        mGraphController = new GraphController(graphModule, miniGraph);

//        findViewById(R.id.exitGraph).setOnClickListener(mGraphController);
//        findViewById(R.id.minusZoom).setOnClickListener(mGraphController);
//        findViewById(R.id.plusZoom).setOnClickListener(mGraphController);
//        findViewById(R.id.resetZoom).setOnClickListener(mGraphController);

        mShowBaseDetails = !mBaseManager.getNumberBase().equals(Base.DECIMAL);
        mShowTrigDetails = false;

        updateDetails();

        mEvaluator.evaluate(mFormulaEditText.getText(), this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Load new history
        mPersist = new Persist(this);
        mPersist.load();
        mHistory = mPersist.getHistory();

        mHistoryAdapter = new HistoryAdapter(this, mHistory,
        new HistoryAdapter.HistoryItemCallback() {
            @Override
            public void onHistoryItemSelected(HistoryEntry entry) {
                mFormulaEditText.insert(entry.getEdited());
                mDisplayView.collapseHistory();
            }
        });
        mHistory.setObserver(mHistoryAdapter);
        mDisplayView.getHistoryView().setAdapter(mHistoryAdapter);
        mDisplayView.scrollToMostRecent();
    }

    private void transitionToGraph() {
        if (mResultEditText.getVisibility() == View.GONE) {
            return;
        }

        setState(CalculatorState.GRAPHING);

        // We don't want the display resizing, so hardcode its width for now.
        mMainDisplay.measure(
                View.MeasureSpec.makeMeasureSpec(mMainDisplay.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        mMainDisplay.getLayoutParams().height = mMainDisplay.getMeasuredHeight();

        // Now we need to shrink the calculations display
        int oldHeight = mCalculationsDisplay.getMeasuredHeight();

        // Hide the result and then measure to grab new coordinates
        mResultEditText.setVisibility(View.GONE);
        mCalculationsDisplay.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mCalculationsDisplay.measure(
                View.MeasureSpec.makeMeasureSpec(mCalculationsDisplay.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int newHeight = mCalculationsDisplay.getMeasuredHeight();

        // Now animate between the old and new heights
        ValueAnimator animator = ValueAnimator.ofInt(oldHeight, newHeight);
        animator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCalculationsDisplay.getLayoutParams().height = (int) animation.getAnimatedValue();
                mCalculationsDisplay.requestLayout();
            }
        });
        animator.start();
    }

    private void transitionToDisplay() {
        if (mResultEditText.getVisibility() == View.VISIBLE) {
            return;
        }

        // Now we need to expand the calculations display
        int oldHeight = mCalculationsDisplay.getMeasuredHeight();

        // Show the result and then measure to grab new coordinates
        mResultEditText.setVisibility(View.VISIBLE);
        mCalculationsDisplay.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mCalculationsDisplay.measure(
                View.MeasureSpec.makeMeasureSpec(mCalculationsDisplay.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int newHeight = mCalculationsDisplay.getMeasuredHeight();

        // Now animate between the old and new heights
        ValueAnimator animator = ValueAnimator.ofInt(oldHeight, newHeight);
        animator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCalculationsDisplay.getLayoutParams().height = (int) animation.getAnimatedValue();
                mCalculationsDisplay.requestLayout();
            }
        });
        animator.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                setState(CalculatorState.INPUT);
            }
        });
        animator.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHistory.enter(mFormulaEditText.getText(), mResultEditText.getText());
        mPersist.save();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // If there's an animation in progress, cancel it first to ensure our state is up-to-date.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_STATE, mCurrentState.ordinal());
        outState.putString(KEY_CURRENT_EXPRESSION,
                mTokenizer.getNormalizedExpression(mFormulaEditText.getText()));
        outState.putInt(KEY_BASE, mBaseManager.getNumberBase().ordinal());
    }

    private void setState(CalculatorState state) {
        if (mCurrentState != state) {
            mCurrentState = state;
            if (state == CalculatorState.RESULT || state == CalculatorState.ERROR) {
                mDeleteButton.setVisibility(View.GONE);
                mClearButton.setVisibility(View.VISIBLE);
            } else {
                mDeleteButton.setVisibility(View.VISIBLE);
                mClearButton.setVisibility(View.GONE);
            }

            if (state == CalculatorState.ERROR) {
                final int errorColor = getResources().getColor(R.color.calculator_error_color);
                mFormulaEditText.setTextColor(errorColor);
                mResultEditText.setTextColor(errorColor);
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    getWindow().setStatusBarColor(errorColor);
                }
            } else {
                mFormulaEditText.setTextColor(
                        getResources().getColor(R.color.display_formula_text_color));
                mResultEditText.setTextColor(
                        getResources().getColor(R.color.display_result_text_color));
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    getWindow().setStatusBarColor(
                            getResources().getColor(R.color.calculator_accent_color));
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mPadViewPager == null || mPadViewPager.getCurrentItem() == 0) {
            // If the user is currently looking at the first pad (or the pad is not paged),
            // allow the system to handle the Back button.
            super.onBackPressed();
        } else {
            // Otherwise, select the previous pad.
            mPadViewPager.setCurrentItem(mPadViewPager.getCurrentItem() - 1);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        // If there's an animation in progress, cancel it so the user interaction can be handled
        // immediately.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }
    }

    public void onButtonClick(View view) {
        mCurrentButton = view;
        switch (view.getId()) {
            case R.id.eq:
                onEquals();
                break;
            case R.id.del:
                onDelete();
                break;
            case R.id.clr:
                onClear();
                break;
            case R.id.det:
                // Add left parenthesis after functions.
                mFormulaEditText.insert(((Button) view).getText() + "(");
                break;
            case R.id.fun_cos:
            case R.id.fun_sin:
            case R.id.fun_tan:
                mShowTrigDetails = true;
                updateDetails();
            case R.id.fun_ln:
            case R.id.fun_log:
                // Add left parenthesis after functions.
                mFormulaEditText.insert(((Button) view).getText() + "(");
                break;
            case R.id.hex:
                setBase(Base.HEXADECIMAL);
                break;
            case R.id.bin:
                setBase(Base.BINARY);
                break;
            case R.id.dec:
                setBase(Base.DECIMAL);
                break;
            case R.id.matrix:
                mFormulaEditText.insert(MatrixView.getPattern());
                break;
            case R.id.matrix_inverse:
                mFormulaEditText.insert(MatrixInverseView.PATTERN);
                break;
            case R.id.matrix_transpose:
                mFormulaEditText.insert(MatrixTransposeView.PATTERN);
                break;
            case R.id.plus_row:
                if(mFormulaEditText.getActiveEditText() instanceof MatrixEditText) {
                    ((MatrixEditText) mFormulaEditText.getActiveEditText()).getMatrixView().addRow();
                }
                break;
            case R.id.minus_row:
                if(mFormulaEditText.getActiveEditText() instanceof MatrixEditText) {
                    ((MatrixEditText) mFormulaEditText.getActiveEditText()).getMatrixView().removeRow();
                }
                break;
            case R.id.plus_col:
                if(mFormulaEditText.getActiveEditText() instanceof MatrixEditText) {
                    ((MatrixEditText) mFormulaEditText.getActiveEditText()).getMatrixView().addColumn();
                }
                break;
            case R.id.minus_col:
                if(mFormulaEditText.getActiveEditText() instanceof MatrixEditText) {
                    ((MatrixEditText) mFormulaEditText.getActiveEditText()).getMatrixView().removeColumn();
                }
                break;
            case R.id.op_add:
            case R.id.op_sub:
            case R.id.op_mul:
            case R.id.op_div:
            case R.id.op_fact:
            case R.id.op_pow:
                mFormulaEditText.insert(((Button) view).getText());
                break;
            default:
                if(mCurrentState.equals(CalculatorState.INPUT) ||
                        mCurrentState.equals(CalculatorState.GRAPHING) ||
                        mFormulaEditText.isCursorModified()) {
                    mFormulaEditText.insert(((Button) view).getText());
                }
                else {
                    mFormulaEditText.setText(((Button) view).getText());
                }
                break;
        }
    }

    @Override
    public boolean onLongClick(View view) {
        mCurrentButton = view;
        if (view.getId() == R.id.del) {
            mHistory.enter(mFormulaEditText.getText(), mResultEditText.getText());
            onClear();
            return true;
        }
        return false;
    }

    @Override
    public void onEvaluate(String expr, String result, int errorResourceId) {
        if (mCurrentState == CalculatorState.INPUT
                || mCurrentState == CalculatorState.GRAPHING) {
            if (result == null || Solver.equal(result, expr)) {
                mResultEditText.clear();
            }
            else {
                mResultEditText.setText(result);
            }
        } else if (errorResourceId != INVALID_RES_ID) {
            onError(errorResourceId);
        } else if (expr.contains(mX)) {
            mHistory.enter(expr, result);
            mDisplayView.scrollToMostRecent();
            onResult("");
        } else if (!TextUtils.isEmpty(result)) {
            mHistory.enter(expr, result);
            mDisplayView.scrollToMostRecent();
            onResult(result);
        } else if (mCurrentState == CalculatorState.EVALUATE) {
            // The current expression cannot be evaluated -> return to the input state.
            setState(CalculatorState.INPUT);
        }

        if (expr.contains(mX)) {
            transitionToGraph();
            mGraphController.startGraph(mFormulaEditText.getText());
        } else {
            transitionToDisplay();
            if (expr.equals(result) || mFormulaEditText.hasNext()) {
                mEqualButton.setState(State.NEXT);
            } else {
                mEqualButton.setState(State.EQUALS);
            }
        }
    }

    @Override
    public void onTextSizeChanged(final AdvancedDisplay textView, float oldSize) {
        if (mCurrentState != CalculatorState.INPUT) { // TODO dont animate when showing graph
            // Only animate text changes that occur from user input.
            return;
        }

        // Calculate the values needed to perform the scale and translation animations,
        // maintaining the same apparent baseline for the displayed text.
        final float textScale = oldSize / textView.getTextSize();
        final float translationX;
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            translationX = (1.0f - textScale) *
                    (textView.getWidth() / 2.0f - textView.getPaddingEnd());
        }
        else {
            translationX = (1.0f - textScale) *
                    (textView.getWidth() / 2.0f - textView.getPaddingRight());
        }
        final float translationY = (1.0f - textScale) *
                (textView.getHeight() / 2.0f - textView.getPaddingBottom());
        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(textView, View.SCALE_X, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.SCALE_Y, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, translationX, 0.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, translationY, 0.0f));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }

    private void onEquals() {
        String text = mFormulaEditText.getText();
        if (mCurrentState == CalculatorState.INPUT) {
            switch(mEqualButton.getState()) {
                case EQUALS:
                    setState(CalculatorState.EVALUATE);
                    mEvaluator.evaluate(text, this);
                    break;
                case NEXT:
                    mFormulaEditText.next();
                    break;
            }
        } else if (mCurrentState == CalculatorState.GRAPHING) {
            setState(CalculatorState.EVALUATE);
            mEvaluator.evaluate(text, this);
        }
    }

    private void onDelete() {
        // Delete works like backspace; remove the last character from the expression.
        mFormulaEditText.backspace();
    }

    private void reveal(View sourceView, int colorRes, final AnimatorListener listener) {
        // Make reveal cover the display
        final RevealView revealView = new RevealView(this);
        revealView.setLayoutParams(mLayoutParams);
        revealView.setRevealColor(getResources().getColor(colorRes));
        mCalculationsDisplay.addView(revealView);

        final SupportAnimator revealAnimator;
        final int[] clearLocation = new int[2];
        sourceView.getLocationInWindow(clearLocation);
        clearLocation[0] += sourceView.getWidth() / 2;
        clearLocation[1] += sourceView.getHeight() / 2;
        final int revealCenterX = clearLocation[0] - revealView.getLeft();
        final int revealCenterY = clearLocation[1] - revealView.getTop();
        final double x1_2 = Math.pow(revealView.getLeft() - revealCenterX, 2);
        final double x2_2 = Math.pow(revealView.getRight() - revealCenterX, 2);
        final double y_2 = Math.pow(revealView.getTop() - revealCenterY, 2);
        final float revealRadius = (float) Math.max(Math.sqrt(x1_2 + y_2), Math.sqrt(x2_2 + y_2));

        revealAnimator =
                ViewAnimationUtils.createCircularReveal(revealView,
                        revealCenterX, revealCenterY, 0.0f, revealRadius);
        revealAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_longAnimTime));
        revealAnimator.addListener(listener);

        final Animator alphaAnimator = ObjectAnimator.ofFloat(revealView, View.ALPHA, 0.0f);
        alphaAnimator.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));
        alphaAnimator.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                mMainDisplay.removeView(revealView);
            }
        });

        revealAnimator.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                play(alphaAnimator);
            }
        });
        play(revealAnimator);
    }

    private void play(Animator animator) {
        mCurrentAnimator = animator;
        animator.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                mCurrentAnimator = null;
            }
        });
        animator.start();
    }

    private void onClear() {
        if (TextUtils.isEmpty(mFormulaEditText.getText())) {
            return;
        }
        reveal(mCurrentButton, R.color.calculator_accent_color, new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                mFormulaEditText.clear();
            }
        });
    }

    private void onError(final int errorResourceId) {
        if (mCurrentState != CalculatorState.EVALUATE) {
            // Only animate error on evaluate.
            mResultEditText.setText(errorResourceId);
            return;
        }

        reveal(mCurrentButton, R.color.calculator_error_color, new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                setState(CalculatorState.ERROR);
                mResultEditText.setText(errorResourceId);
            }
        });
    }

    private void onResult(final String result) {
        // Calculate the values needed to perform the scale and translation animations,
        // accounting for how the scale will affect the final position of the text.
        final float resultScale =
                mFormulaEditText.getVariableTextSize(result) / mResultEditText.getTextSize();
        final float resultTranslationX;
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            resultTranslationX = (1.0f - resultScale) *
                    (mResultEditText.getWidth() / 2.0f - mResultEditText.getPaddingEnd());
        }
        else {
            resultTranslationX = (1.0f - resultScale) *
                    (mResultEditText.getWidth() / 2.0f - mResultEditText.getPaddingRight());
        }
        final float resultTranslationY = (1.0f - resultScale) *
                (mResultEditText.getHeight() / 2.0f - mResultEditText.getPaddingBottom()) +
                (mFormulaEditText.getBottom() - mResultEditText.getBottom()) +
                (mResultEditText.getPaddingBottom() - mFormulaEditText.getPaddingBottom());
        final float formulaTranslationY = -mFormulaEditText.getBottom();

        // Use a value animator to fade to the final text color over the course of the animation.
        final int resultTextColor = mResultEditText.getCurrentTextColor();
        final int formulaTextColor = mFormulaEditText.getCurrentTextColor();
        final ValueAnimator textColorAnimator =
                ValueAnimator.ofObject(new ArgbEvaluator(), resultTextColor, formulaTextColor);
        textColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mResultEditText.setTextColor((Integer) valueAnimator.getAnimatedValue());
            }
        });

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                textColorAnimator,
                ObjectAnimator.ofFloat(mResultEditText, View.SCALE_X, resultScale),
                ObjectAnimator.ofFloat(mResultEditText, View.SCALE_Y, resultScale),
                ObjectAnimator.ofFloat(mResultEditText, View.TRANSLATION_X, resultTranslationX),
                ObjectAnimator.ofFloat(mResultEditText, View.TRANSLATION_Y, resultTranslationY),
                ObjectAnimator.ofFloat(mFormulaEditText, View.TRANSLATION_Y, formulaTranslationY));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_longAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                // Reset all of the values modified during the animation.
                mResultEditText.setTextColor(resultTextColor);
                mResultEditText.setScaleX(1.0f);
                mResultEditText.setScaleY(1.0f);
                mResultEditText.setTranslationX(0.0f);
                mResultEditText.setTranslationY(0.0f);
                mFormulaEditText.setTranslationY(0.0f);

                // Finally update the formula to use the current result.
                mFormulaEditText.setText(result);
                setState(CalculatorState.RESULT);
            }
        });

        play(animatorSet);
    }

    private void setBase(Base base) {
        // Update the BaseManager, which handles restricting which buttons to show
        mBaseManager.setNumberBase(base);
        mShowBaseDetails = true;

        // Update the evaluator, which handles the math
        mEvaluator.setBase(mFormulaEditText.getText(), base, new EvaluateCallback() {
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
        setSelectedBaseButton(base);

        // Disable any buttons that are not relevant to the current base
        for (int resId : mBaseManager.getViewIds(mPadViewPager == null ? -1 : mPadViewPager.getCurrentItem())) {
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
                                setState(CalculatorState.INPUT);
                                mEvaluator.evaluate(mFormulaEditText.getText(), Calculator.this);
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
