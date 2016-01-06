package com.proggroup.areasquarecalculator;

import android.app.Application;
import android.content.SharedPreferences;

import com.proggroup.areasquarecalculator.db.SQLiteHelper;

import java.util.List;

public class InterpolationCalculatorApp extends Application {

	private static InterpolationCalculatorApp sInstance;

	private SQLiteHelper mSQLiteHelper;

	private List<Float> mPpmPoints, mAvgSquarePoints;

	public static InterpolationCalculatorApp getInstance() {
		return sInstance;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		sInstance = this;
		mSQLiteHelper = new SQLiteHelper(getApplicationContext());
	}

	public List<Float> getPpmPoints() {
		return mPpmPoints;
	}

	public void setPpmPoints(List<Float> ppmPoints) {
		this.mPpmPoints = ppmPoints;
	}

	public List<Float> getAvgSquarePoints() {
		return mAvgSquarePoints;
	}

	public void setAvgSquarePoints(List<Float> avgSquarePoints) {
		this.mAvgSquarePoints = avgSquarePoints;
	}

	public SQLiteHelper getSqLiteHelper() {
		return mSQLiteHelper;
	}

	public SharedPreferences getSharedPreferences() {
		return sInstance.getSharedPreferences("prefs", MODE_PRIVATE);
	}
}
