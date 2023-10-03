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

package com.xlythe.calculator.material.view;

import android.content.Context;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import com.xlythe.calculator.material.util.TextUtil;
import com.xlythe.math.BaseModule;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class CalculatorEditText extends FormattedNumberEditText {

    public CalculatorEditText(Context context) {
        super(context);
    }

    public CalculatorEditText(Context context, AttributeSet attr) {
        super(context, attr);
    }
}
