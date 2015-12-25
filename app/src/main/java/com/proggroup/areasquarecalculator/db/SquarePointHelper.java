package com.proggroup.areasquarecalculator.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import com.proggroup.areasquarecalculator.data.Project;

import java.util.ArrayList;
import java.util.List;

public class SquarePointHelper {
    private static final String TABLE_NAME = "square_points";
    public static final String ID = "_square_point_id";

    public static final String CREATE_REQUEST = "create table " + TABLE_NAME +
            " ( " + BaseColumns._ID + " integer primary key autoincrement, "
            + AvgPointHelper.ID + " integer not null);";
    public static final String DROP_REQUEST = "drop table if exists" + TABLE_NAME;

    private SQLiteDatabase writeDb;

    public SquarePointHelper(SQLiteDatabase writeDb) {
        this.writeDb = writeDb;
    }

    public List<Long> getSquarePointIds(long avgPointId) {
        Cursor cursor = writeDb.query(TABLE_NAME, new String[]{BaseColumns._ID},
                AvgPointHelper.ID + " = ?", new String[]{"" + avgPointId}, null, null, null);

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

    public void addSquarePointId(long avgPointId) {
        ContentValues cv = new ContentValues(1);
        cv.put(AvgPointHelper.ID, avgPointId);
        writeDb.insert(TABLE_NAME, null, cv);
    }

    public void addSquarePointIdSimpleMeasure(long avgPointId) {
        List<Long> squarePointIds = getSquarePointIds(avgPointId);
        /*if (squarePointIds.size() == Project.TABLE_MAX_COLS_COUNT) {
            return;
        }*/
        addSquarePointId(avgPointId);
    }

    private long getAvgPointId(long squarePointId) {
        Cursor cursor = writeDb.query(TABLE_NAME, new String[]{AvgPointHelper.ID},
                BaseColumns._ID + " = ?", new String[]{"" + squarePointId}, null, null, null);

        if (cursor.moveToFirst()) {
            cursor.close();

            return cursor.getLong(0);
        } else {
            cursor.close();
            return -1;
        }
    }

    public void deleteSquarePointId(long id, boolean isSimpleMeasure, PointHelper pointHelper) {
        /*if (isSimpleMeasure) {
            long avgPointId = getAvgPointId(id);
            if (avgPointId != -1) {
                return;
            }
        }*/
        pointHelper.deletePoints(id);
        writeDb.delete(TABLE_NAME, BaseColumns._ID + " = ?", new String[]{id + ""});
    }

    public void clear() {
        writeDb.delete(TABLE_NAME, null, null);
    }
}
