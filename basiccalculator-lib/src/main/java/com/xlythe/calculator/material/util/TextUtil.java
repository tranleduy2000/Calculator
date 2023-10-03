package com.xlythe.calculator.material.util;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * For formatting text in the display
 */
public class TextUtil {
    public static String getCleanText(TextView textView) {
        return removeFormatting(textView.getText().toString());
    }

    public static String formatText(String input) {
        return  input;
    }

    @NonNull
    protected static String removeFormatting(String input) {
            input = input.replace(String.valueOf(' '), "");
        return input;
    }

    public static int countOccurrences(@Nullable String haystack, char needle) {
        if (haystack == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < haystack.length(); i++) {
            if (haystack.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }
}
