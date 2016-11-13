package com.xlythe.calculator.holo;

import android.content.Intent;
import android.support.annotation.DrawableRes;

import com.xlythe.view.floating.CreateShortcutActivity;

/**
 * Creates the shortcut icon
 */
public class FloatingCalculatorCreateShortCutActivity extends CreateShortcutActivity {
    @Override
    public CharSequence getShortcutName() {
        return getString(R.string.app_name);
    }

    @DrawableRes
    @Override
    public int getShortcutIcon() {
        return R.drawable.ic_launcher_floating;
    }

    @Override
    public Intent getOpenShortcutActivityIntent() {
        return new Intent(this, FloatingCalculatorOpenShortCutActivity.class);
    }
}
