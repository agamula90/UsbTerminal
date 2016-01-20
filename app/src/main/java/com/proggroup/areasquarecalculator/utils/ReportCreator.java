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

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.proggroup.areasquarecalculator.api.ReportAttachable;
import com.proggroup.areasquarecalculator.data.FontTextSize;
import com.proggroup.areasquarecalculator.data.ReportData;
import com.proggroup.areasquarecalculator.data.ReportDataItem;
import com.proggroup.areasquarecalculator.fragments.BottomFragment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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

        if (reportDate == null) {
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

        if (sampleId == null) {
            sampleId = UNKNOWN;
        }

        sampleIdString = changedToMax(sampleIdString, maxCount);

        reportDataItemList.add(new ReportDataItem(FontTextSize.MEDIUM_TEXT_SIZE, sampleIdString +
                sampleId));

        String location = reportAttachable.location();

        if (location == null) {
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

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, "Measurement " +
                "Folder: " +
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

        String measurementFilesTextEmptyString = changedToMax("", measurementFilesText.length());

        int maxCountSymbolsInFileName = 0;
        int maxPowerOfSquare = 0;

        for (int i = 0; i < countMeasurements; i++) {
            if (maxCountSymbolsInFileName < measurementFiles.get(i).length()) {
                maxCountSymbolsInFileName = measurementFiles.get(i).length();
            }
            if (maxPowerOfSquare < FloatFormatter.format(measurementAverages.get(i)).length()) {
                maxPowerOfSquare = FloatFormatter.format(measurementAverages.get(i)).length();
            }
        }

        List<String> measurementAverageStrings = new ArrayList<>(measurementAverages.size());

        for (int i = 0; i < countMeasurements; i++) {
            measurementFiles.set(i, changedToMax(measurementFiles.get(i),
                    maxCountSymbolsInFileName));

            measurementAverageStrings.add(changedToMaxFromLeft(FloatFormatter.format
                    (measurementAverages.get(i)), maxPowerOfSquare));
        }

        StringBuilder lineBuilder = new StringBuilder();
        StringBuilder measureAverageBuilder = new StringBuilder();

        for (int i = 0; i < countMeasurements; i++) {
            reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                    measurementFilesTextEmptyString + measurementFiles.get(i) + asvString +
                            measurementAverageStrings.get(i)));

            if (i == 0) {
                measureAverageBuilder.append(measurementFilesTextEmptyString);
                lineBuilder.append(measurementFilesTextEmptyString);
                measureAverageBuilder.append(changedToMax("", measurementFiles.get(i).length
                        ()));
                lineBuilder.append(changedToMax("", measurementFiles.get(i).length()));
                measureAverageBuilder.append(changedToMax("", beforeAsvString.length()));
                lineBuilder.append(changedToMax("", beforeAsvString.length()));
                measureAverageBuilder.append(changedToMax("", asvString.length() - beforeAsvString
                        .length()));
                lineBuilder.append(changedToMax("", '-', asvString.length() - beforeAsvString
                        .length()));
            }
            average += measurementAverages.get(i);
        }

        average /= countMeasurements;

        measureAverageBuilder.append(changedToMaxFromLeft(FloatFormatter.format
                (average), maxPowerOfSquare));

        lineBuilder.append(changedToMax("", '-', maxPowerOfSquare));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                lineBuilder.toString()));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                measureAverageBuilder.toString()));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));

        String calibrationFolder = reportData.getCalibrationCurveFolder();

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, "Calibration" +
                " " +
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
                countMinutes : UNKNOWN) + " minutes";

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, minutesText));

        int countVolumes = reportAttachable.volume();

        volume = changedToMax(volume, maxCount);

        String volumeText = measurementFilesTextEmptyString + volume + (countVolumes > 0 ?
                countVolumes : UNKNOWN) + " uL";

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, volumeText));

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));
        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE, ""));

        String operator = reportAttachable.operator();
        String date = reportAttachable.dateString();

        reportDataItemList.add(new ReportDataItem(FontTextSize.NORMAL_TEXT_SIZE,
                "Operator: " + (operator != null ? operator : UNKNOWN) + "                "
                        + "Date: " + (date != null ? date : UNKNOWN)));
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

    public static String changedToMax(String value, char addSymbol, int maxCount) {
        StringBuilder builder = new StringBuilder(value);
        for (int i = 0; i < maxCount - value.length(); i++) {
            builder.append(addSymbol);
        }
        return builder.toString();
    }

    public static String changedToMaxFromLeft(String value, int maxCount) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < maxCount - value.length(); i++) {
            builder.append(" ");
        }
        builder.append(value);
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
                    currentLineStartPosition + length, Spanned.SPAN_INCLUSIVE_INCLUSIVE);

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

    public static void createReport(List<ReportDataItem> dataForInsert, String folderForWrite)
            throws DocumentException, FileNotFoundException {

        String startMargin = "  ";

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(folderForWrite));
        document.open();

        boolean isNextLineNew = true;
        boolean isFirstItem = true;

        Paragraph newParagraph = null;

        for (ReportDataItem reportDataItem : dataForInsert) {
            if (isNextLineNew) {
                reportDataItem.applyLeftPadding(startMargin);
            }

            Font font = new Font(Font.FontFamily.COURIER, reportDataItem.getFontSize() / 1.8f);

            if (reportDataItem.isBold()) {
                font.setStyle(Font.BOLD);
            }

            if (reportDataItem.getForegroundColor() != Color.TRANSPARENT) {
                int color = reportDataItem.getForegroundColor();
                font.setColor(Color.red(color), Color.green(color), Color.blue(color));
            }

            if (isNextLineNew) {
                if (newParagraph != null) {
                    document.add(newParagraph);
                }
                newParagraph = new Paragraph(reportDataItem.getText(), font);
            } else {
                if (newParagraph != null) {
                    newParagraph.add(new Phrase(reportDataItem.getText(), font));
                }
            }

            if (isFirstItem) {
                newParagraph.setAlignment(Element.ALIGN_CENTER);
            }

            isNextLineNew = reportDataItem.isAutoAddBreak();
            isFirstItem = false;
        }

        if (newParagraph != null) {
            document.add(newParagraph);
        }

        document.newPage();
        document.close();
    }

    public static final String REPORT_START_NAME = "RPT_MES_";

    public static final int countReports(String folder) {
        File file = new File(folder);
        File files[] = file.listFiles();
        if (files == null) {
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
                if (count > maxCount) {
                    maxCount = count;
                }
            } catch (NumberFormatException e) {
            }
        }

        return maxCount + 1;
    }
}
