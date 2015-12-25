package com.proggroup.areasquarecalculator.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PointF;
import android.provider.BaseColumns;

import com.proggroup.areasquarecalculator.data.Project;

import java.util.ArrayList;
import java.util.List;

public class PointHelper {
    private static final String TABLE_NAME = "points";
    private static final String POINT_X = "_x";
    private static final String POINT_Y = "_y";

    public static final String CREATE_REQUEST = "create table " + TABLE_NAME +
            " ( " + BaseColumns._ID + " integer primary key autoincrement, "
            + POINT_X + " real not null, "
            + POINT_Y + " real not null, "
            + SquarePointHelper.ID + " integer not null);";
    public static final String DROP_REQUEST = "drop table if exists" + TABLE_NAME;

    private SQLiteDatabase writeDb;

    public PointHelper(SQLiteDatabase writeDb) {
        this.writeDb = writeDb;
    }

    public List<PointF> getPoints(long squarePointId) {
        Cursor cursor = writeDb.query(TABLE_NAME, new String[]{POINT_X, POINT_Y},
                SquarePointHelper.ID + " = ?", new String[]{"" + squarePointId}, null, null, null);

        if (cursor.moveToFirst()) {
            List<PointF> res = new ArrayList<>(cursor.getCount());

            do {
                PointF point = new PointF(cursor.getFloat(0), cursor.getFloat(1));
                res.add(point);

            } while (cursor.moveToNext());
            cursor.close();

            return res;
        } else {
            cursor.close();
            return new ArrayList<>(0);
        }
    }

    public void addPoints(long squarePointId, List<PointF> points) {
        for (PointF point : points) {
            ContentValues cv = new ContentValues(3);
            cv.put(POINT_X, (int)point.x);
            cv.put(POINT_Y, point.y);
            cv.put(SquarePointHelper.ID, squarePointId);
            writeDb.insert(TABLE_NAME, null, cv);
        }
    }

    public void updatePoints(long squarePointId, List<PointF> points) {
        deletePoints(squarePointId);
        addPoints(squarePointId, points);
    }

    public void deletePoints(long squarePointId) {
        writeDb.delete(TABLE_NAME, SquarePointHelper.ID + " = ?", new String[]{"" + squarePointId});
    }

    public void clear() {
        writeDb.delete(TABLE_NAME, null, null);
    }
}
