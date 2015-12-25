package com.proggroup.areasquarecalculator;

import android.app.Application;
import android.content.SharedPreferences;

import com.proggroup.areasquarecalculator.db.SQLiteHelper;

import java.util.List;

public class InterpolationCalculator extends Application {

    private static InterpolationCalculator instance;

    public static InterpolationCalculator getInstance() {
        return instance;
    }

    private SQLiteHelper SQLiteHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        SQLiteHelper = new SQLiteHelper(getApplicationContext());
    }

    private List<Float> ppmPoints, avgSquarePoints;

    public void setPpmPoints(List<Float> ppmPoints) {
        this.ppmPoints = ppmPoints;
    }

    public List<Float> getPpmPoints() {
        return ppmPoints;
    }

    public void setAvgSquarePoints(List<Float> avgSquarePoints) {
        this.avgSquarePoints = avgSquarePoints;
    }

    public List<Float> getAvgSquarePoints() {
        return avgSquarePoints;
    }

    public SQLiteHelper getSqLiteHelper() {
        return SQLiteHelper;
    }

    public SharedPreferences getSharedPreferences() {
        return instance.getSharedPreferences("prefs", MODE_PRIVATE);
    }
}
