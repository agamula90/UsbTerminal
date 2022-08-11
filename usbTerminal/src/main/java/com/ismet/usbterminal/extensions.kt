package com.ismet.usbterminal

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.ismet.usbterminal.data.Charts
import com.ismet.usbterminal.data.PeriodicResponse
import com.ismet.usbterminalnew.R
import com.ismet.usbterminalnew.databinding.*
import com.proggroup.areasquarecalculator.utils.AutoExpandKeyboardUtils
import java.io.File

fun LineChart.set(charts: Charts) {
    axisLeft.axisMaximum = charts.maxY
    xAxis.axisMaximum = charts.maxX
    for (chart in charts.charts) {
        (data.dataSets[chart.id] as LineDataSet).values = chart.points.map { Entry(it.x, it.y) }
    }
    data.notifyDataChanged()
    notifyDataSetChanged()
    invalidate()
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
    if (!startsWith("(") || !endsWith(")")) return encodeToByteArray()
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
    decodeToString().matches(temperaturePattern) -> {
        val response = decodeToString()
        val temperatureParts = response.split(",")
        PeriodicResponse.Temperature(response, temperatureParts[6].toInt())
    }
    else -> null
}

//@5,0(0,0,0,0),180,25,25,25,25

fun File.readTextEnhanced(): String = readText().replace("\r", "")

fun ByteArray.decodeToStringEnhanced(): String {
    val periodicResponse = decodeToPeriodicResponse()
    return periodicResponse?.toString() ?: decodeToString()
}

fun LineChart.init() {
    description.isEnabled = false
    setTouchEnabled(false)
    setDrawGridBackground(false)
    isDragEnabled = false
    setScaleEnabled(false)
    setPinchZoom(false)
    val color = Color.rgb(136, 136, 136)
    xAxis.apply {
        gridColor = color
        setLabelCount(4, true)
        position = XAxis.XAxisPosition.BOTTOM
        axisMinimum = 0f
        axisMaximum = 9f
        textColor = Color.WHITE
    }
    axisRight.isEnabled = false
    axisLeft.apply {
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
    val topContainer = topContainer
    val minHeight = topContainer.minimumHeight

    if (minHeight == 0) {
        val textBelow = scrollBelowText
        AutoExpandKeyboardUtils.expand(
            root.context,
            topContainer,
            bottomFragment,
            toolbar,
            textBelow
        )
        allChartsLayout.layoutParams.height = topContainer.minimumHeight
    }
}