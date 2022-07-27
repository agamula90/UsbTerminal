package com.ismet.usbterminal

import android.app.Application
import android.graphics.PointF
import androidx.lifecycle.*
import com.ismet.usbterminal.data.PowerCommand
import com.ismet.usbterminal.data.PowerState
import com.ismet.usbterminal.mainscreen.powercommands.PowerCommandsFactory
import com.ismet.usbterminal.mainscreen.tasks.MainEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import javax.inject.Inject

const val CHART_INDEX_UNSELECTED = -1
private const val SEND_TEMPERATURE_OR_CO2_DELAY = 1000L
private const val CO2_REQUEST = "(FE-44-00-08-02-9F-25)"

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    handle: SavedStateHandle
): ViewModel() {
    private val app = application as EToCApplication

    val events = Channel<MainEvent>(Channel.UNLIMITED)
    val chartPoints = handle.getLiveData<List<PointF>>("chartPoints", emptyList())
    val maxY = handle.getLiveData("maxY",0)

    private var shouldSendTemperatureRequest = true
    private var readChartJob: Job? = null
    private var sendTemperatureOrCo2Job: Job? = null
    private var sendWaitForCoolingJob: Job? = null

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

    fun startSendingTemperatureOrCo2Requests() {
        sendTemperatureOrCo2Job?.cancel()
        //this should be run even when activity paused
        sendTemperatureOrCo2Job = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                shouldSendTemperatureRequest = !shouldSendTemperatureRequest
                if (shouldSendTemperatureRequest) {
                    events.send(MainEvent.WriteToUsb("/5J5R"))
                    delay(350)
                    events.send(MainEvent.WriteToUsb(app.currentTemperatureRequest))
                } else {
                    events.send(MainEvent.WriteToUsb(CO2_REQUEST))
                }
                delay(SEND_TEMPERATURE_OR_CO2_DELAY)
            }
        }
    }

    fun stopSendingTemperatureOrCo2Requests() {
        sendTemperatureOrCo2Job?.cancel()
        sendTemperatureOrCo2Job = null
    }

    fun waitForCooling() {
        val commandsFactory: PowerCommandsFactory = app.powerCommandsFactory

        val command = if (app.isPreLooping) {
            PowerCommand("/5J1R", 1000)
        } else {
            commandsFactory.currentCommand()
        }

        sendWaitForCoolingJob?.cancel()
        sendWaitForCoolingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val message = if (app.isPreLooping) {
                    command.command
                } else {
                    "/5H0000R"
                }
                val state = commandsFactory.currentPowerState()
                if (state != PowerState.OFF) {
                    events.send(MainEvent.WriteToUsb(message))
                    //TODO do we need next line?
                    //events.send(MainEvent.WriteToUsb(state))
                }
                delay((0.3 * command.delay).toLong())
                delay(command.delay)
            }
        }
    }

    fun stopWaitForCooling() {
        sendWaitForCoolingJob?.cancel()
        sendWaitForCoolingJob = null
    }
}