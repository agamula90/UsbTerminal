package com.ismet.usbterminal.main.chart

import android.graphics.Color
import android.util.SparseArray
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.ismet.usbterminalnew.R

fun LineChart.configure(squares: SparseArray<Float>) {

    //0, 50, 50, 0
    setExtraOffsets(30f, 40f, 60f, 10f)
    setDrawGridBackground(false)
    isDoubleTapToZoomEnabled = false
    description.isEnabled = false
    setTouchEnabled(true)
    isDragEnabled = true
    setScaleEnabled(true)
    setPinchZoom(true)

    val xMin = squares.keyAt(0)
    val xMax = squares.keyAt(squares.size() - 1)
    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        setDrawGridLines(false)
        textSize = 12f
        isEnabled = true
        axisMinimum = xMin.toFloat()
        axisMaximum = xMax.toFloat()
        setLabelCount(squares.size() + 1, true)
    }
    marker = ChartMarkerView(context, R.layout.chart_marker_view)

    val chartEntries = mutableListOf<Entry>()
    for (i in 0 until squares.size()) {
        val square = squares.valueAt(i)
        if (square >= 0) {
            chartEntries.add(Entry(square, (squares.keyAt(i) - xMin).toFloat()))
        }
    }

    data = LineData(
        LineDataSet(chartEntries, null /*"Square values"*/).apply {
            highLightColor = Color.GREEN
            color = Color.BLACK
            setCircleColor(Color.BLACK)
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            valueTextSize = 12f
            fillAlpha = 65
            fillColor = Color.BLACK
            setDrawFilled(false)
            setDrawCircleHole(true)
        }
    )

    axisLeft.apply {
        isEnabled = true
        setDrawGridLines(false)
        setDrawLabels(false)
        setDrawTopYLabelEntry(true)
        axisMaximum = squares.valueAt(squares.size() - 1) + 100
        axisMaximum = squares.valueAt(0) - 100
        //TODO this conflicts with setaxisminimum call, maybe we'll want set draw sero line call
        //setStartAtZero(true);
        setDrawLimitLinesBehindData(true)
    }
    axisRight.isEnabled = false

    legend.apply {
        form = Legend.LegendForm.LINE
        isEnabled = false
    }
}