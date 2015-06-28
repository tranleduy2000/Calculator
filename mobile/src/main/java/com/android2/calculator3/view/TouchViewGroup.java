//package com.android2.calculator3.view;
//
//import android.animation.Animator;
//import android.animation.ValueAnimator;
//import android.content.Context;
//import android.graphics.Color;
//import android.support.v4.view.MotionEventCompat;
//import android.support.v7.widget.LinearLayoutManager;
//import android.support.v7.widget.RecyclerView;
//import android.util.AttributeSet;
//import android.util.Log;
//import android.view.MotionEvent;
//import android.view.VelocityTracker;
//import android.view.View;
//import android.view.ViewConfiguration;
//import android.view.ViewGroup;
//import android.view.ViewTreeObserver;
//import android.widget.AbsListView;
//import android.widget.FrameLayout;
//import android.widget.RelativeLayout;
//import android.widget.TextView;
//
//import com.android2.calculator3.HistoryAdapter;
//import com.android2.calculator3.R;
//import com.xlythe.floatingview.AnimationFinishedListener;
//
///**
// * The display overlay is a container that intercepts touch events on top of:
// *      1. the display, i.e. the formula and result views
// *      2. the history view, which is revealed by dragging down on the display
// *
// * This overlay passes vertical scrolling events down to the history recycler view
// * when applicable.  If the user attempts to scroll up and the recycler is already
// * scrolled all the way up, then we intercept the event and collapse the history.
// */
//public class TouchViewGroup extends RelativeLayout {
//    private static boolean DEBUG = false;
//    private static final String TAG = TouchViewGroup.class.getSimpleName();
//
//    private VelocityTracker mVelocityTracker;
//    private int mMinimumFlingVelocity;
//    private int mMaximumFlingVelocity;
//    private float mInitialMotion;
//    private float mLastMotion;
//    private float mLastDelta;
//    private Orientation mOrientation = Orientation.VERTICAL;
//
//    public enum Orientation {
//        VERTICAL, HORIZONTAL;
//    }
//
//    public TouchViewGroup(Context context) {
//        super(context);
//        setup();
//    }
//
//    public TouchViewGroup(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        setup();
//    }
//
//    public TouchViewGroup(Context context, AttributeSet attrs, int defStyleAttr) {
//        super(context, attrs, defStyleAttr);
//        setup();
//    }
//
//    public TouchViewGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
//        super(context, attrs, defStyleAttr, defStyleRes);
//        setup();
//    }
//
//    private void setup() {
//        ViewConfiguration vc = ViewConfiguration.get(getContext());
//        mMinimumFlingVelocity = vc.getScaledMinimumFlingVelocity();
//        mMaximumFlingVelocity = vc.getScaledMaximumFlingVelocity();
//    }
//
//    @Override
//    public void requestDisallowInterceptTouchEvent(boolean b) {
//        // Nope.
//    }
//
//    public enum TranslateState {
//        EXPANDED, COLLAPSED, PARTIAL
//    }
//
//    @Override
//    public boolean onInterceptTouchEvent(MotionEvent ev) {
//        int action = MotionEventCompat.getActionMasked(ev);
//        float pos;
//        if (mOrientation == Orientation.VERTICAL) {
//            pos = ev.getRawY();
//        } else {
//            pos = ev.getRawX();
//        }
//        boolean intercepted = false;
//        TranslateState state = getTranslateState();
//
//        switch (action) {
//            case MotionEvent.ACTION_DOWN:
//                mInitialMotion = pos;
//                mLastMotion = pos;
//                handleDown();
//                break;
//            case MotionEvent.ACTION_MOVE:
//                float delta = pos - mInitialMotion;
//                if (delta < 0) {
//                    intercepted = state != TranslateState.COLLAPSED && isCollapsingAllowed();
//                } else if (delta > 0) {
//                    intercepted = state != TranslateState.EXPANDED && isExpandingAllowed();
//                }
//                break;
//        }
//
//        return intercepted;
//    }
//
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        int action = MotionEventCompat.getActionMasked(event);
//
//        if (mVelocityTracker == null) {
//            mVelocityTracker = VelocityTracker.obtain();
//        }
//        mVelocityTracker.addMovement(event);
//
//        switch (action) {
//            case MotionEvent.ACTION_DOWN:
//                // Handled in intercept
//                break;
//            case MotionEvent.ACTION_MOVE:
//                handleMove(event);
//                break;
//            case MotionEvent.ACTION_UP:
//                handleUp(event);
//                break;
//        }
//
//        return true;
//    }
//
//    protected void handleDown() {
//    }
//
//    protected void handleMove(MotionEvent event) {
//    }
//
//    private void handleUp(MotionEvent event) {
//        mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
//        if (Math.abs(mVelocityTracker.getYVelocity()) > mMinimumFlingVelocity) {
//            // the sign on velocity seems unreliable, so use last delta to determine direction
//            if (mLastDelta > 0) {
//                expand();
//            } else {
//                collapse();
//            }
//        }
//        mVelocityTracker.recycle();
//        mVelocityTracker = null;
//    }
//
//    /**
//     * An animator that goes from 0 to 100%
//     **/
//    private class DisplayAnimator extends ValueAnimator {
//        public DisplayAnimator(float start, float end) {
//            super();
//            setFloatValues(start, end);
//            addUpdateListener(new AnimatorUpdateListener() {
//                @Override
//                public void onAnimationUpdate(ValueAnimator animation) {
//                    float percent = (float) animation.getAnimatedValue();
//                    onUpdate(percent);
//                }
//            });
//        }
//
//        public void onUpdate(float percent) {
//        }
//
//        float scale(float percent, float goal) {
//            return 1f - percent * (1f - goal);
//        }
//
//        int mixColors(float percent, int initColor, int goalColor) {
//            int a1 = Color.alpha(goalColor);
//            int r1 = Color.red(goalColor);
//            int g1 = Color.green(goalColor);
//            int b1 = Color.blue(goalColor);
//
//            int a2 = Color.alpha(initColor);
//            int r2 = Color.red(initColor);
//            int g2 = Color.green(initColor);
//            int b2 = Color.blue(initColor);
//
//            percent = Math.min(1, percent);
//            percent = Math.max(0, percent);
//            float a = a1 * percent + a2 * (1 - percent);
//            float r = r1 * percent + r2 * (1 - percent);
//            float g = g1 * percent + g2 * (1 - percent);
//            float b = b1 * percent + b2 * (1 - percent);
//
//            return Color.argb((int) a, (int) r, (int) g, (int) b);
//        }
//    }
//}
