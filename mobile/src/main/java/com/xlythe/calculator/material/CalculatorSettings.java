package com.xlythe.calculator.material;

import android.content.Context;
import android.preference.PreferenceManager;

public class CalculatorSettings {

    static void setRadiansEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("USE_RADIANS", enabled).commit();
    }

    static boolean useRadians(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("USE_RADIANS", true);
    }

}
