package com.android2.calculator3.view;

import android.animation.Animator;
import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
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
import android.view.animation.Animation;
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

    private static boolean DEBUG = false;
    private static final String TAG = "DisplayOverlay";

    private RecyclerView mRecyclerView;
    private View mMainDisplay;
    private LinearLayoutManager mLayoutManager;
    private float mInitialMotionY;
    private float mLastMotionY;
    private float mLastDeltaY;
    private int mTouchSlop;
    private int mMinTranslation = -1;
    private int mMaxTranslation = -1;
    private VelocityTracker mVelocityTracker;
    private float mMinVelocity = -1;
    private View mFade;
    private final OnTouchListener mFadeOnTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            collapse();
            return true;
        }
    };

    /**
     * Reports when state changes to expanded or collapsed (partial is ignored)
     */
    public interface TranslateStateListener {
        void onTranslateStateChanged(TranslateState newState);
    }

    private TranslateStateListener mTranslateStateListener;

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
        initVelocityTrackerIfNotExists();
        mVelocityTracker.addMovement(event);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                evaluateHeight();
                break;
            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                break;
            case MotionEvent.ACTION_UP:
                handleUp(event);
                recycleVelocityTracker();
                break;
            case MotionEvent.ACTION_CANCEL:
                recycleVelocityTracker();
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

        if (dy < 0 && state != TranslateState.COLLAPSED) {
            updateTranslation(dy);
        } else if (dy > 0 && state != TranslateState.EXPANDED) {
            updateTranslation(dy);
        }
        mLastMotionY = y;
        mLastDeltaY = dy;
    }

    private void handleUp(MotionEvent event) {
        mVelocityTracker.computeCurrentVelocity(1);
        float yvel = mVelocityTracker.getYVelocity();
        if (DEBUG) {
            Log.v(TAG, "handleUp yvel=" + yvel + ", mLastDeltaY=" + mLastDeltaY);
        }

        TranslateState curState = getTranslateState();
        if (curState != TranslateState.PARTIAL) {
            // already settled
            if (mTranslateStateListener != null) {
                mTranslateStateListener.onTranslateStateChanged(curState);
            }
        } else {
            // the sign on velocity seems unreliable, so use last delta to determine direction
            if (mLastDeltaY > 0) {
                expand();
            } else {
                collapse();
            }
        }
    }

    public void expand() {
        expand(null);
    }

    public void expand(Animator.AnimatorListener listener) {
        settleAt(mMaxTranslation, MAX_ALPHA, listener);
        if (mFade != null) {
            mFade.setOnTouchListener(mFadeOnTouchListener);
        }
    }

    public void collapse() {
        collapse(null);
    }

    public void collapse(Animator.AnimatorListener listener) {
        settleAt(mMinTranslation, 0, listener);
        if (mFade != null) {
            mFade.setOnTouchListener(null);
        }
    }

    /**
     * Smoothly translates the display overlay to the given target
     *
     * @param destTx target translation
     * @param alpha background alpha
     */
    private void settleAt(float destTx, float alpha) {
        settleAt(destTx, alpha, null);
    }

    /**
     * Smoothly translates the display overlay to the given target
     *
     * @param destTx target translation
     * @param alpha background alpha
     * @param listener listener for the end of the animation
     */
    private void settleAt(float destTx, float alpha, final Animator.AnimatorListener listener) {
        animate().translationY(destTx).setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (listener != null) {
                    listener.onAnimationStart(animation);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mTranslateStateListener != null) {
                    mTranslateStateListener.onTranslateStateChanged(getTranslateState());
                }
                if (listener != null) {
                    listener.onAnimationEnd(animation);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (listener != null) {
                    listener.onAnimationCancel(animation);
                }
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                if (listener != null) {
                    listener.onAnimationRepeat(animation);
                }
            }
        }).start();
        if (mFade != null) {
            mFade.animate().alpha(alpha).start();
        }
    }

    private void updateTranslation(float dy) {
        float txY = getTranslationY() + dy;

        float clampedY = Math.min(Math.max(txY, mMinTranslation), mMaxTranslation);
        setTranslationY(clampedY);

        if (mFade != null) {
            float range = mMaxTranslation - mMinTranslation;
            float percent = range == 0 ? 0 : 1 - ((mMaxTranslation - clampedY) / range);
            mFade.setAlpha(MAX_ALPHA * percent);

            if (mFade.getAlpha() > 0) {
                mFade.setOnTouchListener(mFadeOnTouchListener);
            } else {
                mFade.setOnTouchListener(null);
            }
        }
    }

    public boolean isExpanded() {
        return getTranslateState() == TranslateState.EXPANDED;
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

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
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

    public void setTranslateStateListener(TranslateStateListener listener) {
        mTranslateStateListener = listener;
    }

    public TranslateStateListener getTranslateStateListener() {
        return mTranslateStateListener;
    }

    public void setFade(View view) {
        mFade = view;
    }

    private boolean isInBounds(float x, float y, View v) {
        return y >= v.getTop() && y <= v.getBottom() &&
                x >= v.getLeft() && x <= v.getRight();
    }
}
