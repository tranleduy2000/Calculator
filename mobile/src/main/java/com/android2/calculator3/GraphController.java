package com.android2.calculator3;

import android.os.AsyncTask;
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
import java.util.TreeMap;

public class GraphController implements
        OnGraphUpdatedListener, PanListener, ZoomListener {
    private static final int MAX_CACHE_SIZE = 10;

    private final Set<GraphView> mGraphViews = new HashSet<>();
    private final GraphModule mGraphModule;
    private final GraphView mMainGraphView;

    private String mEquation;
    private List<Point> mPendingResults;

    private boolean mLocked;
    private OnUnlockedListener mOnUnlockedListener;

    private final Map<String, List<Point>> mCachedEquations = new LinkedHashMap<String, List<Point>>(MAX_CACHE_SIZE, 1f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Point>> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public GraphController(GraphModule module, GraphView view) {
        mGraphModule = module;
        mMainGraphView = view;
        addGraphView(view);
        mMainGraphView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (mEquation != null) {
                    startGraph(mEquation);
                }
            }
        });
    }

    public void addGraphView(GraphView view) {
        mGraphViews.add(view);
        view.setPanListener(this);
        view.setZoomListener(this);
    }

    public AsyncTask startGraph(final String equation) {
        // If we've already asked this before, quick quick show the result again
        if (mCachedEquations.containsKey(equation)) {
            GraphController.this.onGraphUpdated(mCachedEquations.get(equation));
        }

        invalidateModule();
        mEquation = equation;
        return mGraphModule.updateGraph(equation, new OnGraphUpdatedListener() {
            @Override
            public void onGraphUpdated(List<Point> result) {
                mCachedEquations.put(equation, result);
                GraphController.this.onGraphUpdated(result);
            }
        });
    }

    public void clearGraph() {
        for (GraphView view : mGraphViews) {
            view.setData(new ArrayList<Point>());
            view.invalidate();
        }
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
            for (GraphView view : mGraphViews) {
                view.setData(result);
                view.invalidate();
            }
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
