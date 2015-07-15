package com.android2.calculator3.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.Log;

import com.android2.calculator3.R;
import com.android2.calculator3.view.CalculatorEditText.SpanComponent;
import com.android2.calculator3.view.CalculatorEditText.MathSpannable;

import com.xlythe.math.Constants;
import com.xlythe.math.Solver;

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
        private final String[][] mData;

        private final NinePatchDrawable mBackground;
        private final Rect mBackgroundPadding = new Rect();
        private final float mMinColumnWidth;

        // Cached copy of the span's width
        private int mSize;

        public MatrixSpannable(Context context, String equation) {
            super(equation);
            mContext = context;

            final int rows = countOccurrences(equation, '[') - 1;
            final int columns = countOccurrences(equation, Constants.MATRIX_SEPARATOR) / rows + 1;

            mData = new String[rows][columns];

            String[] data = equation.split(Pattern.quote(Character.toString(Constants.MATRIX_SEPARATOR)) + "|\\]\\[");
            for(int order = 0, row = 0; row < rows; row++) {
                for(int column = 0; column < columns; column++) {
                    mData[row][column] = data[order].replaceAll("[\\[\\]]", "");
                    order++;
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= 21) {
                mBackground = (NinePatchDrawable) mContext.getResources().getDrawable(R.drawable.matrix_background, null);
            } else {
                mBackground = (NinePatchDrawable) mContext.getResources().getDrawable(R.drawable.matrix_background);
            }
            mBackground.getPadding(mBackgroundPadding);

            mMinColumnWidth = mContext.getResources().getDisplayMetrics().density * 50;
        }

        private int getColumnSize(Paint paint, int column) {
            float largestTextWidth = 0;
            for (int i = 0; i < mData.length; i++) {
                largestTextWidth = Math.max(paint.measureText(mData[i][column]), largestTextWidth);
            }
            return (int) Math.max(mMinColumnWidth, largestTextWidth);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (mSize != 0) {
                return mSize;
            }

            int padding = mBackgroundPadding.left + mBackgroundPadding.right;
            int columnSize = 0;
            for (int i = 0; i < mData[0].length; i++) {
                columnSize += getColumnSize(paint, i);
            }
            mSize = Math.max(padding + columnSize, mBackground.getIntrinsicWidth());
            return mSize;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            // Draw the background
            mBackground.setBounds((int) x, top, (int) x + mSize, bottom);
            mBackground.draw(canvas);

            // Draw the text
            float xOffset = x + mBackgroundPadding.left;
            for (int i = 0; i < mData.length; i++) {
                for (int j = 0; j < mData[i].length; j++) {
                    String pos = mData[i][j];
                    canvas.drawText(pos, 0, pos.length(), xOffset, 10, paint);
                }
            }
        }

        @Override
        public boolean removeOnBackspace() {
            return true;
        }

        private static int countOccurrences(String haystack, char needle) {
            int count = 0;
            for(int i = 0; i < haystack.length(); i++) {
                if(haystack.charAt(i) == needle) {
                    count++;
                }
            }
            return count;
        }

    }
}
