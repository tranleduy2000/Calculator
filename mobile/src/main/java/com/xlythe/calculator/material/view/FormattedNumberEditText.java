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

package com.xlythe.calculator.material.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;

import com.xlythe.calculator.material.Constants;
import com.xlythe.calculator.material.Solver;
import com.xlythe.calculator.material.util.TextUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FormattedNumberEditText adds more advanced functionality to NumberEditText.
 * <p>
 * Commas will appear as numbers are typed, exponents will be raised, and backspacing
 * on sin( and log( will remove the whole word. Because of the formatting, getText() will
 * no longer return the correct value. getCleanText() has been added instead.
 */
@SuppressLint("SetTextI18n")
public class FormattedNumberEditText extends NumberEditText {
    private final Set<TextWatcher> mTextWatchers = new HashSet<>();
    private boolean mTextWatchersEnabled = true;
    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (!mTextWatchersEnabled || getSelectionStart() == -1) return;
            mTextWatchersEnabled = false;
            onFormat(s);
            mTextWatchersEnabled = true;
        }
    };
    private List<String> mKeywords;
    private boolean mIsInserting;

    public FormattedNumberEditText(Context context) {
        super(context);
        setUp(context, null);
    }

    public FormattedNumberEditText(Context context, AttributeSet attr) {
        super(context, attr);
        setUp(context, attr);
    }

    private void setUp(Context context, AttributeSet attrs) {
        addTextChangedListener(mTextWatcher);
        invalidateKeywords(context);
    }

    public void invalidateKeywords(Context context) {
        mKeywords = Arrays.asList(
                "asin(",
                "acos(",
                "atan(",
                "sin(",
                "cos(",
                "tan(",
                "acsc(",
                "asec(",
                "acot(",
                "csc(",
                "sec(",
                "cot(",
                "log(",
                "mod(",
                "ln(");

    }

    protected void onFormat(Editable s) {

    }

    @Override
    public void addTextChangedListener(TextWatcher watcher) {
        // Some flavors of Android call addTextChangedListener in the constructor, so add a
        // null check to mTextWatchers
        if (watcher.equals(mTextWatcher) || mTextWatchers == null) {
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
        if (text != null && !mIsInserting) {
            setSelection(getText().length());
        }
        invalidateTextSize();
        if (mTextWatchersEnabled) {
            for (TextWatcher textWatcher : mTextWatchers) {
                textWatcher.afterTextChanged(getEditableFactory().newEditable(getCleanText()));
                textWatcher.onTextChanged(getCleanText(), 0, 0, 0);
            }
        }
    }

    public String getCleanText() {
        return TextUtil.getCleanText(this);
    }

    public void insert(String delta) {
        String currentText = getText().toString();
        int selectionHandle = getSelectionStart();
        String textBeforeInsertionHandle = currentText.substring(0, selectionHandle);
        String textAfterInsertionHandle = currentText.substring(selectionHandle, currentText.length());

        // Add extra rules for decimal points and operators
        if (delta.length() == 1) {
            char text = delta.charAt(0);

            // don't allow two dots in the same number
            if (text == Constants.DECIMAL_POINT) {
                int p = selectionHandle - 1;
                while (p >= 0 && Solver.isDigit(getText().charAt(p))) {
                    if (getText().charAt(p) == Constants.DECIMAL_POINT) {
                        return;
                    }
                    --p;
                }
                p = selectionHandle;
                while (p < getText().length() && Solver.isDigit(getText().charAt(p))) {
                    if (getText().charAt(p) == Constants.DECIMAL_POINT) {
                        return;
                    }
                    ++p;
                }
            }

            char prevChar = selectionHandle > 0 ? getText().charAt(selectionHandle - 1) : '\0';

            // don't allow 2 successive minuses
            if (text == Constants.MINUS && prevChar == Constants.MINUS) {
                return;
            }

            // don't allow the first character to be an operator
            if (selectionHandle == 0 && Solver.isOperator(text) && text != Constants.MINUS) {
                return;
            }

            // don't allow multiple successive operators
            if (Solver.isOperator(text) && text != Constants.MINUS) {
                while (Solver.isOperator(prevChar)) {
                    if (selectionHandle == 1) {
                        return;
                    }

                    --selectionHandle;
                    prevChar = selectionHandle > 0 ? getText().charAt(selectionHandle - 1) : '\0';
                    textBeforeInsertionHandle = textBeforeInsertionHandle.substring(0, selectionHandle);
                }
            }
        }

        mIsInserting = true;
        setText(textBeforeInsertionHandle + delta + textAfterInsertionHandle);
        setSelection((textBeforeInsertionHandle + delta).length());
        mIsInserting = false;
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

    @Override
    public void backspace() {
        // Check and remove keywords
        String text = getText().toString();
        int selectionHandle = getSelectionStart();
        String textBeforeInsertionHandle = text.substring(0, selectionHandle);
        String textAfterInsertionHandle = text.substring(selectionHandle, text.length());

        for (String s : mKeywords) {
            if (textBeforeInsertionHandle.endsWith(s)) {
                int deletionLength = s.length();
                String newText = textBeforeInsertionHandle.substring(0, textBeforeInsertionHandle.length() - deletionLength) + textAfterInsertionHandle;
                setText(newText);
                setSelection(selectionHandle - deletionLength);
                return;
            }
        }

        // Override NumberEditText's method -- because commas might disappear, it complicates things
        if (selectionHandle != 0) {
            setText(textBeforeInsertionHandle.substring(0, textBeforeInsertionHandle.length() - 1)
                    + textAfterInsertionHandle);

            if (getText().length() == text.length() - 2) {
                // 2 characters were deleted (likely a comma and a number)
                selectionHandle -= 2;
            } else {
                --selectionHandle;
            }

            setSelection(selectionHandle);
        }
    }

    @Override
    public void setSelection(int index) {
        super.setSelection(Math.max(0, Math.min(getText().length(), index)));
    }

    @Override
    public int getSelectionStart() {
        // When setting a movement method, selectionStart() suddenly defaults to -1 instead of 0.
        return Math.max(0, super.getSelectionStart());
    }


}
