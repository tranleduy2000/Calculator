package com.android2.calculator3.view;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.util.StateSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A list of drawables. Shows the first drawable to match the state set given.
 * If an animation is defined, will play the animation during a state change.
 */
public class AnimatedStateListDrawable3 extends Drawable {
    private StaticState mCurrentState;
    private Drawable mCurrentTransition;
    private List<StaticState> mStates = new ArrayList<>();

    /**
     * Add a drawable state. This will appear if it matches the state set.
     * */
    public void addState(int[] stateSet, Drawable drawable) {
        mStates.add(new StaticState(stateSet, drawable));
        setState(getState());
    }

    /**
     * Remove a drawable state. This will remove the first drawable that has the exact same state set.
     * */
    public void removeState(int[] stateSet) {
        mStates.remove(new State(stateSet, null));
    }

    /**
     * Define a transition. Whenever switching between the from state and the to state, this animation will play.
     * */
    public <T extends Drawable & Animatable> void addTransition(int[] fromStateSet, int[] toStateSet, T drawable) {
        for(StaticState s : mStates) {
            if(s.matches(fromStateSet)) {
                s.addTransition(new TransitionState(toStateSet, drawable));
            }
        }
    }

    @Override
    protected boolean onStateChange(int[] toStateSet) {
        if(mCurrentState == null) {
            // We haven't set an initial state yet. Lets do that first.
            mCurrentState = getState(toStateSet);
        }

        if(mCurrentState.matches(toStateSet)) {
            // We're still on the right state. Pass the information down.
            return mCurrentState.getDrawable().setState(toStateSet);
        }

        // Update the transition (if there is one)
        mCurrentTransition = mCurrentState.getTransition(toStateSet);
        if(mCurrentTransition != null) {
            mCurrentTransition.setState(toStateSet);
        }

        // Update the state
        mCurrentState = getState(toStateSet);
        mCurrentState.getDrawable().setState(toStateSet);

        return true;
    }

    private StaticState getState(int[] stateSet) {
        for(StaticState s : mStates) {
            if(s.matches(stateSet)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public Drawable getCurrent() {
        return mCurrentTransition == null ? mCurrentState.getDrawable() : mCurrentTransition;
    }

    @Override
    public int getOpacity() {
        return getCurrent().getOpacity();
    }

    @Override
    public void draw(Canvas canvas) {
        final Drawable d = getCurrent();
        if (d instanceof AnimationDrawable) {
            ((AnimationDrawable) d).draw(canvas);
        } else if (d instanceof AnimatedVectorDrawable) {
//            final boolean reversed = state.isTransitionReversed(fromId, toId);
//            transition = new AnimatedVectorDrawableTransition((AnimatedVectorDrawable) d, reversed);
//        } else if (d instanceof Animatable) {
//            transition = new AnimatableTransition((Animatable) d);
//        } else {
//            // We don't know how to animate this transition.
//            return false;
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        for(StaticState ss : mStates) {
            ss.getDrawable().setColorFilter(cf);
            for(State s : ss.transitions) {
                s.getDrawable().setColorFilter(cf);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        for(StaticState ss : mStates) {
            ss.getDrawable().setAlpha(alpha);
            for(State s : ss.transitions) {
                s.getDrawable().setAlpha(alpha);
            }
        }
    }

    protected static class State {
        private final int[] stateSet;
        private final Drawable drawable;

        protected State(int[] stateSet, Drawable drawable) {
            this.stateSet = stateSet;
            this.drawable = drawable;
        }

        public int[] getStateSet() {
            return stateSet;
        }

        public Drawable getDrawable() {
            return drawable;
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof State) {
                State state = (State) o;
                if(state.stateSet.length == stateSet.length) {
                    loop: for (int i : state.stateSet) {
                        for (int j : stateSet) {
                            if (i == j) continue loop;
                        }
                        return false;
                    }
                    return true;
                }
            }
            return false;
        }

        public boolean matches(int[] stateSet) {
            return StateSet.stateSetMatches(this.stateSet, stateSet);
        }
    }

    protected static class StaticState extends State {
        private final Set<State> transitions = new HashSet<State>();

        protected StaticState(int[] stateSet, Drawable drawable) {
            super(stateSet, drawable);
        }

        public void addTransition(TransitionState state) {
            transitions.add(state);
        }

        public Drawable getTransition(int[] stateSet) {
            for(State s : transitions) {
                if(s.matches(stateSet)) {
                    return s.drawable;
                }
            }
            return null;
        }

        public boolean hasTransition(int[] stateSet) {
            return getTransition(stateSet) != null;
        }

    }

    protected static class TransitionState extends State {

        <T extends Drawable & Animatable> TransitionState(int[] stateSet, T drawable) {
            super(stateSet, drawable);
        }

    }
}
