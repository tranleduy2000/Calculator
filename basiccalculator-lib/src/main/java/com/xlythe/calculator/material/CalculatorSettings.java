package com.xlythe.calculator.material;

import android.content.Context;

import androidx.preference.PreferenceManager;

public class CalculatorSettings {

    static void setRadiansEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("bscalc_USE_RADIANS", enabled).apply();
    }

    static boolean useRadians(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("bscalc_USE_RADIANS", true);
    }

}
