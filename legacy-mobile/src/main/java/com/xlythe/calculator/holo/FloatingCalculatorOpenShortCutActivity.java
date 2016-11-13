package com.xlythe.calculator.holo;

import android.content.Intent;

import com.xlythe.view.floating.OpenShortcutActivity;

/**
 * When the shortcut icon is pressed, use this Activity to launch the overlay Service
 */
public class FloatingCalculatorOpenShortCutActivity extends OpenShortcutActivity {
    @Override
    public Intent createServiceIntent() {
        return new Intent(this, FloatingCalculator.class);
    }
}
