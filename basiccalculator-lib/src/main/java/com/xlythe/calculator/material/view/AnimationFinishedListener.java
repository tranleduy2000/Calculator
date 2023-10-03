package com.xlythe.calculator.material.view;

import android.animation.Animator;

import androidx.annotation.NonNull;

public abstract class AnimationFinishedListener implements Animator.AnimatorListener {
    @Override
    public void onAnimationStart(@NonNull Animator animation, boolean isReverse) {
    }

    @Override
    public void onAnimationEnd(@NonNull Animator animation, boolean isReverse) {
        onAnimationFinished();
    }

    @Override
    public void onAnimationStart(@NonNull Animator animation) {
        onAnimationFinished();
    }

    @Override
    public void onAnimationEnd(@NonNull Animator animation) {

    }

    @Override
    public void onAnimationCancel(@NonNull Animator animation) {

    }

    @Override
    public void onAnimationRepeat(@NonNull Animator animation) {

    }

    public abstract void onAnimationFinished();
}
