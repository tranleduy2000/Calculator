/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xlythe.calculator.material;

import androidx.annotation.Nullable;


import org.javia.arity.Symbols;
import org.javia.arity.SyntaxException;

public class CalculatorExpressionEvaluator {
    private final Symbols mSolver;
    private final CalculatorExpressionTokenizer mTokenizer;

    public CalculatorExpressionEvaluator(CalculatorExpressionTokenizer tokenizer) {
        mSolver = new Symbols();
        mTokenizer = tokenizer;
    }

    public void evaluate(CharSequence expr, EvaluateCallback callback) {
        evaluate(expr.toString(), callback);
    }

    public void evaluate(String expr, EvaluateCallback callback) {
        expr = mTokenizer.getNormalizedExpression(expr);

        try {
            if (expr.length() != 0) {
                Double.valueOf(expr);
            }
            callback.onEvaluate(expr, null, null);
            return;
        } catch (NumberFormatException e) {
            // expr is not a simple number
        }

        try {
            double result = mSolver.eval(expr);
            callback.onEvaluate(expr, result, null);
        } catch (SyntaxException e) {
            callback.onEvaluate(expr, null, "Error");
        }
    }

    public interface EvaluateCallback {
        void onEvaluate(String expr, @Nullable Double result, String errorMessage);
    }
}