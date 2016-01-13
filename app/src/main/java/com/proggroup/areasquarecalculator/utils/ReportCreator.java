package com.proggroup.areasquarecalculator.utils;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import com.proggroup.areasquarecalculator.api.ReportAttachable;
import com.proggroup.areasquarecalculator.data.FontTextSize;
import com.proggroup.areasquarecalculator.data.ReportData;
import com.proggroup.areasquarecalculator.data.ReportDataItem;
import com.proggroup.areasquarecalculator.fragments.BottomFragment;

import java.util.ArrayList;
import java.util.List;

public class ReportCreator {
    private ReportCreator() {
    }

    private static final String UNKNOWN = "Unknown";

    public static List<ReportDataItem> defaultReport(ReportData reportData, ReportAttachable
             reportAttachable) {
        List<ReportDataItem> reportDataItemList = new ArrayList<>();

        int backgroundColor = Color.rgb(0, 255, 255);

        reportDataItemList.add(new ReportDataItem(FontTextSize.HEADER_TITLE_SIZE, "EToC Report",
                backgroundColor, false));
        //It's \n line
        reportDataItemList.add(new ReportDataItem(FontTextSize.HEADER_TITLE_SIZE, ""));

        String reportDate = reportAttachable.reportDate();

        if(reportDate == null) {
            reportDate = UNKNOWN;
        }

        reportDataItemList.add(new ReportDataItem(FontTextSize.MEDIUM_TEXT_SIZE, "Date " +
                 reportDate));

        String sampleId = reportAttachable.sampleId();

        if(sampleId == null) {
            sampleId = UNKNOWN;
        }

        reportDataItemList.add(new ReportDataItem(FontTextSize.MEDIUM_TEXT_SIZE, "SampleId " +
                 sampleId));

        String location = reportAttachable.location();

        if(location == null) {
            location = UNKNOWN;
        }

        reportDataItemList.add(new ReportDataItem(FontTextSize.MEDIUM_TEXT_SIZE, "Location " +
                 location));

        ReportDataItem data = new ReportDataItem(FontTextSize.MEDIUM_TEXT_SIZE, "PPM ",
                backgroundColor, false);
        data.setAutoAddBreak(false);
        reportDataItemList.add(data);

        String ppmText = "    " + reportData.getPpm();

        reportDataItemList.add(new ReportDataItem(FontTextSize.BIG_TEXT_SIZE, ppmText,
                backgroundColor, false));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));

        String measurementFolder = reportData.getMeasurementFolder();

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, "Measurement Folder:    " +
                measurementFolder));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, "Measurement " +
                 "Files:"));

        List<String> measurementFiles = reportData.getMeasurementFiles();
        List<Float> measurementAverages = reportData.getMeasurementAverages();
        float average = 0;

        int countMeasurements = measurementFiles.size();

        String beforeAsvString = "    ";
        String asvString = beforeAsvString + "ASV  ";

        String maxLengthString = "";

        for (int i = 0; i < countMeasurements; i++) {
            reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                    measurementFiles.get(i) + asvString + FloatFormatter.format
                            (measurementAverages.get(i))));
            average += measurementAverages.get(i);
            if(maxLengthString.length() < measurementFiles.get(i).length()) {
                maxLengthString = measurementFiles.get(i);
            }
        }

        average /= countMeasurements;

        StringBuilder measureAverageBuilder = new StringBuilder();

        for (int i = 0; i < maxLengthString.length(); i++) {
            measureAverageBuilder.append(" ");
        }

        StringBuilder lineBuilder = new StringBuilder(measureAverageBuilder);

        String averageString = FloatFormatter.format(average);

        for (int i = 0; i < asvString.length() + averageString.length(); i++) {
            if(i < beforeAsvString.length()) {
                lineBuilder.append(" ");
            } else {
                lineBuilder.append("-");
            }
        }

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                lineBuilder.toString()));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                measureAverageBuilder.toString() + asvString + FloatFormatter.format(average)));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));

        String calibrationFolder = reportData.getCalibrationCurveFolder();

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, "Calibration Curve :      " +
                calibrationFolder));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, BottomFragment
                .composePpmCurveText(reportData.getPpmData(), reportData.getAvgData())));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, "Measurements data:"));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, "Auto           " +
                ":" + "    " + reportData.getCountMeasurements() + "measurements"));

        int countMinutes = reportAttachable.countMinutes();

        String minutesText = "Duration:       " + (countMinutes > 0 ? countMinutes : UNKNOWN) +
                " minutes";

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, minutesText));

        int countVolumes = reportAttachable.volume();

        String volumeText = "Volume :        " + (countVolumes > 0 ? countVolumes : UNKNOWN) +
                " uL";

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, volumeText));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));

        String operator = reportAttachable.operator();
        String date = reportAttachable.date();

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                "Operator:" + (operator != null ? operator : UNKNOWN) + "                " +"Date" +
                        "  " + (date != null ? date : UNKNOWN)));
        return reportDataItemList;
    }

    public static Spannable createReport(List<ReportDataItem> dataForInsert) {
        StringBuilder builder = new StringBuilder();
        for (ReportDataItem reportDataItem : dataForInsert) {
            builder.append(reportDataItem.getText());
            if (reportDataItem.isAutoAddBreak()) {
                builder.append("\n");
            }
        }

        Spannable spannable = Spannable.Factory.getInstance().newSpannable(builder);
        int currentLineStartPosition = 0;
        for (ReportDataItem reportDataItem : dataForInsert) {
            String text = reportDataItem.getText();
            int length = text.length();

            spannable.setSpan(new TypefaceSpan("monospace"), currentLineStartPosition,
                     currentLineStartPosition + length, Spanned .SPAN_INCLUSIVE_INCLUSIVE);

            if (currentLineStartPosition == 0) {
                spannable.setSpan(new AlignmentSpan() {
                                      @Override
                                      public Layout.Alignment getAlignment() {
                                          return Layout.Alignment.ALIGN_CENTER;
                                      }
                                  }, currentLineStartPosition, currentLineStartPosition + length,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }

            if (reportDataItem.isBold()) {
                spannable.setSpan(new StyleSpan(Typeface.BOLD), currentLineStartPosition,
                        currentLineStartPosition + length, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }
            if (reportDataItem.getForegroundColor() != Color.TRANSPARENT) {
                spannable.setSpan(new ForegroundColorSpan(reportDataItem.getForegroundColor()),
                        currentLineStartPosition, currentLineStartPosition + length, Spanned
                                .SPAN_INCLUSIVE_INCLUSIVE);
            }

            spannable.setSpan(new AbsoluteSizeSpan(reportDataItem.getFontSize()),
                    currentLineStartPosition, currentLineStartPosition + length, Spanned
                            .SPAN_INCLUSIVE_INCLUSIVE);
            currentLineStartPosition += length + (reportDataItem.isAutoAddBreak() ? 1 : 0);
        }
        return spannable;
    }
}
