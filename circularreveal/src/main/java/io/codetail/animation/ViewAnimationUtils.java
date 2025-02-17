package io.codetail.animation;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.view.ViewHelper;
import com.nineoldandroids.view.ViewPropertyAnimator;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

public class ViewAnimationUtils {

    public static final int SCALE_UP_DURATION = 500;
    private final static boolean LOLLIPOP_PLUS = true;

    /**
     * Returns an Animator which can animate a clipping circle.
     * <p>
     * Any shadow cast by the View will respect the circular clip from this animator.
     * <p>
     * Only a single non-rectangular clip can be applied on a View at any time.
     * Views clipped by a circular reveal animation take priority over
     * {@link android.view.View#setClipToOutline(boolean) View Outline clipping}.
     * <p>
     * Note that the animation returned here is a one-shot animation. It cannot
     * be re-used, and once started it cannot be paused or resumed.
     *
     * @param view        The View will be clipped to the animating circle.
     * @param centerX     The x coordinate of the center of the animating circle.
     * @param centerY     The y coordinate of the center of the animating circle.
     * @param startRadius The starting radius of the animating circle.
     * @param endRadius   The ending radius of the animating circle.
     */
    public static SupportAnimator createCircularReveal(View view,
                                                       int centerX, int centerY,
                                                       float startRadius, float endRadius) {

        return new SupportAnimatorLollipop(android.view.ViewAnimationUtils
                .createCircularReveal(view, centerX, centerY, startRadius, endRadius));

    }


    static Animator.AnimatorListener getRevealFinishListener(RevealAnimator target, Rect bounds) {
        return new RevealAnimator.RevealFinishedJellyBeanMr2(target, bounds);
    }


    /**
     * Lifting view
     *
     * @param view         The animation target
     * @param baseRotation initial Rotation X in 3D space
     * @param fromY        initial Y position of view
     * @param duration     aniamtion duration
     * @param startDelay   start delay before animation begin
     */
    public static void liftingFromBottom(View view, float baseRotation, float fromY, int duration, int startDelay) {
        ViewHelper.setRotationX(view, baseRotation);
        ViewHelper.setTranslationY(view, fromY);

        ViewPropertyAnimator
                .animate(view)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(duration)
                .setStartDelay(startDelay)
                .rotationX(0)
                .translationY(0)
                .start();

    }

    /**
     * Lifting view
     *
     * @param view         The animation target
     * @param baseRotation initial Rotation X in 3D space
     * @param duration     aniamtion duration
     * @param startDelay   start delay before animation begin
     */
    public static void liftingFromBottom(View view, float baseRotation, int duration, int startDelay) {
        ViewHelper.setRotationX(view, baseRotation);
        ViewHelper.setTranslationY(view, view.getHeight() / 3);

        ViewPropertyAnimator
                .animate(view)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(duration)
                .setStartDelay(startDelay)
                .rotationX(0)
                .translationY(0)
                .start();

    }

    /**
     * Lifting view
     *
     * @param view         The animation target
     * @param baseRotation initial Rotation X in 3D space
     * @param duration     aniamtion duration
     */
    public static void liftingFromBottom(View view, float baseRotation, int duration) {
        ViewHelper.setRotationX(view, baseRotation);
        ViewHelper.setTranslationY(view, view.getHeight() / 3);

        ViewPropertyAnimator
                .animate(view)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(duration)
                .rotationX(0)
                .translationY(0)
                .start();

    }

    public static class SimpleAnimationListener implements Animator.AnimatorListener {

        @Override
        public void onAnimationStart(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {

        }

        @Override
        public void onAnimationCancel(Animator animation) {

        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    }

}
