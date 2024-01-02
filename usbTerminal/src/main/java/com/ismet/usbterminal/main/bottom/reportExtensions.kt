package com.ismet.usbterminal.main.bottom

import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.ismet.usbterminal.composePpmCurveText
import com.ismet.usbterminal.format
import com.itextpdf.text.Document
import com.itextpdf.text.DocumentException
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Phrase
import com.itextpdf.text.pdf.PdfWriter
import com.ismet.usbterminal.main.data.FontTextSize
import com.ismet.usbterminal.main.data.ReportData
import com.ismet.usbterminal.main.data.ReportDataItem
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

const val REPORT_START_NAME = "RPT_MES_";
private const val UNKNOWN = "Unknown";

fun defaultReport(
    reportData: ReportData,
    reportAttachable: ReportDataProvider
): List<ReportDataItem> {
    val reportDataItemList: MutableList<ReportDataItem> = ArrayList()
    val backgroundColor = Color.rgb(38, 166, 154)
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.HEADER_TITLE_SIZE,
            "EToC Report",
            backgroundColor,
            false
        )
    )
    reportAttachable.initReport()
    val dateFormat = reportAttachable.dateFormat()
    var dateString = "Date "
    var sampleIdString = "SampleId "
    var locationString = "Location "
    var ppmString = "PPM "
    var maxCount = maxCount(dateString, sampleIdString, locationString, ppmString)
    dateString = changedToMax(dateString, maxCount)
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.MEDIUM_TEXT_SIZE,
            dateString +
                    dateFormat.format(reportAttachable.reportDate())
        )
    )
    sampleIdString = changedToMax(sampleIdString, maxCount)
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.MEDIUM_TEXT_SIZE,
            "$sampleIdString$UNKNOWN"
        )
    )
    locationString = changedToMax(locationString, maxCount)
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.MEDIUM_TEXT_SIZE,
            "$locationString$UNKNOWN"
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.MEDIUM_TEXT_SIZE,
            ""
        )
    )
    ppmString = changedToMax(ppmString, maxCount)
    val data = ReportDataItem(
        FontTextSize.MEDIUM_TEXT_SIZE, ppmString,
        backgroundColor, false
    )
    data.isAutoAddBreak = false
    reportDataItemList.add(data)
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.BIG_TEXT_SIZE,
            "" + reportData
                .ppm,
            backgroundColor,
            false
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ""
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ""
        )
    )
    val measurementFolder = reportData.measurementFolder
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            "Measurement " +
                    "Folder: " +
                    measurementFolder
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ""
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ""
        )
    )
    var measurementFilesText = "Measurement Files:"
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            measurementFilesText
        )
    )
    val measurementFiles = reportData.measurementFiles
    val measurementAverages = reportData.measurementAverages
    var average = 0f
    val countMeasurements = measurementFiles.size
    val beforeAsvString = "    "
    val asvString = beforeAsvString + "ASV  "
    var measurementFilesTextEmptyString =
        changedToMax("", measurementFilesText.length)
    var maxCountSymbolsInFileName = 0
    var maxPowerOfSquare = 0
    for (i in 0 until countMeasurements) {
        if (maxCountSymbolsInFileName < measurementFiles[i].length) {
            maxCountSymbolsInFileName = measurementFiles[i].length
        }
        val measurementAverage = measurementAverages[i].format(2)
        if (maxPowerOfSquare < measurementAverage.length) {
            maxPowerOfSquare = measurementAverage.length
        }
    }
    val measurementAverageStrings: MutableList<String> = ArrayList(measurementAverages.size)
    for (i in 0 until countMeasurements) {
        measurementFiles[i] = changedToMax(
            measurementFiles[i],
            maxCountSymbolsInFileName
        )
        measurementAverageStrings.add(
            changedToMaxFromLeft(
                measurementAverages[i].format(2), maxPowerOfSquare
            )
        )
    }
    val lineBuilder = StringBuilder()
    val measureAverageBuilder = StringBuilder()
    for (i in 0 until countMeasurements) {
        reportDataItemList.add(
            ReportDataItem(
                FontTextSize.NORMAL_TEXT_SIZE,
                measurementFilesTextEmptyString + measurementFiles[i] + asvString +
                        measurementAverageStrings[i]
            )
        )
        if (i == 0) {
            measureAverageBuilder.append(measurementFilesTextEmptyString)
            lineBuilder.append(measurementFilesTextEmptyString)
            measureAverageBuilder.append(changedToMax("", measurementFiles[i].length))
            lineBuilder.append(changedToMax("", measurementFiles[i].length))
            measureAverageBuilder.append(changedToMax("", beforeAsvString.length))
            lineBuilder.append(changedToMax("", beforeAsvString.length))
            measureAverageBuilder.append(
                changedToMax(
                    "", asvString.length - beforeAsvString
                        .length
                )
            )
            lineBuilder.append(
                changedToMax(
                    "", '-', asvString.length - beforeAsvString
                        .length
                )
            )
        }
        average += measurementAverages[i]
    }
    average /= countMeasurements.toFloat()
    measureAverageBuilder.append(
        changedToMaxFromLeft(
            average.format(2), maxPowerOfSquare
        )
    )
    lineBuilder.append(changedToMax("", '-', maxPowerOfSquare))
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            lineBuilder.toString()
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            measureAverageBuilder.toString()
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ""
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ""
        )
    )
    val calibrationFolder = reportData.calibrationCurveFolder
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ("Calibration" +
                    " " +
                    "Curve: " + calibrationFolder)
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            composePpmCurveText(reportData.ppmData, reportData.avgData)
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ""
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ""
        )
    )
    measurementFilesText = "Measurements data:"
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            measurementFilesText
        )
    )
    measurementFilesTextEmptyString = changedToMax("", measurementFilesText.length)
    var auto = "Auto: "
    var duration = "Duration: "
    var volume = "Volume: "
    maxCount = maxCount(auto, duration, volume)
    auto = changedToMax(auto, maxCount)
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            (measurementFilesTextEmptyString + auto +
                    reportData.countMeasurements + " measurements")
        )
    )
    val countMinutes = reportAttachable.countMinutes()
    duration = changedToMax(duration, maxCount)
    val minutesText =
        measurementFilesTextEmptyString + duration + (if (countMinutes > 0) countMinutes else UNKNOWN) + " minutes"
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            minutesText
        )
    )
    val countVolumes = reportAttachable.volume()
    volume = changedToMax(volume, maxCount)
    val volumeText =
        measurementFilesTextEmptyString + volume + (if (countVolumes > 0) countVolumes else UNKNOWN) + " uL"
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            volumeText
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ""
        )
    )
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            ""
        )
    )
    val dateFormatter = reportAttachable.dateFormat()
    reportDataItemList.add(
        ReportDataItem(
            FontTextSize.NORMAL_TEXT_SIZE,
            "Operator: $UNKNOWN                Date: ${dateFormatter.format(reportAttachable.reportDate())}"
        )
    )
    return reportDataItemList
}

private fun maxCount(vararg values: String): Int {
    var max = 0
    for (value in values) {
        max = max.coerceAtLeast(value.length)
    }
    return max
}

fun changedToMax(value: String, maxCount: Int): String {
    val builder = java.lang.StringBuilder(value)
    for (i in 0 until maxCount - value.length) {
        builder.append(" ")
    }
    return builder.toString()
}

fun changedToMax(value: String, addSymbol: Char, maxCount: Int): String {
    val builder = java.lang.StringBuilder(value)
    for (i in 0 until maxCount - value.length) {
        builder.append(addSymbol)
    }
    return builder.toString()
}

fun changedToMaxFromLeft(value: String, maxCount: Int): String {
    val builder = java.lang.StringBuilder()
    for (i in 0 until maxCount - value.length) {
        builder.append(" ")
    }
    builder.append(value)
    return builder.toString()
}

fun createReport(dataForInsert: List<ReportDataItem>): Spannable? {
    val builder = java.lang.StringBuilder()
    val startMargin = "  "
    builder.append(startMargin)
    for (reportDataItem in dataForInsert) {
        builder.append(reportDataItem.text)
        if (reportDataItem.isAutoAddBreak) {
            builder.append("\n")
            builder.append(startMargin)
        }
    }
    val spannable = Spannable.Factory.getInstance().newSpannable(builder)
    var currentLineStartPosition = 0
    for (reportDataItem in dataForInsert) {
        val text = reportDataItem.text
        val length = text.length + if (reportDataItem.isAutoAddBreak) startMargin.length else 0
        spannable.setSpan(
            TypefaceSpan("monospace"), currentLineStartPosition,
            currentLineStartPosition + length, Spanned.SPAN_INCLUSIVE_INCLUSIVE
        )
        if (currentLineStartPosition == 0) {
            spannable.setSpan(
                AlignmentSpan { Layout.Alignment.ALIGN_CENTER },
                currentLineStartPosition,
                currentLineStartPosition + length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        if (reportDataItem.isBold) {
            spannable.setSpan(
                StyleSpan(Typeface.BOLD), currentLineStartPosition,
                currentLineStartPosition + length, Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        if (reportDataItem.foregroundColor != Color.TRANSPARENT) {
            spannable.setSpan(
                ForegroundColorSpan(reportDataItem.foregroundColor),
                currentLineStartPosition,
                currentLineStartPosition + length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        spannable.setSpan(
            AbsoluteSizeSpan(reportDataItem.fontSize),
            currentLineStartPosition,
            currentLineStartPosition + length,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE
        )
        currentLineStartPosition += length + if (reportDataItem.isAutoAddBreak) 1 else 0
    }
    return spannable
}

@Throws(DocumentException::class, FileNotFoundException::class)
fun createReport(dataForInsert: List<ReportDataItem>, folderForWrite: String?) {
    val startMargin = "  "
    val document = Document()
    PdfWriter.getInstance(document, FileOutputStream(folderForWrite))
    document.open()
    var isNextLineNew = true
    var isFirstItem = true
    var newParagraph: Paragraph? = null
    for (reportDataItem in dataForInsert) {
        if (isNextLineNew) {
            reportDataItem.applyLeftPadding(startMargin)
        }
        val font = Font(Font.FontFamily.COURIER, reportDataItem.fontSize / 1.8f)
        if (reportDataItem.isBold) {
            font.style = Font.BOLD
        }
        if (reportDataItem.foregroundColor != Color.TRANSPARENT) {
            val color = reportDataItem.foregroundColor
            font.setColor(Color.red(color), Color.green(color), Color.blue(color))
        }
        if (isNextLineNew) {
            if (newParagraph != null) {
                document.add(newParagraph)
            }
            newParagraph = Paragraph(reportDataItem.text, font)
        } else {
            newParagraph?.add(Phrase(reportDataItem.text, font))
        }
        if (isFirstItem) {
            newParagraph!!.alignment = Element.ALIGN_CENTER
        }
        isNextLineNew = reportDataItem.isAutoAddBreak
        isFirstItem = false
    }
    if (newParagraph != null) {
        document.add(newParagraph)
    }
    document.newPage()
    document.close()
}

fun countReports(file: File): Int {
    val files = file.listFiles() ?: return 0
    val reportFiles = mutableListOf<File>()
    for (htmlFile in files) {
        val htmlName = htmlFile.name
        if (!htmlFile.isDirectory &&
            (htmlName.endsWith(".html") || htmlName.endsWith(".xhtml")) &&
            htmlName.startsWith(REPORT_START_NAME)
        ) {
            reportFiles.add(htmlFile)
        }
    }
    var maxCount = 0
    for (htmlFile in reportFiles) {
        val name = htmlFile.name
        try {
            val count = name.substring(
                name.lastIndexOf('_') + 1, name
                    .lastIndexOf(".")
            ).toInt()
            if (count > maxCount) {
                maxCount = count
            }
        } catch (e: NumberFormatException) {
        }
    }
    return maxCount + 1
}