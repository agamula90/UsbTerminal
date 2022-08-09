package com.ismet.usbterminal

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ismet.usbterminal.data.PeriodicResponse
import com.ismet.usbterminalnew.R
import com.ismet.usbterminalnew.databinding.LayoutDialogMeasureBinding
import com.ismet.usbterminalnew.databinding.LayoutDialogOnOffBinding
import com.ismet.usbterminalnew.databinding.LayoutDialogOneCommandBinding
import com.ismet.usbterminalnew.databinding.LayoutEditorUpdatedBinding
import com.proggroup.areasquarecalculator.utils.AutoExpandKeyboardUtils
import org.achartengine.GraphicalView
import org.achartengine.chart.AbstractChart
import org.achartengine.chart.CombinedXYChart
import org.achartengine.chart.CubicLineChart
import org.achartengine.chart.PointStyle
import org.achartengine.model.XYMultipleSeriesDataset
import org.achartengine.model.XYSeries
import org.achartengine.renderer.XYMultipleSeriesRenderer
import org.achartengine.renderer.XYSeriesRenderer
import java.io.File

fun XYSeries.set(points: List<PointF>) {
    val pointsToAdd = points.toMutableList()
    val pointIndexesToRemove = mutableListOf<Int>()
    for (i in 0 until itemCount) {
        val x = getX(i).toFloat()
        val y = getY(i).toFloat()
        val currentPoint = PointF(x, y)
        val isPointExist = pointsToAdd.remove(currentPoint)
        if (!isPointExist) pointIndexesToRemove.add(i)
    }
    for (pointIndex in pointIndexesToRemove.reversed()) {
        remove(pointIndex)
    }
    for (point in pointsToAdd) {
        add(point.x.toDouble(), point.y.toDouble())
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
    if (!startsWith("(") || !endsWith(")")) return encodeToByteArray()
    val bytesEncoded = substring(1, lastIndex).split("-")
    return bytesEncoded.map { it.toInt(radix = 16).toByte() }.toByteArray()
}

private val temperaturePattern = "^@\\d+,\\d+(\\d+,\\d+,\\d+,\\d+),\\d+,\\d+,\\d+,\\d+,\\d+$".toRegex()

fun ByteArray.decodeToPeriodicResponse(): PeriodicResponse? = when {
    this.size == 7 && this[0].toString(radix = 16) == "FE" && this[1].toString(radix = 16) == "44" -> {
        PeriodicResponse.Co2(
            response = joinToString(separator = "-") { it.toString(radix = 16) },
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

fun File.readTextEnhanced(): String = readText().replace("\r", "")

fun ByteArray.decodeToStringEnhanced(): String {
    val periodicResponse = decodeToPeriodicResponse()
    return periodicResponse?.toString() ?: decodeToString()
}

fun XYMultipleSeriesRenderer.resetLabelValues() {
    yAxisMin = 0.0
    yAxisMax = 10.0
}

fun createXyCombinedChart(minutes: Int, seconds: Int): CombinedXYChart {
    val titles = arrayOf("ppm", "ppm", "ppm")

    val colors = intArrayOf(Color.BLACK, Color.RED, Color.BLUE)
    val renderer = XYMultipleSeriesRenderer()
    for (color in colors) {
        val r = XYSeriesRenderer()
        r.color = color
        r.pointStyle = PointStyle.CIRCLE
        r.lineWidth = 1.5f
        r.isFillPoints = false
        r.isDisplayChartValues = false
        renderer.addSeriesRenderer(r)
    }
    renderer.pointSize = 0f

    val xLabels = IntArray(4)
    var m = 0
    for (i in 0..3) {
        xLabels[i] = m
        m += minutes
    }

    var j = 0
    for (xLabel in xLabels) {
        if (xLabel == 0) {
            renderer.addXTextLabel(j.toDouble(), "")
        } else {
            renderer.addXTextLabel(j.toDouble(), "" + xLabel)
        }
        j += minutes * 60 / seconds
    }

    val maxX: Double = (3 * (minutes * 60 / seconds)).toDouble()

    renderer.apply {
        chartTitle = ""
        xTitle = "minutes"
        yTitle = "ppm"
        xAxisMin = 0.0
        xAxisMax = maxX
        resetLabelValues()
        axesColor = Color.LTGRAY
        labelsColor = Color.WHITE
        setXLabels(0)
        yLabels = 15
        labelsTextSize = 13f
        setShowGrid(true)
        setShowCustomTextGrid(true)
        setGridColor(Color.rgb(136, 136, 136))
        backgroundColor = Color.WHITE
        isApplyBackgroundColor = true
        margins = intArrayOf(0, 60, 0, 0)
        xLabelsAlign = Paint.Align.RIGHT
        setYLabelsAlign(Paint.Align.RIGHT)
        setYLabelsColor(0, Color.rgb(0, 171, 234))
        yLabelsVerticalPadding = -15f
        xLabelsPadding = -5f
        isZoomButtonsVisible = false
        setZoomEnabled(false, false)
        setPanEnabled(false, false)
        isZoomButtonsVisible = false
        isShowLegend = false
        isShowLabels = true
    }

    val dataset = XYMultipleSeriesDataset()
    for (title in titles) {
        val series = XYSeries(title, 0)
        dataset.addSeries(series)
    }

    renderer.scale = 1f
    val chartDefinitions = arrayOf(
        CombinedXYChart.XYCombinedChartDef(CubicLineChart.TYPE, 0),
        CombinedXYChart.XYCombinedChartDef(CubicLineChart.TYPE, 1),
        CombinedXYChart.XYCombinedChartDef(CubicLineChart.TYPE, 2)
    )
    return CombinedXYChart(dataset, renderer, chartDefinitions)
}

fun AbstractChart.attach(binding: LayoutEditorUpdatedBinding, toolbar: View): GraphicalView {
    val topContainer = binding.topContainer
    val minHeight = topContainer.minimumHeight

    if (minHeight == 0) {
        val textBelow = binding.scrollBelowText
        AutoExpandKeyboardUtils.expand(
            binding.root.context,
            topContainer,
            binding.bottomFragment,
            toolbar,
            textBelow
        )
        binding.allChartsLayout.layoutParams.height = topContainer.minimumHeight
    }

    val chartView = GraphicalView(binding.root.context, this)
    binding.chart.apply {
        removeAllViews()
        addView(
            chartView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
    chartView.repaint()
    return chartView
}