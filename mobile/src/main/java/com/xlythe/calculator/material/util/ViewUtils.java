package com.xlythe.calculator.material.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.TypedValue;

import androidx.annotation.NonNull;

public class ViewUtils {
    public static int getColor(@NonNull Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[]{attr});
        int color = a.getColor(0, 0);
        a.recycle();
        return color;
    }

}
