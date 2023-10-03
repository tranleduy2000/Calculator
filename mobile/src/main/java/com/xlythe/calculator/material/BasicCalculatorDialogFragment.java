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
package com.xlythe.calculator.material;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.MutableLiveData;

import com.xlythe.calculator.material.CalculatorExpressionEvaluator.EvaluateCallback;
import com.xlythe.calculator.material.util.TextUtil;
import com.xlythe.calculator.material.util.ViewUtils;
import com.xlythe.calculator.material.view.AnimationFinishedListener;
import com.xlythe.calculator.material.view.DisplayOverlay;
import com.xlythe.calculator.material.view.FormattedNumberEditText;
import com.xlythe.calculator.material.view.ResizingEditText.OnTextSizeChangeListener;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.Consumer;

import io.codetail.animation.SupportAnimator;
import io.codetail.animation.ViewAnimationUtils;
import io.codetail.widget.RevealView;

/**
 * A very basic calculator. Maps button clicks to the display, and solves on each key press.
 */
public class BasicCalculatorDialogFragment extends DialogFragment
        implements OnTextSizeChangeListener, EvaluateCallback, OnLongClickListener {

    protected static final String NAME = "Calculator";
    // instance state keys
    private static final String KEY_CURRENT_STATE = NAME + "_currentState";
    private static final String KEY_CURRENT_EXPRESSION = NAME + "_currentExpression";
    private final ViewGroup.LayoutParams mLayoutParams = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    private CalculatorState mCurrentState;

    private DecimalFormat decimalFormat = new DecimalFormat("#.#######", DecimalFormatSymbols.getInstance(Locale.US));

    private CalculatorExpressionTokenizer mTokenizer;
    private CalculatorExpressionEvaluator mEvaluator;
    private DisplayOverlay mDisplayView;
    private FormattedNumberEditText mFormulaEditText;
    private TextView mResultEditText;
    private View mDeleteButton;
    private View mEqualButton;
    private View mClearButton;
    private TextView mInfoView;
    private final TextWatcher mFormulaTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            setState(CalculatorState.INPUT);
            mEvaluator.evaluate(editable, BasicCalculatorDialogFragment.this);
        }
    };
    private View mCurrentButton;
    private Animator mCurrentAnimator;
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
    private ViewGroup mDisplayForeground;

    private View mConfirmButton;
    private TextView mConfirmResultTextView;
    private MutableLiveData<Double> mResultData = new MutableLiveData<>(null);
    @Nullable
    private Consumer<Double> mOnResultConfirmed;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bscalc_fragment_basic_calculator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        savedInstanceState = savedInstanceState == null ? Bundle.EMPTY : savedInstanceState;
        initialize(savedInstanceState);
        mEvaluator.evaluate(mFormulaEditText.getCleanText(), this);
    }

    public final <T extends View> T findViewById(@IdRes int id) {
        return requireView().findViewById(id);
    }

    protected void initialize(Bundle savedInstanceState) {

        mDisplayView = findViewById(R.id.display);
        mDisplayForeground = findViewById(R.id.the_clear_animation);
        mFormulaEditText = findViewById(R.id.formula);
        mResultEditText = findViewById(R.id.result);
        mDeleteButton = findViewById(R.id.del);
        mClearButton = findViewById(R.id.clr);
        mEqualButton = findViewById(R.id.pad_numeric).findViewById(R.id.eq);
        mInfoView = findViewById(R.id.info);

        if (mEqualButton == null || mEqualButton.getVisibility() != View.VISIBLE) {
            mEqualButton = findViewById(R.id.pad_operator).findViewById(R.id.eq);
        }

        mTokenizer = new CalculatorExpressionTokenizer(requireContext());
        mEvaluator = new CalculatorExpressionEvaluator(mTokenizer);

        setState(CalculatorState.values()[
                savedInstanceState.getInt(KEY_CURRENT_STATE, CalculatorState.INPUT.ordinal())]);

        mFormulaEditText.setText(mTokenizer.getLocalizedExpression(
                savedInstanceState.getString(KEY_CURRENT_EXPRESSION, "")));
        mFormulaEditText.addTextChangedListener(mFormulaTextWatcher);
        mFormulaEditText.setOnKeyListener(mFormulaOnKeyListener);
        mFormulaEditText.setOnTextSizeChangeListener(this);
        mFormulaEditText.setShowSoftInputOnFocus(false);
        mDeleteButton.setOnLongClickListener(this);
        findViewById(R.id.lparen).setOnLongClickListener(this);
        findViewById(R.id.rparen).setOnLongClickListener(this);
        findViewById(R.id.fun_sin).setOnLongClickListener(this);
        findViewById(R.id.fun_cos).setOnLongClickListener(this);
        findViewById(R.id.fun_tan).setOnLongClickListener(this);

        Button dot = findViewById(R.id.dec_point);
        dot.setText(String.valueOf(Constants.DECIMAL_POINT));

        int[] viewIds = {R.id.digit_0, R.id.digit_1, R.id.digit_2, R.id.digit_3, R.id.digit_4,
                R.id.digit_5, R.id.digit_6, R.id.digit_7, R.id.digit_7, R.id.digit_8, R.id.digit_9,
                R.id.dec_point, R.id.eq, R.id.clr, R.id.fun_sin, R.id.fun_cos, R.id.fun_tan,
                R.id.fun_ln, R.id.fun_log, R.id.op_fact, R.id.op_mul, R.id.op_sub, R.id.op_add,
                R.id.const_pi, R.id.const_e, R.id.op_pow, R.id.op_div, R.id.lparen, R.id.rparen, R.id.btn_sqrt,
                R.id.btn_const_imaginary, R.id.btn_percent, R.id.del,};
        for (int viewId : viewIds) {
            findViewById(viewId).setOnClickListener(this::onButtonClick);
        }

        // DEG|RAD button
        invalidateDetails();

        mConfirmResultTextView = findViewById(R.id.confirm_result_text);
        mConfirmButton = findViewById(R.id.btn_confirm);
        mResultData.observe(getViewLifecycleOwner(), (value) -> {
            if ((value != null) && Double.isFinite(value)) {
                mConfirmResultTextView.setText(decimalFormat.format(value));
            } else {
                mConfirmResultTextView.setText("");
            }
            mConfirmButton.setEnabled(value != null);
        });
        mResultData.postValue(null);
        mConfirmButton.setOnClickListener(v ->  {
            if (mOnResultConfirmed != null && mResultData.getValue() != null) {
                mOnResultConfirmed.accept(mResultData.getValue());
                this.dismiss();
            }
        });
    }

    protected void invalidateDetails() {
        Detail detail = getUnitDetail();
        String text = detail.word;
        if (mInfoView != null) {
            mInfoView.setText(text);
            mInfoView.setOnClickListener(detail.listener);
        }
    }

    private Detail getUnitDetail() {
        String text = CalculatorSettings.useRadians(requireContext()) ?
                "RAD" : "DEG";

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int RAD = 0;
                final int DEG = 1;
                final PopupMenu popupMenu = new PopupMenu(requireContext(), mInfoView);
                final Menu menu = popupMenu.getMenu();
                menu.add(0, RAD, menu.size(), "RAD");
                menu.add(0, DEG, menu.size(), "DEG");
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case RAD:
                                CalculatorSettings.setRadiansEnabled(requireContext(), true);
                                break;
                            case DEG:
                                CalculatorSettings.setRadiansEnabled(requireContext(), false);
                                break;
                        }
                        invalidateDetails();
                        setState(CalculatorState.INPUT);
                        getEvaluator().evaluate(mFormulaEditText.getCleanText(), BasicCalculatorDialogFragment.this);
                        return true;
                    }
                });
                popupMenu.show();
            }
        };
        return new Detail(text, listener);
    }

    @Override
    public void onResume() {
        super.onResume();

        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                Resources resources = requireContext().getResources();
                window.setLayout(
                        resources.getDimensionPixelSize(R.dimen.basic_calculator_dialog_width),
                        resources.getDimensionPixelSize(R.dimen.basic_calculator_dialog_height));
            }
        }

        incrementGroupId();

        mDisplayView.scrollToMostRecent();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveHistory(mFormulaEditText.getCleanText(), TextUtil.getCleanText(mResultEditText));
    }

    protected boolean saveHistory(String expr, String result) {
        return result != null && expr != null;

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        // If there's an animation in progress, cancel it first to ensure our state is up-to-date.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_STATE, mCurrentState.ordinal());
        outState.putString(KEY_CURRENT_EXPRESSION,
                mTokenizer.getNormalizedExpression(mFormulaEditText.getCleanText()));
    }

    protected void setState(CalculatorState state) {
        if (mCurrentState != state) {
            mCurrentState = state;
            invalidateEqualsButton();

            if (state == CalculatorState.RESULT || state == CalculatorState.ERROR) {
                mDeleteButton.setVisibility(View.GONE);
                mClearButton.setVisibility(View.VISIBLE);
            } else {
                mDeleteButton.setVisibility(View.VISIBLE);
                mClearButton.setVisibility(View.GONE);
            }

            if (state == CalculatorState.ERROR) {
                mFormulaEditText.setTextColor(ViewUtils.getColor(requireContext(), R.attr.colorError));
                mResultEditText.setTextColor(ViewUtils.getColor(requireContext(), R.attr.colorError));
            } else {
                mFormulaEditText.setTextColor(ViewUtils.getColor(requireContext(), android.R.attr.textColorPrimary));
                mResultEditText.setTextColor(ViewUtils.getColor(requireContext(), android.R.attr.textColorSecondary));
            }
        }
    }

    //@Override
//    public void onBackPressed() {
//        if (mDisplayView.isExpanded()) {
//            mDisplayView.collapse();
//        } else if (mPadViewPager != null && mPadViewPager.isExpanded()) {
//            mPadViewPager.collapse();
//        } else {
//            super.onBackPressed();
//        }
//    }

    @SuppressLint("NonConstantResourceId")
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
            case R.id.fun_cos:
            case R.id.fun_sin:
            case R.id.fun_tan:
            case R.id.fun_ln:
            case R.id.fun_log:
                // Add left parenthesis after functions.
                insert(((Button) view).getText() + "(");
                break;
            case R.id.op_add:
            case R.id.op_sub:
            case R.id.op_mul:
            case R.id.op_div:
            case R.id.op_fact:
            case R.id.op_pow:
                mFormulaEditText.insert(((Button) view).getText().toString());
                break;
            default:
                insert(((Button) view).getText().toString());
                break;
        }
    }

    @SuppressLint({"NonConstantResourceId", "SetTextI18n"})
    @Override
    public boolean onLongClick(View view) {
        mCurrentButton = view;
        switch (view.getId()) {
            case R.id.del:
                saveHistory(mFormulaEditText.getCleanText(), TextUtil.getCleanText(mResultEditText));
                onClear();
                return true;
            case R.id.lparen:
            case R.id.rparen:
                mFormulaEditText.setText('(' + mFormulaEditText.getCleanText() + ')');
                return true;
            case R.id.fun_sin:
                insert("asin(");
                return true;
            case R.id.fun_cos:
                insert("acos(");
                return true;
            case R.id.fun_tan:
                insert("atan(");
                return true;
        }
        return false;
    }

    /**
     * Inserts text into the formula EditText. If an equation was recently solved, it will
     * replace the formula's text instead of appending.
     */
    protected void insert(String text) {
        // Add left parenthesis after functions.
        if (mCurrentState.equals(CalculatorState.INPUT) ||
                mFormulaEditText.isCursorModified()) {
            mFormulaEditText.insert(text);
        } else {
            mFormulaEditText.setText(text);
            incrementGroupId();
        }
    }

    @Override
    public void onEvaluate(String expr, @Nullable Double resultNum, String errorMessage) {
        mResultData.postValue(resultNum);

        String result = null;
        if (resultNum != null) {
            result = decimalFormat.format(resultNum);
        }

        if (mCurrentState == CalculatorState.INPUT) {
            if (result == null) {
                mResultEditText.setText(null);
            } else {
                mResultEditText.setText(TextUtil.formatText(result));
            }
        } else if (errorMessage != null) {
            onError(errorMessage);
        } else if (saveHistory(expr, result)) {
            mDisplayView.scrollToMostRecent();
            onResult(result);
        } else if (mCurrentState == CalculatorState.EVALUATE) {
            // The current expression cannot be evaluated -> return to the input state.
            setState(CalculatorState.INPUT);
        }
        invalidateEqualsButton();
    }

    protected void incrementGroupId() {
    }

    protected void invalidateEqualsButton() {
        // Do nothing. Extensions of Basic Calculator may want to set the equals button to
        // Next mode during certain conditions.
    }

    @Override
    public void onTextSizeChanged(final TextView textView, float oldSize) {
        if (mCurrentState != CalculatorState.INPUT) { // TODO dont animate when showing graph
            // Only animate text changes that occur from user input.
            return;
        }

        // Calculate the values needed to perform the scale and translation animations,
        // maintaining the same apparent baseline for the displayed text.
        final float textScale = oldSize / textView.getTextSize();
        final float translationX;
        translationX = (1.0f - textScale) *
                (textView.getWidth() / 2.0f - textView.getPaddingEnd());
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

    protected void onEquals() {
        String text = mFormulaEditText.getCleanText();
        if (mCurrentState == CalculatorState.INPUT) {
            setState(CalculatorState.EVALUATE);
            mEvaluator.evaluate(text, this);
        }
    }

    protected void onDelete() {
        // Delete works like backspace; remove the last character from the expression.
        mFormulaEditText.backspace();
    }

    private void reveal(View sourceView, @ColorInt int color, final AnimatorListener listener) {
        // Make reveal cover the display
        final RevealView revealView = new RevealView(requireContext());
        revealView.setLayoutParams(mLayoutParams);
        revealView.setRevealColor(color);
        mDisplayForeground.addView(revealView);

        final SupportAnimator revealAnimator;
        final int[] clearLocation = new int[2];
        if (sourceView != null) {
            sourceView.getLocationInWindow(clearLocation);
            clearLocation[0] += sourceView.getWidth() / 2;
            clearLocation[1] += sourceView.getHeight() / 2;
        } else {
            clearLocation[0] = mDisplayForeground.getWidth() / 2;
            clearLocation[1] = mDisplayForeground.getHeight() / 2;
        }
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
                mDisplayForeground.removeView(revealView);
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

    protected void play(Animator animator) {
        mCurrentAnimator = animator;
        animator.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                mCurrentAnimator = null;
            }
        });
        animator.start();
    }

//    @Override
//    public void onUserInteraction() {
//        super.onUserInteraction();
//        if (mCurrentAnimator != null) {
//            mCurrentAnimator.cancel();
//            mCurrentAnimator = null;
//        }
//    }

    protected void onClear() {
        if (TextUtils.isEmpty(mFormulaEditText.getCleanText())) {
            return;
        }
        reveal(mCurrentButton, ViewUtils.getColor(requireContext(), R.attr.colorAccent), new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                mFormulaEditText.clear();
                incrementGroupId();
            }
        });
    }

    protected void onError(final String errorMessage) {
        if (mCurrentState != CalculatorState.EVALUATE) {
            // Only animate error on evaluate.
            mResultEditText.setText(errorMessage);
            return;
        }

        reveal(mCurrentButton, ViewUtils.getColor(requireContext(), R.attr.colorError), new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                setState(CalculatorState.ERROR);
                mResultEditText.setText(errorMessage);
            }
        });
    }

    protected void onResult(final String result) {
        // Calculate the values needed to perform the scale and translation animations,
        // accounting for how the scale will affect the final position of the text.
        final float resultScale =
                mFormulaEditText.getVariableTextSize(result) / mResultEditText.getTextSize();
        final float resultTranslationX = (1.0f - resultScale) *
                (mResultEditText.getWidth() / 2.0f - mResultEditText.getPaddingRight());

        // Calculate the height of the formula (without padding)
        final float formulaRealHeight = mFormulaEditText.getHeight()
                - mFormulaEditText.getPaddingTop()
                - mFormulaEditText.getPaddingBottom();

        // Calculate the height of the resized result (without padding)
        final float resultRealHeight = resultScale *
                (mResultEditText.getHeight()
                        - mResultEditText.getPaddingTop()
                        - mResultEditText.getPaddingBottom());

        // Now adjust the result upwards!
        final float resultTranslationY =
                // Move the result up (so both formula + result heights match)
                -mFormulaEditText.getHeight()
                        // Now switch the result's padding top with the formula's padding top
                        - resultScale * mResultEditText.getPaddingTop()
                        + mFormulaEditText.getPaddingTop()
                        // But the result centers its text! And it's taller now! So adjust for that centered text
                        + (formulaRealHeight - resultRealHeight) / 2;

        // Move the formula all the way to the top of the screen
        final float formulaTranslationY = -mFormulaEditText.getBottom();

        // Use a value animator to fade to the final text color over the course of the animation.
        final int resultTextColor = mResultEditText.getCurrentTextColor();
        final int formulaTextColor = mFormulaEditText.getCurrentTextColor();
        final ValueAnimator textColorAnimator =
                ValueAnimator.ofObject(new ArgbEvaluator(), resultTextColor, formulaTextColor);
        textColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator valueAnimator) {
                mResultEditText.setTextColor((Integer) valueAnimator.getAnimatedValue());
            }
        });
        mResultEditText.setText(TextUtil.formatText(result));
        mResultEditText.setPivotX(mResultEditText.getWidth() / 2f);
        mResultEditText.setPivotY(0f);

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
                mResultEditText.setPivotY(mResultEditText.getHeight() / 2.0f);
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

    protected CalculatorExpressionEvaluator getEvaluator() {
        return mEvaluator;
    }

    public void setOnResultConfirmed(@Nullable Consumer<Double> onResultConfirmed) {
        this.mOnResultConfirmed = onResultConfirmed;
    }

    protected enum CalculatorState {
        INPUT, EVALUATE, RESULT, ERROR
    }

    public static class Detail {
        public final String word;
        public final View.OnClickListener listener;

        public Detail(String word, View.OnClickListener listener) {
            this.word = word;
            this.listener = listener;
        }
    }
}
