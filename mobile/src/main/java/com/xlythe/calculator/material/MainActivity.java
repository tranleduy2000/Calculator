package com.xlythe.calculator.material;

import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout view = new FrameLayout(this);
        Button button = new Button(this);
        button.setText("Show calc");
        button.setOnClickListener(v -> showCalculatorDialog());
        view.addView(button);
        setContentView(view);
        showCalculatorDialog();
    }



    private void showCalculatorDialog() {

        BasicCalculatorDialogFragment basicCalculator = new BasicCalculatorDialogFragment();
        basicCalculator.show(getSupportFragmentManager(), "BasicCalculatorDialogFragment");
    }
}
