///*
//* Copyright (C) 2014 The Android Open Source Project
//*
//* Licensed under the Apache License, Version 2.0 (the "License");
//* you may not use this file except in compliance with the License.
//* You may obtain a copy of the License at
//*
//*      http://www.apache.org/licenses/LICENSE-2.0
//*
//* Unless required by applicable law or agreed to in writing, software
//* distributed under the License is distributed on an "AS IS" BASIS,
//* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//* See the License for the specific language governing permissions and
//* limitations under the License.
//*/
//
//package com.android2.calculator3.view;
//
//import android.animation.ObjectAnimator;
//import android.animation.TimeInterpolator;
//import android.content.res.Resources;
//import android.graphics.drawable.Animatable;
//import android.graphics.drawable.AnimatedVectorDrawable;
//import android.graphics.drawable.AnimationDrawable;
//import android.graphics.drawable.Drawable;
//import android.graphics.drawable.StateListDrawable;
//import android.support.annotation.NonNull;
//import android.support.annotation.Nullable;
//import android.util.SparseIntArray;
//import android.util.StateSet;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
///**
//* Drawable containing a set of Drawable keyframes where the currently displayed
//* keyframe is chosen based on the current state set. Animations between
//* keyframes may optionally be defined using transition elements.
//*/
//public class AnimatedStateListDrawable extends StateListDrawable {
//
//    /** The currently running transition, if any. */
//    private Transition mTransition;
//
//    /** Index to be set after the transition ends. */
//    private int mTransitionToIndex = -1;
//
//    /** Index away from which we are transitioning. */
//    private int mTransitionFromIndex = -1;
//
//    private State mCurrentState = null;
//
//    private List<State> mStates = new ArrayList<>();
//
//    private List<TransitionState> mTransitionStates = new ArrayList<>();
//
//    @Override
//    public boolean setVisible(boolean visible, boolean restart) {
//        final boolean changed = super.setVisible(visible, restart);
//
//        if (mTransition != null && (changed || restart)) {
//            if (visible) {
//                mTransition.start();
//            } else {
//                // Ensure we're showing the correct state when visible.
//                jumpToCurrentState();
//            }
//        }
//
//        return changed;
//    }
//
//    /**
//     * Add a new drawable to the set of keyframes.
//     *
//     * @param stateSet An array of resource IDs to associate with the keyframe
//     * @param drawable The drawable to show when in the specified state, may not be null
//     * @param id The unique identifier for the keyframe
//     */
//    public void addState(@NonNull int[] stateSet, @NonNull Drawable drawable, int id) {
//        if (drawable == null) {
//            throw new IllegalArgumentException("Drawable must not be null");
//        }
//
//        // Remove existing states with that id
//        int prevState = getIndexOf(id);
//        if(prevState >= 0) {
//            mStates.remove(prevState);
//
//        // Create a new state
//        mStates.add(new State(stateSet, drawable, id));
//
//        final int index = super.addState(stateSet, drawable);
//            mStateIds.put(index, id);
//            return index;
//        }
//
//        addState(stateSet, drawable);
//    }
//
//    /**
//     * Adds a new transition between keyframes.
//     *
//     * @param fromId Unique identifier of the starting keyframe
//     * @param toId Unique identifier of the ending keyframe
//     * @param transition An {@link Animatable} drawable to use as a transition, may not be null
//     * @param reversible Whether the transition can be reversed
//     */
//    public <T extends Drawable & Animatable> void addTransition(int fromId, int toId,
//                                                                @NonNull T transition, boolean reversible) {
//        if (transition == null) {
//            throw new IllegalArgumentException("Transition drawable must not be null");
//        }
//
//        mTransitionStates.add(new TransitionState(fromId, toId, transition, reversible));
//    }
//
//    @Override
//    public boolean isStateful() {
//        return true;
//    }
//
//    @Override
//    protected boolean onStateChange(int[] stateSet) {
//        android.graphics.drawable.AnimatedStateListDrawable
//        final State state = getState(stateSet);
//        if (state == getCurrentState()) {
//            // Propagate state change to current drawable.
//            final Drawable current = getCurrent();
//            if (current != null) {
//                return current.setState(stateSet);
//            }
//            return false;
//        }
//
//        // Attempt to find a valid transition to the keyframe.
//        if (selectTransition(state.id)) {
//            return true;
//        }
//
//        // No valid transition, attempt to jump directly to the keyframe.
//        if (selectDrawable(state.id)) {
//            return true;
//        }
//
//        return super.onStateChange(stateSet);
//    }
//
//    private State getCurrentState() {
//        return mCurrentState;
//    }
//
//    private State getState(int[] stateSet) {
//        for(State s : mStates) {
//            if(StateSet.stateSetMatches(s.stateSet, stateSet)) {
//                return s;
//            }
//        }
//        return null;
//    }
//
//    private int getIndexOf(int id) {
//        for(int i=0; i<mStates.size(); i++) {
//            if(mStates.get(i).id == id) {
//                return i;
//            }
//        }
//
//        return -1;
//    }
//
//    private boolean selectTransition(int toIndex) {
//        final int fromIndex;
//        final Transition currentTransition = mTransition;
//        if (currentTransition != null) {
//            if (toIndex == mTransitionToIndex) {
//                // Already animating to that keyframe.
//                return true;
//            } else if (toIndex == mTransitionFromIndex && currentTransition.canReverse()) {
//                // Reverse the current animation.
//                currentTransition.reverse();
//                mTransitionToIndex = mTransitionFromIndex;
//                mTransitionFromIndex = toIndex;
//                return true;
//            }
//
//            // Start the next transition from the end of the current one.
//            fromIndex = mTransitionToIndex;
//
//            // Changing animation, end the current animation.
//            currentTransition.stop();
//        } else {
//            fromIndex = getCurrentState().id;
//        }
//
//        // Reset state.
//        mTransition = null;
//        mTransitionFromIndex = -1;
//        mTransitionToIndex = -1;
//
//        final int fromId = state.getKeyframeIdAt(fromIndex);
//        final int toId = state.getKeyframeIdAt(toIndex);
//        if (toId == 0 || fromId == 0) {
//            // Missing a keyframe ID.
//            return false;
//        }
//
//        final int transitionIndex = state.indexOfTransition(fromId, toId);
//        if (transitionIndex < 0) {
//            // Couldn't select a transition.
//            return false;
//        }
//
//        // This may fail if we're already on the transition, but that's okay!
//        selectDrawable(transitionIndex);
//
//        final Transition transition;
//        final Drawable d = getCurrent();
//        if (d instanceof AnimationDrawable) {
//            final boolean reversed = state.isTransitionReversed(fromId, toId);
//            transition = new AnimationDrawableTransition((AnimationDrawable) d, reversed);
//        } else if (d instanceof AnimatedVectorDrawable) {
//            transition = new AnimatedVectorDrawableTransition((AnimatedVectorDrawable) d);
//        } else if (d instanceof Animatable) {
//            transition = new AnimatableTransition((Animatable) d);
//        } else {
//            // We don't know how to animate this transition.
//            return false;
//        }
//
//        transition.start();
//
//        mTransition = transition;
//        mTransitionFromIndex = fromIndex;
//        mTransitionToIndex = toIndex;
//        return true;
//    }
//
//    private static abstract class Transition {
//        public abstract void start();
//        public abstract void stop();
//
//        public void reverse() {
//            // Not supported by default.
//        }
//
//        public boolean canReverse() {
//            return false;
//        }
//    }
//
//    private static class AnimatableTransition  extends Transition {
//        private final Animatable mA;
//
//        public AnimatableTransition(Animatable a) {
//            mA = a;
//        }
//
//        @Override
//        public void start() {
//            mA.start();
//        }
//
//        @Override
//        public void stop() {
//            mA.stop();
//        }
//    }
//
//
//    private static class AnimationDrawableTransition  extends Transition {
//        private final ObjectAnimator mAnim;
//
//        public AnimationDrawableTransition(AnimationDrawable ad, boolean reversed) {
//            final int frameCount = ad.getNumberOfFrames();
//            final int fromFrame = reversed ? frameCount - 1 : 0;
//            final int toFrame = reversed ? 0 : frameCount - 1;
//            final FrameInterpolator interp = new FrameInterpolator(ad, reversed);
//            final ObjectAnimator anim = ObjectAnimator.ofInt(ad, "currentIndex", fromFrame, toFrame);
//            anim.setAutoCancel(true);
//            anim.setDuration(interp.getTotalDuration());
//            anim.setInterpolator(interp);
//
//            mAnim = anim;
//        }
//
//        @Override
//        public boolean canReverse() {
//            return true;
//        }
//
//        @Override
//        public void start() {
//            mAnim.start();
//        }
//
//        @Override
//        public void reverse() {
//            mAnim.reverse();
//        }
//
//        @Override
//        public void stop() {
//            mAnim.cancel();
//        }
//    }
//
//    private static class AnimatedVectorDrawableTransition  extends Transition {
//        private final AnimatedVectorDrawable mAvd;
//
//        public AnimatedVectorDrawableTransition(AnimatedVectorDrawable avd) {
//            mAvd = avd;
//        }
//
//        @Override
//        public void start() {
//            mAvd.start();
//        }
//
//        @Override
//        public void stop() {
//            mAvd.stop();
//        }
//    }
//
//
//    @Override
//    public void jumpToCurrentState() {
//        super.jumpToCurrentState();
//
//        if (mTransition != null) {
//            mTransition.stop();
//            mTransition = null;
//
//            selectDrawable(mTransitionToIndex);
//            mTransitionToIndex = -1;
//            mTransitionFromIndex = -1;
//        }
//    }
//
//    /**
//     * Interpolates between frames with respect to their individual durations.
//     */
//    private static class FrameInterpolator implements TimeInterpolator {
//        private int[] mFrameTimes;
//        private int mFrames;
//        private int mTotalDuration;
//
//        public FrameInterpolator(AnimationDrawable d, boolean reversed) {
//            updateFrames(d, reversed);
//        }
//
//        public int updateFrames(AnimationDrawable d, boolean reversed) {
//            final int N = d.getNumberOfFrames();
//            mFrames = N;
//
//            if (mFrameTimes == null || mFrameTimes.length < N) {
//                mFrameTimes = new int[N];
//            }
//
//            final int[] frameTimes = mFrameTimes;
//            int totalDuration = 0;
//            for (int i = 0; i < N; i++) {
//                final int duration = d.getDuration(reversed ? N - i - 1 : i);
//                frameTimes[i] = duration;
//                totalDuration += duration;
//            }
//
//            mTotalDuration = totalDuration;
//            return totalDuration;
//        }
//
//        public int getTotalDuration() {
//            return mTotalDuration;
//        }
//
//        @Override
//        public float getInterpolation(float input) {
//            final int elapsed = (int) (input * mTotalDuration + 0.5f);
//            final int N = mFrames;
//            final int[] frameTimes = mFrameTimes;
//
//            // Find the current frame and remaining time within that frame.
//            int remaining = elapsed;
//            int i = 0;
//            while (i < N && remaining >= frameTimes[i]) {
//                remaining -= frameTimes[i];
//                i++;
//            }
//
//            // Remaining time is relative of total duration.
//            final float frameElapsed;
//            if (i < N) {
//                frameElapsed = remaining / (float) mTotalDuration;
//            } else {
//                frameElapsed = 0;
//            }
//
//            return i / (float) N + frameElapsed;
//        }
//    }
//
//    private static class State {
//        private final int[] stateSet;
//        private final Drawable drawable;
//        private final int id;
//
//        State(int[] stateSet, Drawable drawable, int id) {
//            this.stateSet = stateSet;
//            this.drawable = drawable;
//            this.id = id;
//        }
//
//    }
//
//    private static class TransitionState {
//        private final int fromId;
//        private final int toId;
//        private final Drawable transition;
//        private final boolean reversible;
//
//        TransitionState(int fromId, int toId, Drawable transition, boolean reversible) {
//            this.fromId = fromId;
//            this.toId = toId;
//            this.transition = transition;
//            this.reversible = reversible;
//        }
//    }
//}
