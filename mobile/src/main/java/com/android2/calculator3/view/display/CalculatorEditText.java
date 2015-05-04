/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android2.calculator3.view.display;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.android2.calculator3.R;
import com.android2.calculator3.view.CalculatorEditable;
import com.android2.calculator3.view.TextUtil;
import com.xlythe.math.BaseModule;
import com.xlythe.math.Constants;
import com.xlythe.math.EquationFormatter;
import com.xlythe.math.Solver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalculatorEditText extends EditText {
    // Restrict keys from hardware keyboards
    private static final char[] ACCEPTED_CHARS = "0123456789.+-*/\u2212\u00d7\u00f7()!%^".toCharArray();

    private final Set<TextWatcher> mTextWatchers = new HashSet<>();
    private boolean mTextWatchersEnabled = true;
    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (!mTextWatchersEnabled || mSolver == null) return;
            mTextWatchersEnabled = false;

            String text = removeFormatting(s.toString());

            // Get the selection handle, since we're setting text and that'll overwrite it
            mSelectionHandle = getSelectionStart();

            // Adjust the handle by removing any comas or spacing to the left
            String cs = s.subSequence(0, mSelectionHandle).toString();
            mSelectionHandle -= TextUtil.countOccurrences(cs, mSolver.getBaseModule().getSeparator());

            // Update the text with formatted (comas, etc) text
            setText(formatText(text));
            setSelection(Math.min(mSelectionHandle, getText().length()));

            mTextWatchersEnabled = true;
        }
    };
    private EquationFormatter mEquationFormatter;
    private int mSelectionHandle = 0;
    private Solver mSolver;
    private EventListener mEventListener;
    private List<String> mKeywords;
    private Editable.Factory mFactory = new CalculatorEditable.Factory();

    private float mMaximumTextSize;
    private float mMinimumTextSize;
    private float mStepTextSize;

    // Try and use as large a text as possible, if the width allows it
    private int mWidthConstraint = -1;
    private int mHeightConstraint = -1;
    private final Paint mTempPaint = new TextPaint();
    private OnTextSizeChangeListener mOnTextSizeChangeListener;

    public CalculatorEditText(Context context) {
        super(context);
        setUp(context, null);
    }

    public CalculatorEditText(Context context, AttributeSet attr) {
        super(context, attr);
        setUp(context, attr);
    }

    private void setUp(Context context, AttributeSet attrs) {
        setLongClickable(false);

        // Disable highlighting text
        setCustomSelectionActionModeCallback(new NoTextSelectionMode());

        // Display ^ , and other visual cues
        mEquationFormatter = new EquationFormatter();
        addTextChangedListener(mTextWatcher);
        setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && mEventListener != null)
                    mEventListener.onEditTextChanged(CalculatorEditText.this);
            }
        });

        if(attrs != null) {
            final TypedArray a = context.obtainStyledAttributes(
                    attrs, R.styleable.CalculatorEditText, 0, 0);
            mMaximumTextSize = a.getDimension(
                    R.styleable.CalculatorEditText_maxTextSize, getTextSize());
            mMinimumTextSize = a.getDimension(
                    R.styleable.CalculatorEditText_minTextSize, getTextSize());
            mStepTextSize = a.getDimension(R.styleable.CalculatorEditText_stepTextSize,
                    (mMaximumTextSize - mMinimumTextSize) / 3);
            a.recycle();

            setTextSize(TypedValue.COMPLEX_UNIT_PX, mMaximumTextSize);
            setMinimumHeight((int) (mMaximumTextSize * 1.2) + getPaddingBottom() + getPaddingTop());
        }

        setEditableFactory(mFactory);

        mKeywords = Arrays.asList(
                context.getString(R.string.fun_arcsin) + "(",
                context.getString(R.string.fun_arccos) + "(",
                context.getString(R.string.fun_arctan) + "(",
                context.getString(R.string.fun_sin) + "(",
                context.getString(R.string.fun_cos) + "(",
                context.getString(R.string.fun_tan) + "(",
                context.getString(R.string.fun_log) + "(",
                context.getString(R.string.mod) + "(",
                context.getString(R.string.fun_ln) + "(",
                context.getString(R.string.det) + "(",
                context.getString(R.string.dx),
                context.getString(R.string.dy),
                context.getString(R.string.cbrt) + "(");
        NumberKeyListener calculatorKeyListener = new NumberKeyListener() {
            @Override
            public int getInputType() {
                return EditorInfo.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            }

            @Override
            protected char[] getAcceptedChars() {
                return ACCEPTED_CHARS;
            }

            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                /*
                 * the EditText should still accept letters (eg. 'sin') coming from the on-screen touch buttons, so don't filter anything.
                 */
                return null;
            }

            @Override
            public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
                if(keyCode == KeyEvent.KEYCODE_DEL) {
                    CalculatorEditText.this.backspace();
                }
                return super.onKeyDown(view, content, keyCode, event);
            }
        };
        setKeyListener(calculatorKeyListener);
    }

    @Override
    public void addTextChangedListener(TextWatcher watcher) {
        if (watcher.equals(mTextWatcher)) {
            super.addTextChangedListener(watcher);
        } else {
            mTextWatchers.add(watcher);
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (mTextWatchersEnabled) {
            for (TextWatcher textWatcher : mTextWatchers) {
                textWatcher.beforeTextChanged(getCleanText(), 0, 0, 0);
            }
        }
        super.setText(text, type);
        if (text != null) {
            setSelection(getText().length());
        }
        invalidateTextSize();
        if (mTextWatchersEnabled) {
            for (TextWatcher textWatcher : mTextWatchers) {
                textWatcher.afterTextChanged(mFactory.newEditable(getCleanText()));
                textWatcher.onTextChanged(getCleanText(), 0, 0, 0);
            }
        }
    }

    public String getCleanText() {
        return toString();
    }

    public void insert(String text) {
        if (mTextWatchersEnabled) {
            for (TextWatcher textWatcher : mTextWatchers) {
                textWatcher.beforeTextChanged(getCleanText(), 0, 0, 0);
            }
        }
        getText().insert(getSelectionStart(), text);
        invalidateTextSize();
        if (mTextWatchersEnabled) {
            for (TextWatcher textWatcher : mTextWatchers) {
                textWatcher.afterTextChanged(mFactory.newEditable(getCleanText()));
                textWatcher.onTextChanged(getCleanText(), 0, 0, 0);
            }
        }
    }

    private void invalidateTextSize() {
        float oldTextSize = getTextSize();
        float newTextSize = getVariableTextSize(getText().toString());
        if (oldTextSize != newTextSize) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, newTextSize);
        }
    }

    @Override
    public void setTextSize(int unit, float size) {
        final float oldTextSize = getTextSize();
        super.setTextSize(unit, size);
        if (mOnTextSizeChangeListener != null && getTextSize() != oldTextSize) {
            mOnTextSizeChangeListener.onTextSizeChanged(this, oldTextSize);
        }
    }

    public void clear() {
        setText(null);
    }

    public boolean isCursorModified() {
        return getSelectionStart() != getText().length();
    }

    public void next() {
        if (getSelectionStart() == getText().length()) {
            setSelection(0);
        } else {
            setSelection(getSelectionStart() + 1);
        }
    }

    public void backspace() {
        // Check and remove keywords
        int selectionHandle = getSelectionStart();
        String textBeforeInsertionHandle = getText().toString().substring(0, selectionHandle);
        String textAfterInsertionHandle = getText().toString().substring(selectionHandle, getText().toString().length());

        for(String s : mKeywords) {
            if(textBeforeInsertionHandle.endsWith(s)) {
                int deletionLength = s.length();
                String text = textBeforeInsertionHandle.substring(0, textBeforeInsertionHandle.length() - deletionLength) + textAfterInsertionHandle;
                setText(text);
                setSelection(selectionHandle - deletionLength);
                return;
            }
        }

        if (selectionHandle != 0) {
            setText(getText().subSequence(0, selectionHandle - 1).toString()
                            + getText().subSequence(selectionHandle, getText().length()));
        }
    }

    public void setOnTextSizeChangeListener(OnTextSizeChangeListener listener) {
        mOnTextSizeChangeListener = listener;
    }

    public float getVariableTextSize(String text) {
        if (mWidthConstraint < 0 || mMaximumTextSize <= mMinimumTextSize) {
            // Not measured, bail early.
            return getTextSize();
        }

        // Count exponents, which aren't measured properly.
        int exponents = TextUtil.countOccurrences(text, '^');

        // Step through increasing text sizes until the text would no longer fit.
        float lastFitTextSize = mMinimumTextSize;
        while (lastFitTextSize < mMaximumTextSize) {
            final float nextSize = Math.min(lastFitTextSize + mStepTextSize, mMaximumTextSize);
            mTempPaint.setTextSize(nextSize);
            if (mTempPaint.measureText(text) > mWidthConstraint) {
                break;
            } else if(nextSize + nextSize * exponents / 2 > mHeightConstraint) {
                break;
            } else {
                lastFitTextSize = nextSize;
            }
        }

        return lastFitTextSize;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mWidthConstraint =
                MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        mHeightConstraint =
                MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getVariableTextSize(getText().toString()));
    }

    public void setSolver(Solver solver) {
        mSolver = solver;
    }

    private String removeFormatting(String input) {
        input = input.replace(Constants.POWER_PLACEHOLDER, Constants.POWER);
        if(mSolver != null) {
            input = input.replace(String.valueOf(mSolver.getBaseModule().getSeparator()), "");
        }
        return input;
    }

    private Spanned formatText(String input) {
        if(mSolver != null) {
            // Add grouping, and then split on the selection handle
            // which is saved as a unique char
            String grouped = mEquationFormatter.addComas(mSolver, input, mSelectionHandle);
            if (grouped.contains(String.valueOf(BaseModule.SELECTION_HANDLE))) {
                String[] temp = grouped.split(String.valueOf(BaseModule.SELECTION_HANDLE));
                mSelectionHandle = temp[0].length();
                input = "";
                for (String s : temp) {
                    input += s;
                }
            } else {
                input = grouped;
                mSelectionHandle = input.length();
            }
        }

        return Html.fromHtml(mEquationFormatter.insertSupScripts(input));
    }

    @Override
    public String toString() {
        return removeFormatting(getText().toString());
    }

    @Override
    public View focusSearch(int direction) {
        View v;
        switch(direction) {
            case View.FOCUS_FORWARD:
                v = mEventListener.nextView(this);
                while(!v.isFocusable())
                    v = mEventListener.nextView(v);
                return v;
            case View.FOCUS_BACKWARD:
                v = mEventListener.previousView(this);
                while(!v.isFocusable())
                    v = mEventListener.previousView(v);
                return v;
        }
        return super.focusSearch(direction);
    }

    class NoTextSelectionMode implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Prevents the selection action mode on double tap.
            return false;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    }

    public interface OnTextSizeChangeListener {
        void onTextSizeChanged(TextView textView, float oldSize);
    }
}
