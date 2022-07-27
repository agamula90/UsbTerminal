package com.ismet.usbterminal

import android.graphics.PointF
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    handle: SavedStateHandle
): ViewModel() {
    val chartPoints = handle.getLiveData<List<PointF>>("chartPoints", emptyList())
    val maxY = handle.getLiveData("maxY",0)
    private var readChartJob: Job? = null

    fun readCharts(filePath: String){
        readChartJob?.cancel()
        readChartJob = viewModelScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            val lines = file.readLines()
            var startX = when {
                filePath.contains("R1") -> 1
                filePath.contains("R3") -> (lines.size + 1) * 2
                else -> lines.size + 1
            }
            var newMaxY = maxY.value!!
            val newChartPoints = chartPoints.value!!.toMutableList()
            for (line in lines) {
                if (line.isNotEmpty()) {
                    val arr = line.split(",").toTypedArray()
                    val co2 = arr[1].toDouble()
                    startX++
                    if (co2 >= newMaxY) {
                        newMaxY = if (newChartPoints.isEmpty()) {
                            (3 * co2).toInt()
                        } else {
                            (co2 + co2 * 15 / 100f).toInt()
                        }
                    }
                    newChartPoints.add(PointF(startX.toFloat(), co2.toFloat()))
                }
            }

            maxY.postValue(newMaxY)
            chartPoints.postValue(newChartPoints)
        }
    }
}