package com.android2.calculator3;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.android2.calculator3.view.FormattedNumberEditText;
import com.xlythe.math.Constants;
import com.xlythe.math.EquationFormatter;
import com.xlythe.math.History;
import com.xlythe.math.Persist;
import com.xlythe.math.Solver;

public class MainActivity extends Activity {

    /**
     * Constant for an invalid resource id.
     */
    public static final int INVALID_RES_ID = -1;

    // Calc logic
    private View.OnClickListener mListener;
    private ViewSwitcher mDisplay;
    private ImageButton mDelete;
    private ImageButton mClear;
    private ViewPager mPager;
    private Persist mPersist;
    private History mHistory;
    private CalculatorExpressionTokenizer mTokenizer;
    private CalculatorExpressionEvaluator mEvaluator;
    private State mState;

    private enum State {
        DELETE, CLEAR, ERROR;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                if (insets.isRound()) {
                    setContentView(R.layout.activity_main_round);
                } else {
                    setContentView(R.layout.activity_main);
                }
                initialize(insets);
                return insets;
            }
        });
        getWindow().getDecorView().requestApplyInsets();
    }

    protected void initialize(WindowInsets insets) {
        // Rebuild constants. If the user changed their locale, it won't kill the app
        // but it might change a decimal point from . to ,
        Constants.rebuildConstants();

        mTokenizer = new CalculatorExpressionTokenizer(this);
        mEvaluator = new CalculatorExpressionEvaluator(mTokenizer);

        mPager = (ViewPager) findViewById(R.id.panelswitch);

        mPersist = new Persist(this);
        mPersist.load();

        mHistory = mPersist.getHistory();

        mDisplay = (ViewSwitcher) findViewById(R.id.display);
        for (int i = 0; i < mDisplay.getChildCount(); i++) {
            final FormattedNumberEditText displayChild = (FormattedNumberEditText) mDisplay.getChildAt(i);
            displayChild.setSolver(mEvaluator.getSolver());
        }

        mDelete = (ImageButton) findViewById(R.id.delete);
        mClear = (ImageButton) findViewById(R.id.clear);
        mListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.delete:
                        onDelete();
                        break;
                    case R.id.clear:
                        mDisplay.showNext();
                        onClear();
                        break;
                    case R.id.eq:
                        mEvaluator.evaluate(getActiveEditText().getCleanText(), new CalculatorExpressionEvaluator.EvaluateCallback() {
                            @Override
                            public void onEvaluate(String expr, String result, int errorResourceId) {
                                mDisplay.showNext();
                                if (errorResourceId != MainActivity.INVALID_RES_ID) {
                                    onError(errorResourceId);
                                } else {
                                    setText(result);
                                }
                                if (saveHistory(expr, result)) {
                                    RecyclerView history = (RecyclerView) findViewById(R.id.history);
                                    history.getLayoutManager().scrollToPosition(history.getAdapter().getItemCount() - 1);
                                }
                            }
                        });
                        break;
                    case R.id.parentheses:
                        setText("(" + getActiveEditText().getText() + ")");
                        break;
                    default:
                        if(((Button) v).getText().toString().length() >= 2) {
                            onInsert(((Button) v).getText().toString() + "(");
                        } else {
                            onInsert(((Button) v).getText().toString());
                        }
                        break;
                }
            }
        };
        mDelete.setOnClickListener(mListener);
        mDelete.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mDisplay.showNext();
                onClear();
                return true;
            }
        });
        mClear.setOnClickListener(mListener);

        CalculatorPageAdapter adapter = new CalculatorPageAdapter(
                getBaseContext(), insets, mListener, mEvaluator.getSolver(), mHistory);
        mPager.setAdapter(adapter);
        mPager.setCurrentItem(1);

        setState(State.DELETE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPersist.save();
    }

    private void onDelete() {
        setState(State.DELETE);
        getActiveEditText().backspace();
    }

    private void onClear() {
        setState(State.DELETE);
        getActiveEditText().clear();
    }

    private void setText(String text) {
        setState(State.CLEAR);
        getActiveEditText().setText(text);
    }

    private void onInsert(String text) {
        if (mState == State.ERROR || (mState == State.CLEAR && !Solver.isOperator(text))) {
            setText(text);
        } else {
            getActiveEditText().insert(text);
        }

        setState(State.DELETE);
    }

    private void onError(int resId) {
        setState(State.ERROR);
        getActiveEditText().setText(resId);
    }

    private void setState(State state) {
        mDelete.setVisibility(state == State.DELETE ? View.VISIBLE : View.GONE);
        mClear.setVisibility(state != State.DELETE ? View.VISIBLE : View.GONE);
        if(mState != state) {
            switch (state) {
                case CLEAR:
                    getActiveEditText().setTextColor(getResources().getColor(R.color.display_formula_text_color));
                    break;
                case DELETE:
                    getActiveEditText().setTextColor(getResources().getColor(R.color.display_formula_text_color));
                    break;
                case ERROR:
                    getActiveEditText().setTextColor(getResources().getColor(R.color.calculator_error_color));
                    break;
            }
            mState = state;
        }
    }

    private FormattedNumberEditText getActiveEditText() {
        return (FormattedNumberEditText) mDisplay.getCurrentView();
    }

    protected boolean saveHistory(String expr, String result) {
        if (mHistory == null) {
            return false;
        }

        if (!TextUtils.isEmpty(expr)
                && !TextUtils.isEmpty(result)
                && !Solver.equal(expr, result)
                && (mHistory.current() == null || !mHistory.current().getFormula().equals(expr))) {
            expr = EquationFormatter.appendParenthesis(expr);
            expr = Solver.clean(expr);
            mHistory.enter(expr, result);
            return true;
        }
        return false;
    }
}
