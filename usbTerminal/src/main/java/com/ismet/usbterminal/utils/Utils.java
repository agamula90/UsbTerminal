package com.ismet.usbterminal.utils;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Environment;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Pair;
import android.widget.TextView;

import com.ismet.usbterminal.updated.EToCApplication;
import com.ismet.usbterminal.updated.data.PullState;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Utils {
	private Utils() {
	}

	public synchronized static void appendText(TextView txtOutput, String text) {
		int lineCount = txtOutput.getLineCount();
		if(lineCount >= 30) {
			int countLines = 0;

			String textString = txtOutput.getText().toString();

			for (int i = 0; i < textString.length(); i++) {
				if(textString.charAt(i) == '\n') {
					countLines++;
				}
				if(countLines == 30) {
					textString = textString.substring(0, i);
					break;
				}
			}
			txtOutput.setText(textString);
		}
		if (!TextUtils.isEmpty(txtOutput.getText())) {
			txtOutput.setText(text + "\n" + txtOutput.getText());
		} else {
			txtOutput.setText(text);
		}
	}

	public static boolean isPullStateNone() {
		return EToCApplication.getInstance().getPullState() == PullState.NONE;
	}

	public static void deleteFiles(String date, String chartidx) {
		File dir = new File(Environment.getExternalStorageDirectory(), "/AEToC_MES_Files");
		String[] filenameArry = dir.list();
		if (filenameArry != null) {
			for (int i = 0; i < filenameArry.length; i++) {
				File subDir = new File(dir, filenameArry[i]);
				if (subDir.isDirectory()) {
					String[] fileNames = subDir.list();
					for (int j = 0; j < fileNames.length; j++) {
						if (fileNames[j].contains(date) && fileNames[j].contains
								(chartidx)) {
							File f = new File(subDir, fileNames[j]);
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
				File subDir = new File(dir, filenameArry[i]);
				if (subDir.isDirectory()) {
					String[] fileNames = subDir.list();
					for (int j = 0; j < fileNames.length; j++) {
						if (fileNames[j].contains(date) && fileNames[j].contains
								(chartidx)) {
							File f = new File(subDir.getAbsolutePath() + "/" + fileNames[j]);
							f.delete();
						}
					}
				}

			}
		}
	}

    private static List<ReportData> defaultReport() {
        List<ReportData> reportDatas = new ArrayList<>();

        int backgroundColor = Color.rgb(0, 255, 255);

        reportDatas.add(new ReportData(FontTextSize.HEADER_TITLE_SIZE, "EToC Report",
                backgroundColor, false));
        //It's \n line
        reportDatas.add(new ReportData(FontTextSize.HEADER_TITLE_SIZE, ""));
        reportDatas.add(new ReportData(FontTextSize.MEDIUM_TEXT_SIZE, "Date 1010"));
        reportDatas.add(new ReportData(FontTextSize.MEDIUM_TEXT_SIZE, "SampleId"));
        ReportData data = new ReportData(FontTextSize.MEDIUM_TEXT_SIZE, "Location",
                backgroundColor, false);
        data.setAutoAddBreak(false);
        reportDatas.add(data);

        reportDatas.add(new ReportData(FontTextSize.BIG_TEXT_SIZE, "1586",
                backgroundColor, false));
        reportDatas.add(new ReportData(FontTextSize.BIG_TEXT_SIZE, "1586",
                backgroundColor, false));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "Measurement Folder:    " +
                 "MES_20160112_032844_test5"));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "Measurement Files:"));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "MES_20160112_032942_20_R1.csv    ASV  00000000.0000"));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "MES_20160112_032942_20_R1.csv    ASV  00000000.0000"));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "MES_20160112_032942_20_R1.csv    ASV  00000000.0000"));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "                          " +
                "   " + ".csv    ________"));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "Calibration Curve :      CAL_Curve_20160112_044009.csv"));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "0  0.0000  1000 ................................"));

        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "Measurements data:"));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "Auto               3 measurements"));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "Duration:       3 minutes"));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "Volume :        20uL"));

        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, ""));

        reportDatas.add(new ReportData(FontTextSize.NORMAL_TEXT_SIZE, "Operator:__________________________________         Date  ______________"));
        return reportDatas;
    }

	public static Spannable createReport(List<ReportData> dataForInsert) {
        StringBuilder builder = new StringBuilder();
        for (ReportData reportData : dataForInsert) {
            if(reportData.isAutoAddBreak()) {
                builder.append(reportData.getText()).append("\n");
            }
        }

        Spannable spannable = Spannable.Factory.getInstance().newSpannable(builder);
        int currentLineStartPosition = 0;
        for (ReportData reportData : dataForInsert) {
            String text = reportData.getText();
            int length = text.length();

            if(reportData.isBold()) {
                spannable.setSpan(new StyleSpan(Typeface.BOLD), currentLineStartPosition,
                        currentLineStartPosition + length, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }
            if(reportData.getForegroundColor() != Color.TRANSPARENT) {
                spannable.setSpan(new BackgroundColorSpan(reportData.getForegroundColor()), currentLineStartPosition,
                        currentLineStartPosition + length, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }


            spannable.setSpan(new AbsoluteSizeSpan(reportData.getFontSize()), currentLineStartPosition,
                    currentLineStartPosition + length, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            currentLineStartPosition += length + (reportData.isAutoAddBreak() ? 1 : 0);
        }
        return spannable;
    }
}
