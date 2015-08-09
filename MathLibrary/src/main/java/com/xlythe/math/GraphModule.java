package com.xlythe.math;

import android.os.AsyncTask;

import org.javia.arity.SyntaxException;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class GraphModule extends Module {
    private float mMinY;
    private float mMaxY;
    private float mMinX;
    private float mMaxX;
    private float mZoomLevel;

    public GraphModule(Solver solver) {
        super(solver);
    }

    public void setRange(float min, float max) {
        mMinY = min;
        mMaxY = max;
    }

    public void setDomain(float min, float max) {
        mMinX = min;
        mMaxX = max;
    }

    public void setZoomLevel(float level) {
        mZoomLevel = level;
    }

    /**
     * Given a function, updateGraph will attempt to build a list of points that can be graphed.
     * */
    public AsyncTask updateGraph(String text, OnGraphUpdatedListener l) {
        boolean endsWithOperator = text.length() != 0 &&
                (Solver.isOperator(text.charAt(text.length() - 1)) || text.endsWith("("));
        boolean containsMatrices = getSolver().displayContainsMatrices(text);
        if(endsWithOperator || containsMatrices) {
            return null;
        }

        GraphTask newTask = new GraphTask(getSolver(), mMinY, mMaxY, mMinX, mMaxX, mZoomLevel, l);
        newTask.execute(text);
        return newTask;
    }

    class GraphTask extends AsyncTask<String, String, List<Point>> {
        private final Solver mSolver;
        private final OnGraphUpdatedListener mListener;
        private final float mMinY;
        private final float mMaxY;
        private final float mMinX;
        private final float mMaxX;
        private final float mZoomLevel;

        public GraphTask(Solver solver, float minY, float maxY, float minX, float maxX,
                         float zoomLevel, OnGraphUpdatedListener l) {
            mSolver = solver;
            mListener = l;
            mMinY = minY;
            mMaxY = maxY;
            mMinX = minX;
            mMaxX = maxX;
            mZoomLevel = zoomLevel;
        }

        @Override
        protected List<Point> doInBackground(String... eq) {
            try {
                return graph(mSolver.getBaseModule().changeBase(eq[0],
                        mSolver.getBaseModule().getBase(), Base.DECIMAL));
            } catch(SyntaxException e) {
                cancel(true);
                return null;
            }
        }

        public List<Point> graph(String equation) {
            final LinkedList<Point> series = new LinkedList<Point>();
            mSolver.pushFrame();

            final float delta = 0.1f * mZoomLevel;
            float previousPoint;
            for(float x = mMinX; x <= mMaxX; x += delta) {
                if(isCancelled()) {
                    return null;
                }

                try {
                    mSolver.define("X", x);
                    float y = (float) mSolver.eval(equation);
                    series.add(new Point(x, y));
                } catch(SyntaxException e) {}
            }
            mSolver.popFrame();

            return Collections.unmodifiableList(series);
        }

        @Override
        protected void onPostExecute(List<Point> result) {
            mListener.onGraphUpdated(result);
        }
    }

    public static interface OnGraphUpdatedListener {
        public void onGraphUpdated(List<Point> result);
    }
}
