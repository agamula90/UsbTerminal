package com.ismet.usbterminal

import android.graphics.PointF
import org.achartengine.model.XYSeries

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