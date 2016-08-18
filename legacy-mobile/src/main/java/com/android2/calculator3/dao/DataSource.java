package com.android2.calculator3.dao;

import android.database.Cursor;
import android.database.SQLException;

public interface DataSource {
    void open() throws SQLException;

    void close();

    String[] getColumns();

    Cursor getRows();

    String getTableName();
}
