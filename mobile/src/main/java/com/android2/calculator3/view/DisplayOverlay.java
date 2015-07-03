package com.android2.calculator3.view;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android2.calculator3.HistoryAdapter;
import com.android2.calculator3.R;
import com.xlythe.floatingview.AnimationFinishedListener;

/**
 * The display overlay is a container that intercepts touch events on top of:
 *      1. the display, i.e. the formula and result views
 *      2. the history view, which is revealed by dragging down on the display
 *
 * This overlay passes vertical scrolling events down to the history recycler view
 * when applicable.  If the user attempts to scroll up and the recycler is already
 * scrolled all the way up, then we intercept the event and collapse the history.
 */
public class DisplayOverlay extends RelativeLayout {

    /**
     * Alpha when history is pulled down
     * */
    private static final float MAX_ALPHA = 0.6f;

    private static boolean DEBUG = true;
    private static final String TAG = DisplayOverlay.class.getSimpleName();

    private VelocityTracker mVelocityTracker;
    private int mMinimumFlingVelocity;
    private int mMaximumFlingVelocity;
    private RecyclerView mRecyclerView;
    private View mMainDisplay;
    private View mDisplayBackground;
    private View mDisplayForeground;
    private View mDisplayGraph;
    private TextView mFormulaEditText;
    private TextView mResultEditText;
    private View mCalculationsDisplay;
    private View mInfoText;
    private LinearLayoutManager mLayoutManager;
    private int mTouchSlop;
    private float mInitialMotionY;
    private float mLastMotionY;
    private float mLastDeltaY;
    private int mMinTranslation = -1;
    private int mMaxTranslation = -1;
    private float mMaxDisplayScale = 1f;
    private int mFormulaInitColor = -1;
    private int mResultInitColor = -1;
    private View mFade;
    private final OnTouchListener mFadeOnTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            collapse();
            return true;
        }
    };
    private final DisplayAnimator mAnimator = new DisplayAnimator(0, 1f);
    private final Animator.AnimatorListener mCollapseListener = new AnimationFinishedListener() {
        @Override
        public void onAnimationFinished() {
            mDisplayBackground.setPivotX(mDisplayBackground.getWidth() / 2);
            if (mDisplayGraph.getVisibility() != View.VISIBLE) {
                mDisplayBackground.setPivotY(mDisplayBackground.getHeight() / 2);
            } else {
                mDisplayBackground.setPivotY(0);
            }

            mFormulaEditText.setPivotX(mFormulaEditText.getWidth() / 2);
            mFormulaEditText.setPivotY(mFormulaEditText.getHeight() / 2);

            mResultEditText.setPivotX(mResultEditText.getWidth() / 2);
            mResultEditText.setPivotY(mResultEditText.getHeight() / 2);
        }
    };
    private View mEvaluatedDisplay;

    public DisplayOverlay(Context context) {
        super(context);
        setup();
    }

    public DisplayOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public DisplayOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    public DisplayOverlay(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setup();
    }

    private void setup() {
        ViewConfiguration vc = ViewConfiguration.get(getContext());
        mMinimumFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mTouchSlop = vc.getScaledTouchSlop();
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (android.os.Build.VERSION.SDK_INT >= 16) {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
                evaluateHeight();
                setTranslationY(mMinTranslation);
                if (DEBUG) {
                    Log.v(TAG, String.format("mMinTranslation=%s, mMaxTranslation=%s", mMinTranslation, mMaxTranslation));
                }
                scrollToMostRecent();
            }
        });
    }

    public enum TranslateState {
        EXPANDED, COLLAPSED, PARTIAL
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRecyclerView = (RecyclerView) findViewById(R.id.historyRecycler);
        mLayoutManager = new LinearLayoutManager(getContext()) {
            private int[] mMeasuredDimension = new int[2];

            @Override
            public void onMeasure(RecyclerView.Recycler recycler, RecyclerView.State state,
                                  int widthSpec, int heightSpec) {
                final int widthMode = View.MeasureSpec.getMode(widthSpec);
                final int heightMode = View.MeasureSpec.getMode(heightSpec);
                final int widthSize = View.MeasureSpec.getSize(widthSpec);
                final int heightSize = View.MeasureSpec.getSize(heightSpec);
                int width = 0;
                int height = 0;
                for (int i = 0; i < getItemCount() && height < heightSize&& width < widthSize; i++) {
                    measureScrapChild(recycler, i,
                            View.MeasureSpec.makeMeasureSpec(i, View.MeasureSpec.UNSPECIFIED),
                            View.MeasureSpec.makeMeasureSpec(i, View.MeasureSpec.UNSPECIFIED),
                            mMeasuredDimension);

                    if (getOrientation() == HORIZONTAL) {
                        width = width + mMeasuredDimension[0];
                        if (i == 0) {
                            height = mMeasuredDimension[1];
                        }
                    } else {
                        height = height + mMeasuredDimension[1];
                        if (i == 0) {
                            width = mMeasuredDimension[0];
                        }
                    }
                }
                // If child view is more than screen size, there is no need to make it wrap content. We can use original onMeasure() so we can scroll view.
                if (height < heightSize && width < widthSize) {
                    switch (widthMode) {
                        case View.MeasureSpec.EXACTLY:
                            width = widthSize;
                        case View.MeasureSpec.AT_MOST:
                        case View.MeasureSpec.UNSPECIFIED:
                    }

                    switch (heightMode) {
                        case View.MeasureSpec.EXACTLY:
                            height = heightSize;
                        case View.MeasureSpec.AT_MOST:
                        case View.MeasureSpec.UNSPECIFIED:
                    }

                    setMeasuredDimension(width, height);
                } else {
                    // Override the size to match the entire screen (so when we drag it down it's not cut off)
                    setMeasuredDimension(DisplayOverlay.this.getWidth(), DisplayOverlay.this.getHeight());
                }
            }

            private void measureScrapChild(RecyclerView.Recycler recycler, int position, int widthSpec,
                                           int heightSpec, int[] measuredDimension) {
                View view = recycler.getViewForPosition(position);
                if (view != null) {
                    RecyclerView.LayoutParams p = (RecyclerView.LayoutParams) view.getLayoutParams();
                    int childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec,
                            getPaddingLeft() + getPaddingRight(), p.width);
                    int childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec,
                            getPaddingTop() + getPaddingBottom(), p.height);
                    view.measure(childWidthSpec, childHeightSpec);
                    measuredDimension[0] = view.getMeasuredWidth() + p.leftMargin + p.rightMargin;
                    measuredDimension[1] = view.getMeasuredHeight() + p.bottomMargin + p.topMargin;
                    recycler.recycleView(view);
                }
            }
        };
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mLayoutManager.setStackFromEnd(true);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private boolean mCloseOnFling;

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    mCloseOnFling = true;
                } else if (newState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    if (mCloseOnFling && isScrolledToEnd()) {
                        collapse();
                    }
                }
            }
        });

        mMainDisplay = findViewById(R.id.main_display);
        mDisplayBackground = findViewById(R.id.the_card);
        mDisplayForeground = findViewById(R.id.the_clear_animation);
        mDisplayGraph = findViewById(R.id.mini_graph);
        mFormulaEditText = (TextView) findViewById(R.id.formula);
        mResultEditText = (TextView) findViewById(R.id.result);
        mCalculationsDisplay = findViewById(R.id.calculations);
        mInfoText = findViewById(R.id.info);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // Nope.
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = MotionEventCompat.getActionMasked(ev);
        float y = ev.getRawY();
        boolean intercepted = false;
        TranslateState state = getTranslateState();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mInitialMotionY = y;
                mLastMotionY = y;
                handleDown();
                break;
            case MotionEvent.ACTION_MOVE:
                float delta = Math.abs(y - mInitialMotionY);
                if (delta > mTouchSlop) {
                    float dy = y - mInitialMotionY;
                    if (dy < 0) {
                        intercepted = isScrolledToEnd() && state != TranslateState.COLLAPSED;
                    } else if (dy > 0) {
                        intercepted = state != TranslateState.EXPANDED;
                    }
                }
                break;
        }

        return intercepted;
    }

    private boolean isScrolledToEnd() {
        return mLayoutManager.findLastCompletelyVisibleItemPosition() ==
                mRecyclerView.getAdapter().getItemCount() - 1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Handled in intercept
                break;
            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                break;
            case MotionEvent.ACTION_UP:
                handleUp(event);
                break;
        }

        return true;
    }

    private void handleDown() {
        evaluateHeight();

        if (mMinTranslation == mMaxTranslation) {
            return;
        }

        if (isCollapsed()) {
            mFormulaInitColor = mFormulaEditText.getCurrentTextColor();
            mResultInitColor = mResultEditText.getCurrentTextColor();

            mDisplayBackground.setPivotX(mDisplayBackground.getWidth() / 2);
            if (mDisplayGraph.getVisibility() != View.VISIBLE) {
                mDisplayBackground.setPivotY(mDisplayBackground.getHeight());
            } else {
                mDisplayBackground.setPivotY(0);
            }

            mFormulaEditText.setPivotX(0);
            mFormulaEditText.setPivotY(0);

            mResultEditText.setPivotX(mResultEditText.getWidth());
            mResultEditText.setPivotY(0);

            final String formula = mFormulaEditText.getText().toString();
            final String result = mResultEditText.getText().toString();
            mEvaluatedDisplay = getAdapter().parseView(mRecyclerView, formula, result);
            int leftMargin = ((MarginLayoutParams) mEvaluatedDisplay.getLayoutParams()).leftMargin;
            int topMargin = ((MarginLayoutParams) mEvaluatedDisplay.getLayoutParams()).topMargin;
            int rightMargin = ((MarginLayoutParams) mEvaluatedDisplay.getLayoutParams()).rightMargin;
            mEvaluatedDisplay.measure(MeasureSpec.makeMeasureSpec(getWidth() - leftMargin - rightMargin, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE / 2, MeasureSpec.AT_MOST));
            mEvaluatedDisplay.layout(leftMargin, topMargin, mEvaluatedDisplay.getMeasuredWidth() + leftMargin, mEvaluatedDisplay.getMeasuredHeight() + topMargin);
            Log.d(TAG, String.format("l=%s,t=%s,r=%s,b=%s,width=%s,height=%s", mEvaluatedDisplay.getLeft(), mEvaluatedDisplay.getTop(), mEvaluatedDisplay.getRight(), mEvaluatedDisplay.getBottom(), mEvaluatedDisplay.getWidth(), mEvaluatedDisplay.getHeight()));
        }
    }

    private void handleMove(MotionEvent event) {
        TranslateState state = getTranslateState();
        float y = event.getRawY();
        float dy = y - mLastMotionY;
        if (DEBUG) {
            Log.v(TAG, "handleMove y=" + y + ", dy=" + dy);
        }

        float percent = calculateCurrentPercent(dy);
        if (dy < 0 && state != TranslateState.COLLAPSED) {
            mAnimator.onUpdate(percent);
        } else if (dy > 0 && state != TranslateState.EXPANDED) {
            mAnimator.onUpdate(percent);
        }
        mLastMotionY = y;
        mLastDeltaY = dy;
    }

    private void handleUp(MotionEvent event) {
        if (mMinTranslation == mMaxTranslation) {
            return;
        }

        mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
        if (Math.abs(mVelocityTracker.getYVelocity()) > mMinimumFlingVelocity) {
            // the sign on velocity seems unreliable, so use last delta to determine direction
            if (mLastDeltaY > 0) {
                expand();
            } else {
                collapse();
            }
        } else {
            if (calculateCurrentPercent(0) > 0.5f) {
                expand();
            } else {
                collapse();
            }
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    private float calculateCurrentPercent(float dy) {
        float clampedY = Math.min(Math.max(getTranslationY() + dy, mMinTranslation), mMaxTranslation);
        float percent = getRange() <= 0 ? 0 : (clampedY - mMinTranslation) / getRange();
        return percent;
    }

    private float getRange() {
        return mMaxTranslation - mMinTranslation;
    }

    public void expand() {
        expand(null);
    }

    public void expand(Animator.AnimatorListener listener) {
        if (mMinTranslation == mMaxTranslation) {
            if (listener != null) {
                listener.onAnimationStart(null);
                listener.onAnimationEnd(null);
            }
            return;
        }

        DisplayAnimator animator = new DisplayAnimator(calculateCurrentPercent(0), 1f);
        if (listener != null) {
            animator.addListener(listener);
        }
        animator.start();

        // Close history when tapping on the background
        if (mFade != null) {
            mFade.setOnTouchListener(mFadeOnTouchListener);
        }
    }

    public void collapse() {
        collapse(null);
    }

    public void collapse(Animator.AnimatorListener listener) {
        if (mMinTranslation == mMaxTranslation) {
            if (listener != null) {
                listener.onAnimationStart(null);
                listener.onAnimationEnd(null);
            }
            return;
        }

        DisplayAnimator animator = new DisplayAnimator(calculateCurrentPercent(0), 0f);
        if (listener != null) {
            animator.addListener(listener);
        }
        animator.addListener(mCollapseListener);
        animator.start();

        // Remove the background onTouchListener
        if (mFade != null) {
            mFade.setOnTouchListener(null);
        }
    }

    public boolean isExpanded() {
        return getTranslateState() == TranslateState.EXPANDED;
    }

    public boolean isCollapsed() {
        return getTranslateState() == TranslateState.COLLAPSED;
    }

    public void transitionToGraph(Animator.AnimatorListener listener) {
        mDisplayGraph.setVisibility(View.VISIBLE);

        // We don't want the display resizing, so hardcode its width for now.
        mMainDisplay.measure(
                View.MeasureSpec.makeMeasureSpec(mMainDisplay.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        mMainDisplay.getLayoutParams().height = mMainDisplay.getMeasuredHeight();

        // Now we need to shrink the calculations display
        int oldHeight = mCalculationsDisplay.getMeasuredHeight();

        // Hide the result and then measure to grab new coordinates
        mResultEditText.setVisibility(View.GONE);
        mCalculationsDisplay.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mCalculationsDisplay.measure(
                View.MeasureSpec.makeMeasureSpec(mCalculationsDisplay.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int newHeight = mCalculationsDisplay.getMeasuredHeight();

        // Now animate between the old and new heights
        float scale = mMaxDisplayScale = (float) newHeight / oldHeight;
        long duration = getResources().getInteger(android.R.integer.config_longAnimTime);
        mDisplayBackground.setPivotY(0f);
        mDisplayBackground.animate()
                .scaleY(scale)
                .setListener(listener)
                .setDuration(duration)
                .start();

        // Update the foreground too (even though it's invisible)
        mDisplayForeground.setPivotY(0f);
        mDisplayForeground.animate()
                .scaleY(scale)
                .setDuration(duration)
                .start();
    }

    public void transitionToDisplay(Animator.AnimatorListener listener) {
        // Show the result again
        mResultEditText.setVisibility(View.VISIBLE);

        // Now animate between the old and new heights
        float scale = mMaxDisplayScale = 1f;
        long duration = getResources().getInteger(android.R.integer.config_longAnimTime);
        mDisplayBackground.animate()
                .scaleY(scale)
                .setListener(new AnimationFinishedListener() {
                    @Override
                    public void onAnimationFinished() {
                        mDisplayGraph.setVisibility(View.GONE);
                    }
                })
                .setDuration(duration)
                .start();

        // Update the foreground too (even though it's invisible)
        mDisplayForeground.animate()
                .scaleY(scale)
                .setListener(listener)
                .setDuration(duration)
                .start();
    }

    private TranslateState getTranslateState() {
        float txY = getTranslationY();
        if (txY <= mMinTranslation) {
            return TranslateState.COLLAPSED;
        } else if (txY >= mMaxTranslation) {
            return TranslateState.EXPANDED;
        } else {
            return TranslateState.PARTIAL;
        }
    }

    public void setAdapter(RecyclerView.Adapter adapter) {
        mRecyclerView.setAdapter(adapter);
    }

    public HistoryAdapter getAdapter() {
        return ((HistoryAdapter) mRecyclerView.getAdapter());
    }

    private boolean hasDisplayEntry() {
        HistoryAdapter adapter = (HistoryAdapter) mRecyclerView.getAdapter();
        return adapter.getDisplayEntry() != null;
    }

    private void evaluateHeight() {
        if (hasDisplayEntry()) {
            // The display turns into an entry item, which increases the recycler height by 1.
            // That means the height is dirty now :( Try again later.
            return;
        }

        mMinTranslation = -getHeight() + mMainDisplay.getHeight();
        View child = mRecyclerView.getChildAt(0);
        float childHeight = child == null ? 0 : child.getHeight();
        int itemCount = mRecyclerView.getAdapter().getItemCount() + 1;
        if (itemCount * childHeight < getHeight()) {
            mMaxTranslation = mMinTranslation + (int) (itemCount * childHeight);
        } else {
            mMaxTranslation = 0;
        }
    }

    public void scrollToMostRecent() {
        mRecyclerView.scrollToPosition(mRecyclerView.getAdapter().getItemCount()-1);
    }

    public void setFade(View view) {
        mFade = view;
    }

    /**
     * An animator that goes from 0 to 100%
     **/
    private class DisplayAnimator extends ValueAnimator {
        public DisplayAnimator(float start, float end) {
            super();
            setFloatValues(start, end);
            addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float percent = (float) animation.getAnimatedValue();
                    onUpdate(percent);
                }
            });
        }

        public void onUpdate(float percent) {
            // Update the drag animation
            float txY = mMinTranslation + percent * (getRange());
            setTranslationY(txY);

            // Update the background alpha
            if (mFade != null) {
                mFade.setAlpha(MAX_ALPHA * percent);
            }

            // Update the display
            final HistoryAdapter adapter = getAdapter();
            final String formula = mFormulaEditText.getText().toString();
            final String result = mResultEditText.getText().toString();
            float adjustedTranslation = 0;

            // Get both our current width/height and the width/height we want to be
            int width = mEvaluatedDisplay.findViewById(R.id.history_line).getWidth();
            int height = mEvaluatedDisplay.findViewById(R.id.history_line).getHeight();
            int displayWidth = mDisplayBackground.getWidth();
            int displayHeight = mDisplayBackground.getHeight();

            // When we're fully expanded, turn the display into another row on the history adapter
            if (percent == 1f) {
                if (adapter.getDisplayEntry() == null) {
                    // We're at 100%, but haven't switched to the adapter yet. Time to do your thing.
                    adapter.setDisplayEntry(formula, result);
                    mDisplayBackground.setVisibility(View.GONE);
                    mCalculationsDisplay.setVisibility(View.GONE);
                    mDisplayGraph.setVisibility(View.GONE);
                    scrollToMostRecent();
                }

                // Adjust margins to match the entry
                adjustedTranslation += mEvaluatedDisplay.getHeight();
            } else if (adapter.getDisplayEntry() != null) {
                // We're no longer at 100%, so remove the entry (if it's attached)
                adapter.clearDisplayEntry();
                mDisplayBackground.setVisibility(View.VISIBLE);
                mCalculationsDisplay.setVisibility(View.VISIBLE);
                if (adapter.hasGraph(formula)) {
                    mDisplayGraph.setVisibility(View.VISIBLE);
                }
            }

            float scaledWidth = scale(percent, (float) width / displayWidth);
            float scaledHeight = Math.min(scale(percent, (float) height / displayHeight), mMaxDisplayScale);

            // Scale the card behind everything
            mDisplayBackground.setScaleX(scaledWidth);
            mDisplayBackground.setScaleY(scaledHeight);

            // Scale the graph behind the card (may be invisible, but oh well) TODO -- proper translations
            mDisplayGraph.setTranslationY(percent * -height);
            mDisplayGraph.setScaleX(scaledWidth);

            // Move the formula over to the far left
            TextView exprView = (TextView) mEvaluatedDisplay.findViewById(R.id.historyExpr);
            float exprScale = exprView.getTextSize() / mFormulaEditText.getTextSize();
            mFormulaEditText.setScaleX(scale(percent, exprScale));
            mFormulaEditText.setScaleY(scale(percent, exprScale));
            float formulaWidth = exprView.getPaint().measureText(mFormulaEditText.getText().toString());
            mFormulaEditText.setTranslationX(percent * (
                    + formulaWidth
                    - exprScale * (mFormulaEditText.getWidth() - mFormulaEditText.getPaddingRight())
                    + getLeft(exprView, null)
            ));
            mFormulaEditText.setTranslationY(percent * (
                    + getTop(exprView, null)
                    - exprScale * mFormulaEditText.getPaddingTop()
                    + exprScale * mFormulaEditText.getPaddingBottom()
            ));
            mFormulaEditText.setTextColor(mixColors(percent, mFormulaInitColor, exprView.getCurrentTextColor()));

            // Move the result to keep in place with the display TODO 'graph' text for graphs
            TextView resultView = (TextView) mEvaluatedDisplay.findViewById(R.id.historyResult);
            float resultScale = resultView.getTextSize() / mResultEditText.getTextSize();
            mResultEditText.setScaleX(scale(percent, resultScale));
            mResultEditText.setScaleY(scale(percent, resultScale));
            mResultEditText.setTranslationX(percent * (
                    // We have pivotX set at getWidth(), so the right sides will match up.
                    // Adjust the right edges of the real and the calculated views
                    - getRight(mResultEditText, mCalculationsDisplay)
                    + getRight(resultView, null)

                    // But getRight() doesn't include padding! So match the padding as well
                    + mResultEditText.getPaddingRight() * scale(percent, resultScale)
                    - resultView.getPaddingRight()
            ));
            mResultEditText.setTranslationY(percent * (
                    // Likewise, pivotY is set to 0, so the top sides will match up
                    // Adjust the top edges of the real and the calculated views
                    - getTop(mResultEditText, mCalculationsDisplay)
                    + getTop(resultView, null)

                    // But getTop() doesn't include padding! So match the padding as well
                    - mResultEditText.getPaddingTop() * scale(percent, resultScale)
                    + resultView.getPaddingTop()
            ));
            mResultEditText.setTextColor(mixColors(percent, mResultInitColor, resultView.getCurrentTextColor()));

            // Fade away HEX/RAD info text
            mInfoText.setAlpha(scale(percent, 0));

            // Handle readjustment of everything so it follows the finger
            adjustedTranslation += percent * (
                    + mDisplayBackground.getPivotY()
                    - mDisplayBackground.getPivotY() * height / mDisplayBackground.getHeight());

            mRecyclerView.setTranslationY(adjustedTranslation);
            mCalculationsDisplay.setTranslationY(adjustedTranslation);
            mInfoText.setTranslationY(adjustedTranslation);

            mFormulaEditText.setEnabled(percent == 0);

            if (DEBUG) {
                Log.d(TAG, String.format("percent=%s,txY=%s,alpha=%s,width=%s,height=%s,scaledWidth=%s,scaledHeight=%s",
                        percent, txY, mFade.getAlpha(), width, height, scaledWidth, scaledHeight));
            }
        }

        float scale(float percent, float goal) {
            return 1f - percent * (1f - goal);
        }

        int mixColors(float percent, int initColor, int goalColor) {
            int a1 = Color.alpha(goalColor);
            int r1 = Color.red(goalColor);
            int g1 = Color.green(goalColor);
            int b1 = Color.blue(goalColor);

            int a2 = Color.alpha(initColor);
            int r2 = Color.red(initColor);
            int g2 = Color.green(initColor);
            int b2 = Color.blue(initColor);

            percent = Math.min(1, percent);
            percent = Math.max(0, percent);
            float a = a1 * percent + a2 * (1 - percent);
            float r = r1 * percent + r2 * (1 - percent);
            float g = g1 * percent + g2 * (1 - percent);
            float b = b1 * percent + b2 * (1 - percent);

            return Color.argb((int) a, (int) r, (int) g, (int) b);
        }
    }

    private int getLeft(View view, View relativeTo) {
        if (view == null || view == relativeTo) {
            return 0;
        }
        return view.getLeft() + (view.getParent() instanceof View ? getLeft((View) view.getParent(), relativeTo) : 0);
    }

    private int getRight(View view, View relativeTo) {
        return getLeft(view, relativeTo) + view.getWidth();
    }

    private int getTop(View view, View relativeTo) {
        if (view == null || view == relativeTo) {
            return 0;
        }
        return view.getTop() + (view.getParent() instanceof View ? getTop((View) view.getParent(), relativeTo) : 0);
    }
}
