package com.ismet.usbterminal

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.get
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.ismet.usbterminal.data.Charts
import com.ismet.usbterminal.data.PeriodicResponse
import com.ismet.usbterminal.data.PrefConstants
import com.ismet.usbterminalnew.BuildConfig
import com.ismet.usbterminalnew.R
import com.ismet.usbterminalnew.databinding.*
import com.proggroup.areasquarecalculator.utils.AutoExpandKeyboardUtils
import java.io.File
import kotlin.math.roundToInt

fun LineChart.set(chartHelperPaint: Paint, charts: Charts) {
    axisLeft.axisMaximum = charts.maxY
    xAxis.axisMaximum = charts.maxX
    for (chart in charts.charts) {
        (data.dataSets[chart.id] as LineDataSet).values = chart.points.map { Entry(it.x, it.y) }
    }
    data.notifyDataChanged()
    notifyDataSetChanged()
    invalidate()
    post {
        val parentView = (parent as ViewGroup)
        val backgroundView = parentView[parentView.indexOfChild(this) - 1]
        val backgroundMarginStart = axisLeft.getRequiredWidthSpace(chartHelperPaint)
        val backgroundLayoutParams = backgroundView.layoutParams as FrameLayout.LayoutParams
        backgroundLayoutParams.marginStart = backgroundMarginStart.toInt()
        backgroundView.layoutParams = backgroundLayoutParams
    }
}

fun Context.showOnOffDialog(init: (LayoutDialogOnOffBinding) -> Unit, okClick: (LayoutDialogOnOffBinding, DialogInterface) -> Unit): AlertDialog = AlertDialog.Builder(this).let {
    val inflater = LayoutInflater.from(this)
    val binding = LayoutDialogOnOffBinding.inflate(inflater)
    init(binding)
    val dialog = it.apply {
        setTitle("Set On/Off commands")
        setView(binding.root)
        setPositiveButton(R.string.ui_save) { dialog, _ ->
            dialog.hideSoftInput()
            okClick(binding, dialog)
        }
        setNegativeButton(R.string.ui_cancel) { dialog, _ ->
            dialog.hideSoftInput()
            dialog.cancel()
        }
    }.create()
    dialog.show()
    dialog
}

fun Context.showCommandDialog(init: (LayoutDialogOneCommandBinding) -> Unit, okClick: (LayoutDialogOneCommandBinding, DialogInterface) -> Unit): AlertDialog = AlertDialog.Builder(this).let {
    val inflater = LayoutInflater.from(this)
    val binding = LayoutDialogOneCommandBinding.inflate(inflater)
    init(binding)
    val dialog = it.apply {
        setTitle("Set command")
        setView(binding.root)
        setPositiveButton(R.string.ui_save) { dialog, _ ->
            dialog.hideSoftInput()
            okClick(binding, dialog)
        }
        setNegativeButton(R.string.ui_cancel) { dialog, _ ->
            dialog.hideSoftInput()
            dialog.cancel()
        }
    }.create()
    dialog.show()
    dialog
}

private fun DialogInterface.hideSoftInput() {
    (this as? AlertDialog)?.window?.let {
        WindowInsetsControllerCompat(it, it.decorView).hide(WindowInsetsCompat.Type.ime())
    }
}

fun TextView.append(isRead: Boolean, command: String) {
    if (command.isEmpty()) return
    val fullNewText = (if (isRead) "Rx: " else "Tx: ") + command + "\n" + text.toString()
    text = fullNewText.withMaxLines(30)
}

private fun String.withMaxLines(maxLines: Int): String {
    val lines = split("\n", limit = maxLines).mapIndexed { index, s ->
        if (index != maxLines - 1) {
            s
        } else {
            val firstIndexOfNewLineCharacter = s.indexOf("\n")
            if (firstIndexOfNewLineCharacter != -1) {
                s.substring(0, firstIndexOfNewLineCharacter)
            } else {
                s
            }
        }
    }

    return lines.joinToString(separator = "\n")
}

fun View.showMeasureDialog(init: (LayoutDialogMeasureBinding) -> Unit, okClick: (LayoutDialogMeasureBinding, DialogInterface) -> Unit): AlertDialog = AlertDialog.Builder(context).let {
    isEnabled = false
    val inflater = LayoutInflater.from(context)
    val binding = LayoutDialogMeasureBinding.inflate(inflater)
    init(binding)
    val dialog = it.apply {
        setTitle("Start Measure")
        setView(binding.root)
        setPositiveButton(R.string.ui_save) { dialog, _ ->
            dialog.hideSoftInput()
            okClick(binding, dialog)
        }
        setNegativeButton(R.string.ui_cancel) { dialog, _ ->
            dialog.hideSoftInput()
        }
    }.create()
    dialog.setOnCancelListener { isEnabled = true }
    dialog.show()
    dialog
}

fun String.encodeToByteArrayEnhanced(): ByteArray {
    if (!startsWith("(") || !endsWith(")")) {
        return when {
            BuildConfig.DEBUG -> encodeToByteArray()
            else -> encodeToByteArray() + "\r".encodeToByteArray()
        }
    }
    val bytesEncoded = substring(1, lastIndex).split("-")
    return bytesEncoded.map { it.toInt(radix = 16).toByte() }.toByteArray()
}

private val temperaturePattern = "^@\\d+,\\d+\\(\\d+,\\d+,\\d+,\\d+\\),\\d+,\\d+,\\d+,\\d+,\\d+$".toRegex()

fun ByteArray.decodeToPeriodicResponse(): PeriodicResponse? = when {
    this.size == 7 && this[0] == 0xFE.toByte() && this[1] == 0x44.toByte() -> {
        PeriodicResponse.Co2(
            response = joinToString(separator = "-") {
                val value = (0xFF and it.toInt()).toString(radix = 16)
                if (value.length != 2) {
                    "0" + value.uppercase()
                } else {
                    value.uppercase()
                }
            },
            value = String.format("%02X%02X", this[3], this[4]).toInt(radix = 16)
        )
    }
    BuildConfig.DEBUG -> getNotRawPeriodicResponse()
    else -> copyOfRange(0, size - 1).getNotRawPeriodicResponse()
}

private fun ByteArray.getNotRawPeriodicResponse(): PeriodicResponse? = when {
    decodeToString().matches(temperaturePattern) -> {
        val response = decodeToString()
        val temperatureParts = response.split(",")
        PeriodicResponse.Temperature(response, temperatureParts[6].toInt())
    }
    else -> null
}

/*
 * read text from sub path, specified by string
 */
fun String.readTextEnhanced(file: com.ismet.storage.File): String = file.read(this).replace("\r", "")

fun ByteArray.decodeToStringEnhanced(): String {
    val periodicResponse = decodeToPeriodicResponse()
    if (periodicResponse != null) return periodicResponse.toString()
    return when {
        BuildConfig.DEBUG -> decodeToString()
        else -> copyOfRange(0, size - 1).decodeToString()
    }
}

fun LineChart.init(prefs: SharedPreferences) {
    description.isEnabled = false
    minOffset = 0f
    setTouchEnabled(false)
    setDrawGridBackground(false)
    isDragEnabled = false
    setScaleEnabled(false)
    setPinchZoom(false)
    val color = Color.rgb(136, 136, 136)
    xAxis.apply {
        gridColor = color
        setLabelCount(4, true)
        valueFormatter = object: ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String = when(value){
                0f -> ""
                else -> (value * ((labelCount - 1) / axisMaximum) * prefs.getInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)).roundToInt().toString()
            }
        }
        position = XAxis.XAxisPosition.BOTTOM
        axisMinimum = 0f
        axisMaximum = 9f
        textColor = Color.WHITE
        yOffset = 0f
        setAvoidFirstLastClipping(true)
    }
    axisRight.isEnabled = false
    setClipValuesToContent(true)
    axisLeft.apply {
        xOffset = 0f
        gridColor = color
        setLabelCount(9, true)
        axisMinimum = 0f
        axisMaximum = 10f
        textColor = Color.WHITE
    }

    val dataSets = listOf(Color.BLACK, Color.RED, Color.BLUE).map { createDataSet(it) }
    data = LineData(dataSets)

    legend.form = LegendForm.NONE
    legend.setDrawInside(true)
    post {
        val parentView = (parent as ViewGroup)
        val backgroundView = parentView[parentView.indexOfChild(this) - 1]
        val backgroundLayoutParams = backgroundView.layoutParams as FrameLayout.LayoutParams
        val backgroundMarginBottom = xAxis.mLabelHeight
        backgroundLayoutParams.apply {
            bottomMargin = (backgroundMarginBottom * 1.5f).toInt()
            topMargin = backgroundMarginBottom / 2
        }
        backgroundView.layoutParams = backgroundLayoutParams
        val chartLayoutParams = layoutParams as FrameLayout.LayoutParams
        chartLayoutParams.apply {
            topMargin = backgroundMarginBottom / 2
            bottomMargin = backgroundMarginBottom / 2
        }
        layoutParams = chartLayoutParams
    }
}

private fun createDataSet(lineColor: Int) = LineDataSet(ArrayList(), null).apply {
    mode = LineDataSet.Mode.CUBIC_BEZIER
    //maybe need to set cubic intensity
    cubicIntensity = 0.05f
    setDrawIcons(false)
    color = lineColor
    setDrawCircles(false)
    setDrawValues(false)
    lineWidth = 1.5f
}

fun LayoutMainBinding.showBottomViews() {
    val minHeight = topContainer.minimumHeight

    if (minHeight == 0) {
        AutoExpandKeyboardUtils.expand(
            root.context,
            topContainer,
            toolbar,
            bottomFragment,
            scrollBelowText
        )
        allChartsLayout.layoutParams.height = topContainer.minimumHeight
    }
}