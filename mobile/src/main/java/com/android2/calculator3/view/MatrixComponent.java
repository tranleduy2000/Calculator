package com.android2.calculator3.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import com.android2.calculator3.R;
import com.android2.calculator3.view.CalculatorEditText.SpanComponent;
import com.android2.calculator3.view.CalculatorEditText.MathSpannable;

import com.xlythe.math.Constants;

import java.util.regex.Pattern;

/**
 * A component for CalculatorEditText that draws matrices in a really pretty way
 */
public class MatrixComponent extends SpanComponent {
    private final Context mContext;

    public MatrixComponent(Context context) {
        super();
        mContext = context;
    }

    @Override
    public String parse(String formula) {
        if (verify(formula)) {
            return parseMatrix(formula);
        } else {
            return null;
        }
    }

    @Override
    public MathSpannable getSpan(String equation) {
        return new MatrixSpannable(mContext, equation);
    }

    public static String getPattern() {
        return "[[" + Constants.MATRIX_SEPARATOR + "][" + Constants.MATRIX_SEPARATOR + "]]";
    }

    private static boolean verify(String text) {
        String separator = String.valueOf(Constants.MATRIX_SEPARATOR);
        String decimal = String.valueOf(Constants.DECIMAL_POINT);
        String validMatrix = "\\[(\\[[\u2212-]?[A-F0-9]*(" + Pattern.quote(decimal) + "[A-F0-9]*)?(" + Pattern.quote(separator) + "[\u2212-]?[A-F0-9]*(" + Pattern.quote(decimal) + "[A-F0-9]*)?)*\\])+\\].*";
        return text.matches(validMatrix);
    }

    private static String parseMatrix(String text) {
        int bracket_open = 0;
        int bracket_closed = 0;
        for(int i = 0; i < text.length(); i++) {
            if(text.charAt(i) == '[') {
                bracket_open++;
            } else if(text.charAt(i) == ']') {
                bracket_closed++;
            }
            if(bracket_open == bracket_closed) return text.substring(0, i + 1);
        }
        return "";
    }

    private static class MatrixSpannable extends MathSpannable {
        private final Context mContext;

        public MatrixSpannable(Context context, String equation) {
            super(equation);
            mContext = context;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return 500;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            Drawable background;
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                background = mContext.getResources().getDrawable(R.drawable.matrix_background, null);
            } else {
                background = mContext.getResources().getDrawable(R.drawable.matrix_background);
            }
            background.setBounds((int) x, top, (int) x + 500, bottom);
            background.draw(canvas);
        }

        @Override
        public boolean removeOnBackspace() {
            return true;
        }
    }
}
