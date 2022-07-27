package com.ismet.usbterminal

import android.graphics.PointF
import androidx.lifecycle.*
import com.ismet.usbterminal.mainscreen.tasks.MainEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

const val CHART_INDEX_UNSELECTED = -1

@HiltViewModel
class MainViewModel @Inject constructor(
    handle: SavedStateHandle
): ViewModel() {
    val events = Channel<MainEvent>(Channel.UNLIMITED)
    val chartPoints = handle.getLiveData<List<PointF>>("chartPoints", emptyList())
    val maxY = handle.getLiveData("maxY",0)
    private var readChartJob: Job? = null

    /**
     * return chart index, based on filePath input
     */
    fun readChart(filePath: String): Int {
        readChartJob?.cancel()
        val newChartIndex = when {
            filePath.contains("R1") -> 0
            filePath.contains("R2") -> 1
            filePath.contains("R3") -> 2
            else -> CHART_INDEX_UNSELECTED
        }
        if (newChartIndex == CHART_INDEX_UNSELECTED) {
            // events.offer(MainEvent.ShowToast("Required Log files not available"))
            readChartJob = null
            return newChartIndex
        }
        readChartJob = viewModelScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            val lines = file.readLines()
            var startX = maxOf(1, (lines.size + 1) * newChartIndex)
            var newMaxY = maxY.value!!
            val newChartPoints = mutableListOf<PointF>()
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
                    maxY.postValue(newMaxY)
                    chartPoints.postValue(newChartPoints)
                    delay(50)
                }
            }
            events.send(MainEvent.ShowToast("File reading done"))
        }
        return newChartIndex
    }
}