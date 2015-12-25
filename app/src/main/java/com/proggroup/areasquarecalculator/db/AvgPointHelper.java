package com.proggroup.areasquarecalculator.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.view.inputmethod.InputMethodManager;

import com.proggroup.areasquarecalculator.data.Project;

import java.util.ArrayList;
import java.util.List;

public class AvgPointHelper {
    public static final String TABLE_NAME = "avg_points";
    public static final String ID = "_avg_id";
    public static final String PPM_VALUE = "_ppm";

    public static final String CREATE_REQUEST = "create table " + TABLE_NAME +
            " ( " + BaseColumns._ID + " integer primary key autoincrement, " +
            PPM_VALUE + " real not null, " +
            Project.ID + " integer not null);";
    public static final String DROP_REQUEST = "drop table if exists" + TABLE_NAME;

    private SQLiteDatabase writeDb;
    private Project project;

    public AvgPointHelper(SQLiteDatabase writeDb, Project project) {
        this.writeDb = writeDb;
        this.project = project;
    }

    public long addAvgPoint() {
        ContentValues cv = new ContentValues(2);
        cv.put(Project.ID, project.getId());
        cv.put(PPM_VALUE, 0);
        return writeDb.insert(TABLE_NAME, null, cv);
    }

    public void updatePpm(long avgPointId, float ppm) {
        ContentValues cv = new ContentValues(1);
        cv.put(PPM_VALUE, ppm);
        writeDb.update(TABLE_NAME, cv, BaseColumns._ID + " = ?", new String[]{avgPointId + ""});
    }

    public float getPpmValue(long pointId) {
        Cursor cursor = writeDb.query(TABLE_NAME, new String[]{PPM_VALUE},
                BaseColumns._ID + " = ?", new String[]{"" + pointId}, null, null, null);

        if (cursor.moveToFirst()) {
            float ppm = cursor.getFloat(0);
            cursor.close();

            return ppm;
        } else {
            cursor.close();
            return -1;
        }
    }

    public List<Long> getAvgPoints() {
        Cursor cursor = writeDb.query(TABLE_NAME, new String[]{BaseColumns._ID},
                Project.ID + " = ?", new String[]{"" + project.getId()}, null, null, null);

        if (cursor.moveToFirst()) {
            List<Long> res = new ArrayList<>(cursor.getCount());

            do {
                res.add(cursor.getLong(0));

            } while (cursor.moveToNext());
            cursor.close();

            return res;
        } else {
            cursor.close();
            return new ArrayList<>(0);
        }
    }

    public void deleteAvgPoint(long avgPointId, SquarePointHelper squarePointHelper, PointHelper
            pointHelper) {
        List<Long> squarePointIds = squarePointHelper.getSquarePointIds(avgPointId);
        for (Long squarePointId : squarePointIds) {
            squarePointHelper.deleteSquarePointId(squarePointId, project.isSimpleMeasure(), pointHelper);
        }
        writeDb.delete(TABLE_NAME, BaseColumns._ID + " = ?", new String[] {"" + avgPointId});
    }

    public void clear() {
        writeDb.delete(TABLE_NAME, null, null);
    }
}
