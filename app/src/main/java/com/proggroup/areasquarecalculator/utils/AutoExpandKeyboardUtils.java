package com.proggroup.areasquarecalculator.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.View;

public class AutoExpandKeyboardUtils {

	private static final String PREFS_NAME = "expand_keyboard_prefs";

	private static final String EXPAND_PREF_KEY = "exp_height";

	private static SharedPreferences sSharedPreferences;

	private AutoExpandKeyboardUtils() {
	}

	private static int findStatusBarHeight(Context context) {
		int resId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");

		int result = 0;
		if (resId > 0) {
			result = context.getResources().getDimensionPixelSize(resId);
		}
		return result;
	}

	public static void expand(Context context, View viewForMinHeight, View... staticViews) {
		sSharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		int expHeight = sSharedPreferences.getInt(EXPAND_PREF_KEY, 0);

		if (expHeight == 0) {
			int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			int height = 0;
			for (View staticView : staticViews) {
				staticView.measure(measureSpec, measureSpec);
				height += staticView.getMeasuredHeight();
			}
			height += findStatusBarHeight(context);
			DisplayMetrics metrics = viewForMinHeight.getResources().getDisplayMetrics();

			expHeight = metrics.heightPixels - (int) (/*metrics.density **/
					height);
			sSharedPreferences.edit().putInt(EXPAND_PREF_KEY, expHeight).commit();
		}
		viewForMinHeight.setMinimumHeight(expHeight);
	}
}
