package com.ismet.usbterminal

import android.app.Application
import android.content.SharedPreferences
import com.proggroup.areasquarecalculator.AttachableApplication
import com.proggroup.areasquarecalculator.db.SQLiteHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EToCApplication : Application(), AttachableApplication {
    private lateinit var mSQLiteHelper: SQLiteHelper

    private var mPpmPoints: List<Float>? = null
    private var mAvgSquarePoints: MutableList<Float>? = null

    override fun onCreate() {
        super.onCreate()
        mSQLiteHelper = SQLiteHelper(this)
    }

    override fun getPpmPoints(): List<Float>? {
        return mPpmPoints
    }

    override fun setPpmPoints(ppmPoints: List<Float>?) {
        this.mPpmPoints = ppmPoints
    }

    override fun getAvgSquarePoints(): MutableList<Float>? {
        return mAvgSquarePoints
    }

    override fun setAvgSquarePoints(avgSquarePoints: MutableList<Float>?) {
        this.mAvgSquarePoints = avgSquarePoints
    }

    override fun getSqLiteHelper(): SQLiteHelper {
        return mSQLiteHelper
    }

    override fun getSharedPreferences(): SharedPreferences {
        return getSharedPreferences("prefs", MODE_PRIVATE)
    }
}