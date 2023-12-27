package com.proggroup.areasquarecalculator;

import android.content.SharedPreferences;

import com.proggroup.areasquarecalculator.db.SQLiteHelper;

import java.util.List;

public interface AttachableApplication {
    List<Float> getPpmPoints();

    void setPpmPoints(List<Float> ppmPoints);

    List<Float> getAvgSquarePoints();

    void setAvgSquarePoints(List<Float> avgSquarePoints);

    SQLiteHelper getSqLiteHelper();

    SharedPreferences getSharedPreferences();
}
