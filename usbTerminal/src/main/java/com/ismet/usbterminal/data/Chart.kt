package com.ismet.usbterminal.data

import android.graphics.PointF

data class Chart(val id: Int, val points: List<PointF>) {
    fun canBeRestoredFromFilePath(filePath: String) = filePath.endsWith("_R${id + 1}.csv")
}
