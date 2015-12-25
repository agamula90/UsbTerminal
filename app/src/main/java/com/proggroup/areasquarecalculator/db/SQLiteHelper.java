package com.proggroup.areasquarecalculator.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SQLiteHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "data_interpolation.db";
    private static final int DB_VERSION = 1;

    public SQLiteHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(PointHelper.CREATE_REQUEST);
        db.execSQL(SquarePointHelper.CREATE_REQUEST);
        db.execSQL(AvgPointHelper.CREATE_REQUEST);
        db.execSQL(ProjectHelper.CREATE_REQUEST);

        new ProjectHelper(null).startInit(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(PointHelper.DROP_REQUEST);
        db.execSQL(SquarePointHelper.DROP_REQUEST);
        db.execSQL(AvgPointHelper.DROP_REQUEST);
        db.execSQL(ProjectHelper.DROP_REQUEST);
        onCreate(db);
    }
}
