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
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;

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
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                invalidateSpannables();
            }
        });
        addSpanComponent(new MatrixComponent(getContext()));
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

            MathSpannable[] link = buffer.getSpans(off, off, MathSpannable.class);

            if (link.length != 0) {
                return link[0].onTouchEvent(event);
            }

            return super.onTouchEvent(widget, buffer, event);
        }
    }
}
