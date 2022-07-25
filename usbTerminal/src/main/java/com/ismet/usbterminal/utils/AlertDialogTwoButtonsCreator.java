package com.ismet.usbterminal.utils;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;

import com.ismet.usbterminal.MainActivity;
import com.ismet.usbterminalnew.R;
import com.ismet.usbterminal.mainscreen.EToCMainActivity;

import kotlin.Deprecated;

public class AlertDialogTwoButtonsCreator {

    @Deprecated(message = "Use MainActivity instead")
	public static AlertDialog.Builder createTwoButtonsAlert(EToCMainActivity activity, int
			layoutId, String
			dialogText, DialogInterface.OnClickListener okListener, DialogInterface
			.OnClickListener cancelListener, OnInitLayoutListener initLayoutListener) {
		AlertDialog.Builder alertbuilder = new AlertDialog.Builder(activity);
		alertbuilder.setTitle(dialogText);

		LayoutInflater inflater = activity.getLayoutInflater();

		if(layoutId != 0) {
			View dialogView = inflater.inflate(layoutId, null);
			alertbuilder.setView(dialogView);
			initLayoutListener.onInitLayout(dialogView);
		}
		alertbuilder.setPositiveButton(R.string.ui_save, okListener);
		alertbuilder.setNegativeButton(R.string.ui_cancel, cancelListener);

		return alertbuilder;
	}

    public static AlertDialog.Builder createTwoButtonsAlert(
            MainActivity activity,
            int layoutId,
            String dialogText,
            DialogInterface.OnClickListener okListener,
            DialogInterface.OnClickListener cancelListener,
            OnInitLayoutListener initLayoutListener
    ) {
        AlertDialog.Builder alertbuilder = new AlertDialog.Builder(activity);
        alertbuilder.setTitle(dialogText);

        LayoutInflater inflater = activity.getLayoutInflater();

        if(layoutId != 0) {
            View dialogView = inflater.inflate(layoutId, null);
            alertbuilder.setView(dialogView);
            initLayoutListener.onInitLayout(dialogView);
        }
        alertbuilder.setPositiveButton(R.string.ui_save, okListener);
        alertbuilder.setNegativeButton(R.string.ui_cancel, cancelListener);

        return alertbuilder;
    }

	public interface OnInitLayoutListener {
		void onInitLayout(View contentView);
	}
}
