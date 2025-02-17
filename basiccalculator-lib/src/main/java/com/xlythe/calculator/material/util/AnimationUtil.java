package com.xlythe.calculator.material.util;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;

import androidx.annotation.NonNull;

/**
 * Utility for transition animations
 */
public class AnimationUtil {

    public static final int DEFAULT_FADE_DURATION = 200;
    public static final int DEFAULT_SHRINK_GROW_DURATION = 200;

    /**
     * Makes view visible and transitions alpha from 0 to 1.  Does nothing if view is
     * already visible.
     *
     */
    public static void fadeIn(View view, int duration) {
        if (view.getVisibility() == View.VISIBLE) {
            return;
        }
        view.setAlpha(0);
        view.setVisibility(View.VISIBLE);
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "alpha", 0, 1);
        anim.setDuration(duration);
        anim.start();
    }

    /**
     * Fade in with default duration
     *
     */
    public static void fadeIn(View view) {
        fadeIn(view, DEFAULT_FADE_DURATION);
    }

    /**
     * Transitions alpha from 1 to 0 and then sets visibility to gone
     *
     */
    public static void fadeOut(final View view, int duration) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "alpha", 1, 0);
        anim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {

            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                view.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {

            }

            @Override
            public void onAnimationRepeat(@NonNull Animator animation) {

            }
        });
        anim.setDuration(DEFAULT_FADE_DURATION);
        anim.start();
    }

    /**
     * Fade out with default duration
     *
     */
    public static void fadeOut(View view) {
        fadeOut(view, DEFAULT_FADE_DURATION);
    }

    /**
     * Shrink view1 and then grow view2
     *
     * @param view1    view to shrink
     * @param view2    view to grow
     * @param duration duration for each phase of the animation
     */
    public static void shrinkAndGrow(final View view1, final View view2, int duration) {
        ScaleAnimation shrinkAnim =
                new ScaleAnimation(1, 0, 1, 0, view1.getWidth() / 2, view1.getHeight() / 2);
        final ScaleAnimation growAnim =
                new ScaleAnimation(0, 1, 0, 1, view2.getWidth() / 2, view2.getHeight() / 2);
        shrinkAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view1.setVisibility(View.INVISIBLE);
                view2.setVisibility(View.VISIBLE);
                view2.startAnimation(growAnim);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        shrinkAnim.setDuration(duration);
        growAnim.setDuration(duration);
        view1.startAnimation(shrinkAnim);
    }

    /**
     * Shrink and grow with default duration
     *
     */
    public static void shrinkAndGrow(View view1, View view2) {
        shrinkAndGrow(view1, view2, DEFAULT_SHRINK_GROW_DURATION);
    }
}
