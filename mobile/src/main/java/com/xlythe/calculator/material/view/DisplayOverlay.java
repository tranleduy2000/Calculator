package com.xlythe.calculator.material.view;

import android.animation.Animator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewConfiguration;
import android.widget.RelativeLayout;

/**
 * The display overlay is a container that intercepts touch events on top of:
 * 1. the display, i.e. the formula and result views
 * 2. the history view, which is revealed by dragging down on the display
 * <p>
 * This overlay passes vertical scrolling events down to the history recycler view
 * when applicable.  If the user attempts to scroll up and the recycler is already
 * scrolled all the way up, then we intercept the event and collapse the history.
 */
public class DisplayOverlay extends RelativeLayout {

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


    private void setup() {
        ViewConfiguration vc = ViewConfiguration.get(getContext());
    }

    public void collapse() {
        collapse(null);
    }

    public void collapse(Animator.AnimatorListener listener) {

    }

    public boolean isExpanded() {
        return false;
    }

    public void scrollToMostRecent() {
    }

    public enum TranslateState {
        EXPANDED, COLLAPSED
    }


}
