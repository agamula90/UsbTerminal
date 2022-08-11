package com.proggroup

import android.graphics.PointF
import java.io.File

fun File.readPoints(): List<PointF> {
    val lines = readLines()
    val points = mutableListOf<PointF>()
    var lastTime = -1
    for (line in lines) {
        val values: Array<String> = line.split(",").toTypedArray()
        if (values.size >= 2) {
            val parseTime = values[0].parseSeconds()
            if (parseTime < 0) {
                return emptyList()
            }
            if (lastTime != parseTime) {
                points.add(PointF(parseTime.toFloat(), values[1].toFloat()))
                lastTime = parseTime
            }
        }
    }
    return points
}

private fun String.parseSeconds(): Int {
    val splitValues = split(":").toTypedArray()
    val minutes = splitValues[0].toInt()
    if (splitValues.size < 2) {
        return -1
    }
    val seconds = splitValues[1].substringBefore('.').toInt()

    return minutes * 60 + seconds
}

fun File.calculateSquare(): Float {
    val points = readPoints()

    return if (points.isEmpty()) {
        -1f
    } else points.calculateSquare()
}

fun List<PointF>.calculateSquare(): Float {
    val startIndex = findStartIndex()
    if (startIndex == -1) return -1f
    val yCached = get(startIndex).y
    val endIndex = findEndIndex(yCached)
    if (endIndex < startIndex) {
        return -1f
    }
    return subList(startIndex, endIndex + 1).zipWithNext().fold(0f) { acc, points ->
        val firstPoint = points.first
        val nextPoint = points.second
        acc + (firstPoint.y + nextPoint.y) / 2 * (nextPoint.x - firstPoint.x) - yCached * (nextPoint.x - firstPoint.x)
    }
}

/**
 * Search of first index of growing y.
 *
 * @param this Y points, first index is searched from.
 * @return Index of first growing value.
 */
private fun List<PointF>.findStartIndex(): Int {
    for (i in 0 until size - 1) {
        if (this[i].y < this[i + 1].y) {
            return i
        }
    }
    return -1
}

/**
 * Search of last index of growing y.
 *
 * @param this        Y points, last index is searched from.
 * @param cachedStartValue Cached value - search can be stopped, when we reach this value.
 * @return Index of last growing value.
 */
private fun List<PointF>.findEndIndex(cachedStartValue: Float): Int {
    val len = size
    if (this[len - 1].y > cachedStartValue) {
        this[len - 1].y = cachedStartValue
    }
    for (i in len - 1 downTo 0) {
        if (this[i].y == cachedStartValue || i < len - 1 && this[i].y < this[i + 1].y) {
            return i
        }
    }
    return -1
}