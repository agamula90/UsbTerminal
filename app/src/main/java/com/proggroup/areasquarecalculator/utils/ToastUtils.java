package com.proggroup.areasquarecalculator.utils;

import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import com.proggroup.areasquarecalculator.R;

public class ToastUtils {
    private ToastUtils() {
    }

    public static void wrap(Toast toast) {
        View view = toast.getView();

        view.setBackgroundResource(R.drawable.toast_drawable);
        toast.setGravity(Gravity.CENTER, 0, 0);
    }
}
