package com.android2.calculator3;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.ViewTreeObserver;

import com.android2.calculator3.view.GraphView;
import com.android2.calculator3.view.GraphView.PanListener;
import com.android2.calculator3.view.GraphView.ZoomListener;
import com.xlythe.math.GraphModule;
import com.xlythe.math.GraphModule.OnGraphUpdatedListener;
import com.xlythe.math.Point;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphController implements
        OnGraphUpdatedListener, PanListener, ZoomListener {
    private static final String TAG = GraphController.class.getSimpleName();
    private static final int MAX_CACHE_SIZE = 10;

    private final GraphModule mGraphModule;
    private final GraphView mMainGraphView;

    private String mEquation;
    private List<Point> mPendingResults;

    private boolean mLocked;
    private OnUnlockedListener mOnUnlockedListener;

    private final Handler mHandler = new Handler();

    private static final Map<String, List<Point>> mCachedEquations = new LinkedHashMap<String, List<Point>>(MAX_CACHE_SIZE, 1f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Point>> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public GraphController(GraphModule module, GraphView view) {
        mGraphModule = module;
        mMainGraphView = view;

        mMainGraphView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (android.os.Build.VERSION.SDK_INT < 16) {
                    mMainGraphView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    mMainGraphView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                if (mEquation != null) {
                    Log.d(TAG, "View was laid out. Attempting to graph " + mEquation);
                    startGraph(mEquation);
                }
            }
        });
    }

    public AsyncTask startGraph(final String equation) {
        // If we've already asked this before, quick quick show the result again
        if (mCachedEquations.containsKey(equation)) {
            onGraphUpdated(mCachedEquations.get(equation));
        }

        mEquation = equation;
        if (mMainGraphView.getXAxisMin() == mMainGraphView.getXAxisMax()) {
            Log.d(TAG, "This view hasn't been laid out yet. Will delay graphing " + equation);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mMainGraphView.getXAxisMin() != mMainGraphView.getXAxisMax()) {
                        startGraph(equation);
                    }
                }
            });
            return null;
        }

        invalidateModule();
        return mGraphModule.updateGraph(equation, new OnGraphUpdatedListener() {
            @Override
            public void onGraphUpdated(List<Point> result) {
                mCachedEquations.put(equation, result);
                GraphController.this.onGraphUpdated(result);
            }
        });
    }

    public void clearGraph() {
        mMainGraphView.setData(new ArrayList<Point>());
    }

    private void invalidateModule() {
        mGraphModule.setDomain(mMainGraphView.getXAxisMin(), mMainGraphView.getXAxisMax());
        mGraphModule.setRange(mMainGraphView.getYAxisMin(), mMainGraphView.getYAxisMax());
        mGraphModule.setZoomLevel(mMainGraphView.getZoomLevel());
    }

    @Override
    public void onGraphUpdated(List<Point> result) {
        if (isLocked()) {
            mPendingResults = result;
        } else {
            mMainGraphView.setData(result);
        }
    }

    @Override
    public void panApplied() {
        invalidateGraph();
    }

    @Override
    public void zoomApplied(float level) {
        invalidateGraph();
    }

    private void invalidateGraph() {
        invalidateModule();
        if (mEquation != null) {
            mGraphModule.updateGraph(mEquation, this);
        }
    }

    public void lock() {
        mLocked = true;
    }

    public void unlock() {
        mLocked = false;
        if (mOnUnlockedListener != null) {
            mOnUnlockedListener.onUnlocked();
        }
        if (mPendingResults != null) {
            onGraphUpdated(mPendingResults);
            mPendingResults = null;
        }
    }

    public boolean isLocked() {
        return mLocked;
    }

    public void setOnUnlockedListener(OnUnlockedListener listener) {
        mOnUnlockedListener = listener;
    }

    public interface OnUnlockedListener {
        void onUnlocked();
    }
}
