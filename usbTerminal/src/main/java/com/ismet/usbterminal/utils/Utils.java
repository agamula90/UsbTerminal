package com.ismet.usbterminal.utils;

import android.os.Environment;
import android.text.TextUtils;
import android.widget.TextView;

import java.io.File;

public class Utils {
	private Utils() {
	}

	public static void appendText(TextView txtOutput, String text) {
		if (!TextUtils.isEmpty(txtOutput.getText())) {
			txtOutput.setText(text + "\n" + txtOutput.getText());
		} else {
			txtOutput.setText(text);
		}
	}

	public static void deleteFiles(String date, String chartidx) {
		File dir = new File(Environment.getExternalStorageDirectory(), "/AEToC_MES_Files");
		String[] filenameArry = dir.list();
		if (filenameArry != null) {
			for (int i = 0; i < filenameArry.length; i++) {
				File subdir = new File(dir, filenameArry[i]);
				if (subdir.isDirectory()) {
					String[] filenameArry1 = subdir.list();
					for (int j = 0; j < filenameArry1.length; j++) {
						if (filenameArry1[j].contains(date) && filenameArry1[j].contains
								(chartidx)) {
							File f = new File(subdir, filenameArry1[j]);
							f.delete();
						}
					}
				}

			}
		}

		dir = new File(Environment.getExternalStorageDirectory(), "AEToC_CAL_Files");
		filenameArry = dir.list();
		if (filenameArry != null) {
			for (int i = 0; i < filenameArry.length; i++) {
				File subdir = new File(dir, filenameArry[i]);
				if (subdir.isDirectory()) {
					String[] filenameArry1 = subdir.list();
					for (int j = 0; j < filenameArry1.length; j++) {
						if (filenameArry1[j].contains(date) && filenameArry1[j].contains
								(chartidx)) {
							File f = new File(subdir.getAbsolutePath() + "/" + filenameArry1[j]);
							f.delete();
						}
					}
				}

			}
		}
	}

}
