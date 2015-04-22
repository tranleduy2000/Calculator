package com.android2.calculator3;

import android.view.View;
import android.view.ViewTreeObserver;

import com.android2.calculator3.view.GraphView;
import com.android2.calculator3.view.GraphView.PanListener;
import com.android2.calculator3.view.GraphView.ZoomListener;
import com.xlythe.math.GraphModule;
import com.xlythe.math.GraphModule.OnGraphUpdatedListener;
import com.xlythe.math.Point;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GraphController implements
        OnGraphUpdatedListener, PanListener, ZoomListener,
        View.OnClickListener {

    private final Set<GraphView> mGraphViews = new HashSet<>();
    private final GraphModule mGraphModule;
    private final GraphView mMainGraphView;

    private String mEquation;

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

    private void resetState() {
        mEquation = null;
        for (GraphView view : mGraphViews) {
            view.zoomReset();
        }
    }

    public void startGraph(String equation) {
        invalidateModule();
        mEquation = equation;
        mGraphModule.updateGraph(equation, this);
    }

    private void invalidateModule() {
        mGraphModule.setDomain(mMainGraphView.getXAxisMin(), mMainGraphView.getXAxisMax());
        mGraphModule.setRange(mMainGraphView.getYAxisMin(), mMainGraphView.getYAxisMax());
        mGraphModule.setZoomLevel(mMainGraphView.getZoomLevel());
    }

    public void exitGraphMode() {
        resetState();
    }

    @Override
    public void onGraphUpdated(List<Point> result) {
        for (GraphView view : mGraphViews) {
            view.setData(result);
            view.invalidate();
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

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.exitGraph:
                exitGraphMode();
                break;
            case R.id.minusZoom:
                for (GraphView view : mGraphViews) {
                    view.zoomOut();
                }
                break;
            case R.id.plusZoom:
                for (GraphView view : mGraphViews) {
                    view.zoomIn();
                }
                break;
            case R.id.resetZoom:
                for (GraphView view : mGraphViews) {
                    view.zoomReset();
                }
                break;
        }
    }

    private void invalidateGraph() {
        invalidateModule();
        if (mEquation != null) {
            mGraphModule.updateGraph(mEquation, this);
        }
    }
}
