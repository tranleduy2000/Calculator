package com.xlythe.calculator;

import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.xlythe.calculator.material.BasicCalculatorDialogFragment;

public class MainActivity extends AppCompatActivity {
    Button button;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout view = new FrameLayout(this);
        button = new Button(this);
        button.setText("Show calc");
        button.setOnClickListener(v -> showCalculatorDialog());
        view.addView(button);
        setContentView(view);
        showCalculatorDialog();
    }



    private void showCalculatorDialog() {

        BasicCalculatorDialogFragment basicCalculator = new BasicCalculatorDialogFragment();
        basicCalculator.setOnResultConfirmed(aDouble ->  {
            this.button.setText(aDouble.toString());
        });
        basicCalculator.show(getSupportFragmentManager(), "BasicCalculatorDialogFragment");
    }
}
