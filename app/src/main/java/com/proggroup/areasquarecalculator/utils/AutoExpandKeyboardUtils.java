package com.proggroup.areasquarecalculator.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

public class AutoExpandKeyboardUtils {

    private static final String PREFS_NAME = "expand_keyboard_prefs";
    private static final String EXPAND_PREF_KEY = "exp_height";

    private static SharedPreferences sSharedPreferences;

    private AutoExpandKeyboardUtils() {
    }

    public static void expand(Context context, View viewForMinHeight, View... staticViews) {
        sSharedPreferences = context.getSharedPreferences(PREFS_NAME, Context
                .MODE_PRIVATE);
        int expHeight = sSharedPreferences.getInt(EXPAND_PREF_KEY, 0);

        if(expHeight == 0) {
            int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int height = 0;
            for (View staticView : staticViews) {
                staticView.measure(measureSpec, measureSpec);
                height += staticView.getMeasuredHeight();
            }
            DisplayMetrics metrics = viewForMinHeight.getResources().getDisplayMetrics();

            expHeight = metrics.heightPixels - (int)(/*metrics.density **/
                    height);
            sSharedPreferences.edit().putInt(EXPAND_PREF_KEY, expHeight).commit();
        }
        viewForMinHeight.setMinimumHeight(expHeight);
    }
}
