package com.android2.calculator3.view;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.RelativeLayout;

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
     * Closing the history with a fling will finish at least this fast (ms)
     */
    private static final float MIN_SETTLE_DURATION = 200f;

    /**
     * Alpha when history is pulled down
     * */
    private static final float MAX_ALPHA = 0.6f;

    private static boolean DEBUG = true;
    private static final String TAG = "DisplayOverlay";

    private RecyclerView mRecyclerView;
    private View mMainDisplay;
    private CardView mDisplayBackground;
    private View mDisplayForeground;
    private View mDisplayGraph;
    private View mFormulaEditText;
    private View mResultEditText;
    private View mCalculationsDisplay;
    private LinearLayoutManager mLayoutManager;
    private float mInitialMotionY;
    private float mLastMotionY;
    private float mLastDeltaY;
    private int mMinTranslation = -1;
    private int mMaxTranslation = -1;
    private float mMinVelocity = -1;
    private float mMaxDisplayScale = 1f;
    private View mFade;
    private final OnTouchListener mFadeOnTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            collapse();
            return true;
        }
    };
    private final DisplayAnimator mAnimator = new DisplayAnimator(0, 1f);

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
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (android.os.Build.VERSION.SDK_INT >= 16) {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
                mDisplayBackground.setPivotY(0);
                evaluateHeight();
                setTranslationY(mMinTranslation);
                if (DEBUG) {
                    Log.v(TAG, String.format("mMinTranslation=%s, mMaxTranslation=%s", mMinTranslation, mMaxTranslation));
                }
                initializeHistory();
            }
        });
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // Nope.
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
                for (int i = 0; i < getItemCount(); i++) {
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
                    super.onMeasure(recycler, state, widthSpec, heightSpec);
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
        mRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
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
        mDisplayBackground = (CardView) findViewById(R.id.the_card);
        mDisplayForeground = findViewById(R.id.the_clear_animation);
        mDisplayGraph = findViewById(R.id.mini_graph);
        mFormulaEditText = findViewById(R.id.formula);
        mResultEditText = findViewById(R.id.result);
        mCalculationsDisplay = findViewById(R.id.calculations);
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
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = y - mInitialMotionY;
                if (dy < 0) {
                    intercepted = isScrolledToEnd() && state != TranslateState.COLLAPSED;
                } else if (dy > 0) {
                    intercepted = state != TranslateState.EXPANDED;
                }

                break;
        }

        if (!intercepted) {
            mInitialMotionY = y;
            mLastMotionY = y;
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
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                evaluateHeight();
                break;
            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                break;
            case MotionEvent.ACTION_UP:
                handleUp();
                break;
        }

        return true;
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

    private void handleUp() {
        TranslateState curState = getTranslateState();
        if (curState == TranslateState.PARTIAL) {
            // the sign on velocity seems unreliable, so use last delta to determine direction
            if (mLastDeltaY > 0) {
                expand();
            } else {
                collapse();
            }
        }
    }

    private float calculateCurrentPercent(float dy) {
        float clampedY = Math.min(Math.max(getTranslationY() + dy, mMinTranslation), mMaxTranslation);
        float range = mMaxTranslation - mMinTranslation;
        float percent = range == 0 ? 0 : (clampedY - mMinTranslation) / range;
        return percent;
    }

    public void expand() {
        expand(null);
    }

    public void expand(Animator.AnimatorListener listener) {
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
        DisplayAnimator animator = new DisplayAnimator(calculateCurrentPercent(0), 0f);
        if (listener != null) {
            animator.addListener(listener);
        }
        animator.start();

        // Remove the background onTouchListener
        if (mFade != null) {
            mFade.setOnTouchListener(null);
        }
    }

    public boolean isExpanded() {
        return getTranslateState() == TranslateState.EXPANDED;
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

    private void evaluateHeight() {
        mMinTranslation = -getHeight() + mMainDisplay.getHeight();
        mMaxTranslation = mMinTranslation + mRecyclerView.getHeight();
    }

    /**
     * Set the size and offset of the history view
     *
     * We want the display+history to take up the full height of the parent minus some
     * predefined padding.  The normal way to do this would be to give the overlay a height
     * of match_parent minus some margin, and set an initial translation.  The issue with
     * this is that the display has a height of wrap content and the keypad fills the
     * remaining space, so we cannot determine the proper height for the history view until
     * after layout completes.
     *
     * To account for this, we make this method available to setup the history and graph
     * views after layout completes.
     */
    public void initializeHistory() {
        scrollToMostRecent();

        if (mMinVelocity < 0) {
            int txDist = mMaxTranslation;
            mMinVelocity = txDist / MIN_SETTLE_DURATION;
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
            float txY = mMinTranslation + percent * (mMaxTranslation - mMinTranslation);
            setTranslationY(txY);

            // Update the background alpha
            if (mFade != null) {
                mFade.setAlpha(MAX_ALPHA * percent);
            }

            // Update the display
            float scalePercent = -1;
            float scaledWidth = -1;
            float scaledHeight = -1;
            CardView child = (CardView) mRecyclerView.getChildAt(0);
            if (child != null) {
                int width = child.getWidth();
                int height = child.getHeight();
                int displayWidth = mDisplayBackground.getWidth();
                int displayHeight = mDisplayBackground.getHeight();

                scalePercent = Math.min(1f, (txY - mMinTranslation) / height);

                scaledWidth = 1f - scalePercent * (1 - (float) width / displayWidth);
                scaledHeight = 1f - scalePercent * (1 - (float) height / displayHeight);
                scaledHeight = Math.min(scaledHeight, mMaxDisplayScale);

                mDisplayBackground.setScaleX(scaledWidth);
                mDisplayBackground.setScaleY(scaledHeight);

                float graphTranslation = scalePercent * -height;
                mDisplayGraph.setTranslationY(graphTranslation);
                mDisplayGraph.setScaleX(scaledWidth);
            }

            if (DEBUG) {
                Log.d(TAG, String.format("percent=%s,txY=%s,alpha=%s,scalePercent=%s,scaledWidth=%s,scaledHeight=%s",
                        percent, txY, mFade.getAlpha(), scalePercent, scaledWidth, scaledHeight));
            }
        }
    }
}
