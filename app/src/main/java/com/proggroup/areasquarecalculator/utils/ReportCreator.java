package com.proggroup.areasquarecalculator.utils;

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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ReportCreator {
    private ReportCreator() {
    }

    private static final String UNKNOWN = "Unknown";

    public static List<ReportDataItem> defaultReport(ReportData reportData, ReportAttachable
             reportAttachable) {
        List<ReportDataItem> reportDataItemList = new ArrayList<>();

        int backgroundColor = Color.rgb(38, 166, 154);

        reportDataItemList.add(new ReportDataItem(FontTextSize.HEADER_TITLE_SIZE, "EToC Report",
                backgroundColor, false));
        //It's \n line
        reportDataItemList.add(new ReportDataItem(FontTextSize.HEADER_TITLE_SIZE, ""));

        String reportDate = reportAttachable.reportDateString();

        if(reportDate == null) {
            reportDate = UNKNOWN;
        }

        String dateString = "Date ";
        String sampleIdString = "SampleId ";
        String locationString = "Location ";
        String ppmString = "PPM ";

        int maxCount = maxCount(dateString, sampleIdString, locationString, ppmString);

        dateString = changedToMax(dateString, maxCount);

        reportDataItemList.add(new ReportDataItem(FontTextSize.MEDIUM_TEXT_SIZE, dateString +
                 reportDate));

        String sampleId = reportAttachable.sampleId();

        if(sampleId == null) {
            sampleId = UNKNOWN;
        }

        sampleIdString = changedToMax(sampleIdString, maxCount);

        reportDataItemList.add(new ReportDataItem(FontTextSize.MEDIUM_TEXT_SIZE, sampleIdString +
                 sampleId));

        String location = reportAttachable.location();

        if(location == null) {
            location = UNKNOWN;
        }

        locationString = changedToMax(locationString, maxCount);

        reportDataItemList.add(new ReportDataItem(FontTextSize.MEDIUM_TEXT_SIZE, locationString +
                 location));

        reportDataItemList.add(new ReportDataItem(FontTextSize.MEDIUM_TEXT_SIZE, ""));

        ppmString = changedToMax(ppmString, maxCount);

        ReportDataItem data = new ReportDataItem(FontTextSize.MEDIUM_TEXT_SIZE, ppmString,
                backgroundColor, false);
        data.setAutoAddBreak(false);
        reportDataItemList.add(data);

        reportDataItemList.add(new ReportDataItem(FontTextSize.BIG_TEXT_SIZE, "" + reportData
                .getPpm(), backgroundColor, false));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));

        String measurementFolder = reportData.getMeasurementFolder();

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, "Measurement Folder: " +
                measurementFolder));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));

        String measurementFilesText = "Measurement Files:";

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                 measurementFilesText));

        List<String> measurementFiles = reportData.getMeasurementFiles();
        List<Float> measurementAverages = reportData.getMeasurementAverages();
        float average = 0;

        int countMeasurements = measurementFiles.size();

        String beforeAsvString = "    ";
        String asvString = beforeAsvString + "ASV  ";

        String maxLengthString = "";

        String measurementFilesTextEmptyString = changedToMax("", measurementFilesText.length());

        for (int i = 0; i < countMeasurements; i++) {
            reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                    measurementFilesTextEmptyString + measurementFiles.get(i) + asvString +
                    FloatFormatter.format(measurementAverages.get(i))));
            average += measurementAverages.get(i);
            if(maxLengthString.length() < measurementFiles.get(i).length()) {
                maxLengthString = measurementFiles.get(i);
            }
        }

        average /= countMeasurements;

        StringBuilder measureAverageBuilder = new StringBuilder();

        for (int i = 0; i < maxLengthString.length() + measurementFilesTextEmptyString.length();
              i++) {
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

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, "Calibration " +
                "Curve: " + calibrationFolder));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, BottomFragment
                .composePpmCurveText(reportData.getPpmData(), reportData.getAvgData())));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));

        measurementFilesText = "Measurements data:";

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                 measurementFilesText));

        measurementFilesTextEmptyString = changedToMax("", measurementFilesText.length());

        String auto = "Auto: ";
        String duration = "Duration: ";
        String volume = "Volume: ";

        maxCount = maxCount(auto, duration, volume);

        auto = changedToMax(auto, maxCount);

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                measurementFilesTextEmptyString + auto +
                 reportData.getCountMeasurements() + " measurements"));

        int countMinutes = reportAttachable.countMinutes();

        duration = changedToMax(duration, maxCount);

        String minutesText = measurementFilesTextEmptyString + duration + (countMinutes > 0 ?
                countMinutes : UNKNOWN) +
                " minutes";

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, minutesText));

        int countVolumes = reportAttachable.volume();

        volume = changedToMax(volume, maxCount);

        String volumeText = measurementFilesTextEmptyString + volume + (countVolumes > 0 ?
                countVolumes : UNKNOWN) +
                " uL";

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, volumeText));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));

        String operator = reportAttachable.operator();
        String date = reportAttachable.dateString();

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                "Operator: " + (operator != null ? operator : UNKNOWN) + "                "
                        +"Date: " + (date != null ? date : UNKNOWN)));
        return reportDataItemList;
    }

    private static int maxCount(String... values) {
        int max = 0;
        for (String value : values) {
            max = Math.max(max, value.length());
        }
        return max;
    }

    public static String changedToMax(String value, int maxCount) {
        StringBuilder builder = new StringBuilder(value);
        for (int i = 0; i < maxCount - value.length(); i++) {
            builder.append(" ");
        }
        return builder.toString();
    }

    public static Spannable createReport(List<ReportDataItem> dataForInsert) {
        StringBuilder builder = new StringBuilder();

        String startMargin = "  ";

        builder.append(startMargin);

        for (ReportDataItem reportDataItem : dataForInsert) {
            builder.append(reportDataItem.getText());
            if (reportDataItem.isAutoAddBreak()) {
                builder.append("\n");
                builder.append(startMargin);
            }
        }

        Spannable spannable = Spannable.Factory.getInstance().newSpannable(builder);
        int currentLineStartPosition = 0;
        for (ReportDataItem reportDataItem : dataForInsert) {
            String text = reportDataItem.getText();
            int length = text.length() + (reportDataItem.isAutoAddBreak() ? startMargin.length()
                    : 0);

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

    public static final String REPORT_START_NAME = "RPT_MES_";

    public static final int countReports(String folder) {
        File file = new File(folder);
        File files[] = file.listFiles();
        if(files == null) {
            return 0;
        }

        List<File> reportFiles = new ArrayList<>();
        for (File htmlFile : files) {
            String htmlName = htmlFile.getName();
            if (!htmlFile.isDirectory() && (htmlName.endsWith(".html") || htmlName
                    .endsWith(".xhtml")) && htmlName.startsWith(REPORT_START_NAME)) {
                reportFiles.add(htmlFile);
            }
        }

        int maxCount = 0;

        for (File htmlFile : reportFiles) {
            String name = htmlFile.getName();
            try {
                String str = name.substring(name.lastIndexOf('_') + 1, name
                        .lastIndexOf("."));
                int count = Integer.parseInt(str);
                if(count > maxCount) {
                    maxCount = count;
                }
            } catch (NumberFormatException e) {
            }
        }

        return maxCount + 1;
    }
}
