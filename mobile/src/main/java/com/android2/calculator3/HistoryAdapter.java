/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android2.calculator3;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android2.calculator3.view.HistoryLine;
import com.xlythe.math.Constants;
import com.xlythe.math.EquationFormatter;
import com.xlythe.math.History;
import com.xlythe.math.HistoryEntry;
import com.xlythe.math.Solver;

import java.util.Vector;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private final Context mContext;
    private final Solver mSolver;
    private final Vector<HistoryEntry> mEntries;
    private final EquationFormatter mEquationFormatter;
    private final String mX;
    protected HistoryItemCallback mCallback;
    private HistoryEntry mDisplayEntry;

    public interface HistoryItemCallback {
        void onHistoryItemSelected(HistoryEntry entry);
    }

    public HistoryAdapter(Context context, Solver solver, History history, HistoryItemCallback callback) {
        mContext = context;
        mSolver = solver;
        mEntries = history.getEntries();
        mEquationFormatter = new EquationFormatter();
        mCallback = callback;
        mX = context.getString(R.string.var_x);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView historyExpr;
        public TextView historyResult;

        public ViewHolder(View v) {
            super(v);
            historyExpr = (TextView)v.findViewById(R.id.historyExpr);
            historyResult = (TextView)v.findViewById(R.id.historyResult);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(getContext()).inflate(getLayoutResourceId(), parent, false);
        return new ViewHolder(view);
    }

    protected int getLayoutResourceId() {
        return R.layout.history_entry;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final HistoryLine view = (HistoryLine) holder.itemView.findViewById(R.id.history_line);
        final HistoryEntry entry = getEntry(position);
        final HistoryEntry nextEntry = getNextEntry(position);

        view.setAdapter(this);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallback.onHistoryItemSelected(entry);
            }
        });

        holder.historyExpr.setText(formatText(entry.getFormula()));
        holder.historyResult.setText(formatText(entry.getResult()));

        if (entry.getFormula().contains(mX)) {
            holder.historyResult.setText(R.string.graph);
        }

        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
        if (nextEntry != null && entry.getGroupId() == nextEntry.getGroupId()) {
            view.setBackgroundResource(R.drawable.white_card_subitem);
        } else {
            view.setBackgroundResource(R.drawable.white_card);
        }
        // Due to a bug, setBackgroundResource resets padding
        view.setPadding(dp(16), dp(8), dp(16), dp(8));
    }

    private int dp(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }

    private HistoryEntry getEntry(int position) {
        final HistoryEntry entry;
        if (mDisplayEntry != null && position == mEntries.size() - 1) {
            entry = mDisplayEntry;
        } else {
            entry = mEntries.elementAt(position);
        }
        return entry;
    }

    private HistoryEntry getNextEntry(int position) {
        ++position;

        if (mEntries.size() == position) {
            return null;
        } else {
            return getEntry(position);
        }
    }

    public HistoryEntry getDisplayEntry() {
        return mDisplayEntry;
    }

    public void setDisplayEntry(String formula, String result) {
        mDisplayEntry = new HistoryEntry(formula, result, -1);
        notifyDataSetChanged();
    }

    public void clearDisplayEntry() {
        mDisplayEntry = null;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        if (mDisplayEntry == null) {
            return mEntries.size() - 1;
        } else {
            return mEntries.size();
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    protected Spanned formatText(String text) {
        if (text.matches(".*\\de[-" + Constants.MINUS + "]?\\d.*")) {
            text = text.replace("e", Constants.MUL + "10^");
        }
        return Html.fromHtml(
                mEquationFormatter.insertSupScripts(
                mEquationFormatter.addComas(mSolver, text)));
    }

    public Context getContext() {
        return mContext;
    }
}
