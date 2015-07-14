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

package com.android2.calculator3.view;

import android.content.Context;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import com.xlythe.math.BaseModule;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class CalculatorEditText extends FormattedNumberEditText {
    // Look for special text (like matrices) that we want to format differently
    private final Set<SpanComponent> mComponents = new HashSet<>();
    private final Set<CharacterStyle> mSpans = new HashSet<>();

    public CalculatorEditText(Context context) {
        super(context);
        setUp(context, null);
    }

    public CalculatorEditText(Context context, AttributeSet attr) {
        super(context, attr);
        setUp(context, attr);
    }

    private void setUp(Context context, AttributeSet attrs) {
        setMovementMethod(new MathMovementMethod());
        addSpanComponent(new MatrixComponent(getContext()));
    }

    protected void onFormat(Editable s) {
        // Grab the text, as well as the selection handle
        String editable = s.toString();
        MutableInteger selectionHandle = new MutableInteger(getSelectionStart());

        // Make adjustments (insert will append a SELECTION_HANDLE marker)
        int customHandle = editable.indexOf(BaseModule.SELECTION_HANDLE);
        if (customHandle >= 0) {
            selectionHandle.set(customHandle);
            editable = editable.replace(Character.toString(BaseModule.SELECTION_HANDLE), "");
        }

        // Update the text with the correct (no SELECTION_HANDLE) copy
        setText(editable);
        setSelection(selectionHandle.intValue());
        invalidateSpannables();
        s = getText();

        // We don't want to format anything that's controlled by MathSpannables (like matrices).
        // So grab all the spans in our EditText
        MathSpannable[] spans = s.getSpans(0, s.length(), MathSpannable.class);
        final Editable s2 = s;
        Arrays.sort(spans, new Comparator<MathSpannable>() {
            @Override
            public int compare(MathSpannable a, MathSpannable b) {
                return s2.getSpanStart(a) - s2.getSpanStart(b);
            }
        });

        // Ah, no spans. Nothing to think about, so easy.
        if (spans.length == 0) {
            super.onFormat(s);
            return;
        }

        // Start formatting, but skip the parts that involve spans
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < spans.length + 1; i++) {
            int start = i == 0 ? 0 : s.getSpanEnd(spans[i - 1]);
            int end = i == spans.length ? s.length() : s.getSpanStart(spans[i]);

            String text = editable.substring(start, end);
            if (selectionHandle.intValue() >= start && selectionHandle.intValue() < end) {
                // Special case -- keep track of the selection handle
                String cs = text.substring(0, selectionHandle.intValue() - start);
                selectionHandle.subtract(TextUtil.countOccurrences(cs, getSolver().getBaseModule().getSeparator()));
            }
            text = formatText(removeFormatting(text), selectionHandle);
            builder.append(text);
            if (i < spans.length) {
                builder.append(spans[i].getEquation());
            }
        }

        // Update the text with formatted (comas, etc) text
        setText(Html.fromHtml(builder.toString()));
        setSelection(selectionHandle.intValue());
        invalidateSpannables();
    }

    @Override
    protected String removeFormatting(String input) {
        StringBuilder cleanText = new StringBuilder();
        StringBuilder cache = new StringBuilder();

        loop: for (int i = 0; i < input.length(); i++) {
            for (SpanComponent component : mComponents) {
                String equation = component.parse(input.substring(i));
                if (equation != null) {
                    // Apply super.removeFormatting on the cache (the part we didn't really care about)
                    cleanText.append(super.removeFormatting(cache.toString()));
                    cache = new StringBuilder();

                    // Leave the parsed equation as-is (TODO: clean this too? via component?)
                    cleanText.append(equation);
                    i += equation.length();

                    // Go to the next character
                    continue loop;
                }
            }
            cache.append(input.charAt(i));
        }
        cleanText.append(super.removeFormatting(cache.toString()));
        return cleanText.toString();
    }

    public void addSpanComponent(SpanComponent component) {
        mComponents.add(component);
        invalidateSpannables();
    }

    public void invalidateSpannables() {
        final Spannable spans = getText();
        final String text = spans.toString();

        // Remove existing spans
        for (CharacterStyle style : mSpans) {
            spans.removeSpan(style);
        }

        // Loop over the text, looking for new spans
        for (int i = 0; i < text.length(); i++) {
            for (SpanComponent component : mComponents) {
                String equation = component.parse(text.substring(i));
                if (equation != null) {
                    MathSpannable span = component.getSpan(equation);
                    spans.setSpan(span, i, i + equation.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i += equation.length();
                    break;
                }
            }
        }
    }

    @Override
    public void backspace() {
        if (getSelectionStart() > 0) {
            MathSpannable[] spans = getText().getSpans(getSelectionStart(), getSelectionStart(), MathSpannable.class);
            if (spans.length != 0) {
                if (spans[0].removeOnBackspace()) {
                    String text = getText().toString();
                    int selectionHandle = getSelectionStart();
                    String textBeforeInsertionHandle = text.substring(0, selectionHandle);
                    String textAfterInsertionHandle = text.substring(selectionHandle, text.length());

                    int deletionLength = spans[0].getEquation().length();
                    String newText = textBeforeInsertionHandle.substring(0, textBeforeInsertionHandle.length() - deletionLength) + textAfterInsertionHandle;
                    setText(newText);
                    setSelection(selectionHandle - deletionLength);

                    return;
                }
            }
        }

        super.backspace();
    }

    public static abstract class SpanComponent {
        public abstract String parse(String formula);
        public abstract MathSpannable getSpan(String equation);
    }

    /**
     * A span that represents a mathematical expression (eg. a matrix) that can't be easily
     * expressed as just text
     * */
    public static abstract class MathSpannable extends ReplacementSpan {
        private String mEquation;

        public MathSpannable(String equation) {
            mEquation = equation;
        }

        public String getEquation() {
            return mEquation;
        }

        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        public boolean removeOnBackspace() {
            return false;
        }
    }

    /**
     * Looks for MathSpannables and passes onTouch events to them
     * */
    public static class MathMovementMethod extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            MathSpannable[] spans = buffer.getSpans(off, off, MathSpannable.class);

            if (spans.length != 0) {
                return spans[0].onTouchEvent(event);
            }

            return super.onTouchEvent(widget, buffer, event);
        }
    }
}
