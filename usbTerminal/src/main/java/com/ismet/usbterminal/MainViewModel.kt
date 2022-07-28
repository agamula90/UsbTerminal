package com.ismet.usbterminal

import android.app.Application
import android.graphics.PointF
import android.os.Environment
import android.preference.PreferenceManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ismet.usbterminal.data.AppData
import com.ismet.usbterminal.data.PowerCommand
import com.ismet.usbterminal.data.PowerState
import com.ismet.usbterminal.data.PrefConstants
import com.ismet.usbterminal.mainscreen.powercommands.PowerCommandsFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.xgouchet.texteditor.common.TextFileUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import javax.inject.Inject

const val CHART_INDEX_UNSELECTED = -1
private const val SEND_TEMPERATURE_OR_CO2_DELAY = 1000L
const val CO2_REQUEST = "(FE-44-00-08-02-9F-25)"
private const val DATE_FORMAT = "yyyyMMdd"
private const val TIME_FORMAT = "HHmmss"
private const val DELIMITER = "_"
val FORMATTER = SimpleDateFormat("${DATE_FORMAT}${DELIMITER}${TIME_FORMAT}")

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    handle: SavedStateHandle
): ViewModel() {
    private val cacheFilesDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val app = application as EToCApplication
    private val prefs = PreferenceManager.getDefaultSharedPreferences(app)

    val events = Channel<MainEvent>(Channel.UNLIMITED)
    val chartPoints = handle.getLiveData<List<PointF>>("chartPoints", emptyList())
    val maxY = handle.getLiveData("maxY",0)

    private var shouldSendTemperatureRequest = true
    private var readChartJob: Job? = null
    private var sendTemperatureOrCo2Job: Job? = null
    private var sendWaitForCoolingJob: Job? = null
    private var readCommandsJob: Job? = null
    var chartDate: String = ""
    var chartIdx = 0
    var subDirDate: String = ""

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
                    delay(50)
                    maxY.postValue(newMaxY)
                    chartPoints.postValue(newChartPoints)
                }
            }
            events.send(MainEvent.ShowToast("File reading done"))
        }
        return newChartIndex
    }

    fun startSendingTemperatureOrCo2Requests() {
        sendTemperatureOrCo2Job?.cancel()
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

    fun readCommandsFromFile(file: File, shouldUseRecentDirectory: Boolean, runningTime: Long, oneLoopTime: Long) {
        readCommandsFromText(TextFileUtils.readTextFile(file), shouldUseRecentDirectory, runningTime, oneLoopTime)
    }

    fun readCommandsFromText(text: String?, shouldUseRecentDirectory: Boolean, runningTime: Long, oneLoopTime: Long) {
        if (text != null && text.isNotEmpty()) {
            stopSendingTemperatureOrCo2Requests()
            val commands: Array<String>
            val delimiter = "\n"
            commands = text.split(delimiter).toTypedArray()
            val simpleCommands: MutableList<String> = ArrayList()
            val loopCommands: MutableList<String> = ArrayList()
            var isLoop = false
            var loopcmd1Idx = -1
            var loopcmd2Idx = -1
            var autoPpm = false
            for (commandIndex in commands.indices) {
                val command = commands[commandIndex]
                if (command != "" && command != "\n") {
                    if (command.contains("loop")) {
                        isLoop = true
                        var lineNos = command.replace("loop", "")
                        lineNos = lineNos.replace("\n", "")
                        lineNos = lineNos.replace("\r", "")
                        lineNos = lineNos.trim { it <= ' ' }
                        val line1 = lineNos.substring(
                            0, lineNos.length
                                    / 2
                        )
                        val line2 = lineNos.substring(
                            lineNos.length / 2,
                            lineNos.length
                        )
                        loopcmd1Idx = line1.toInt() - 1
                        loopcmd2Idx = line2.toInt() - 1
                    } else if (command == "autoppm") {
                        autoPpm = true
                    } else if (isLoop) {
                        if (commandIndex == loopcmd1Idx) {
                            loopCommands.add(command)
                        } else if (commandIndex == loopcmd2Idx) {
                            loopCommands.add(command)
                            isLoop = false
                        }
                    } else {
                        simpleCommands.add(command)
                    }
                }
            }
            readCommandsJob?.cancel()
            readCommandsJob = viewModelScope.launch(Dispatchers.IO) {
                delay(300)
                if (shouldUseRecentDirectory) {
                    val ppm = prefs.getInt(PrefConstants.KPPM, -1)
                    //cal directory
                    if (ppm != -1) {
                        val directory = File(Environment.getExternalStorageDirectory(), AppData.CAL_FOLDER_NAME)
                        val directoriesInside = directory.listFiles { pathname -> pathname.isDirectory }
                        if (directoriesInside != null && directoriesInside.isNotEmpty()) {
                            var recentDir: File? = null
                            for (dir in directoriesInside) {
                                if (recentDir == null || dir.lastModified() > recentDir.lastModified()) {
                                    recentDir = dir
                                }
                            }
                            val name = recentDir!!.name
                            val tokenizer = StringTokenizer(name, DELIMITER)
                            tokenizer.nextToken()
                            // format of directory name is:
                            // MES/CAL + ${DELIMITER} + ${DATE_FORMAT} + ${DELIMITER} + ${TIME_FORMAT} +
                            // ${DELIMITER} + ${USER_COMMENT}
                            val date = tokenizer.nextToken()
                            val time = tokenizer.nextToken()
                            subDirDate = date + DELIMITER + time
                        }
                    }
                }
                val isAuto = prefs.getBoolean(PrefConstants.IS_AUTO, false)
                if (isAuto) {
                    repeat(3) {
                        processChart(runningTime, oneLoopTime, simpleCommands, loopCommands)
                    }
                } else {
                    processChart(runningTime, oneLoopTime, simpleCommands, loopCommands)
                }

                if (isAuto && autoPpm && !prefs.getBoolean(PrefConstants.SAVE_AS_CALIBRATION, false)) {
                    events.send(MainEvent.InvokeAutoCalculations)
                }

                startSendingTemperatureOrCo2Requests()
                events.send(MainEvent.UpdateTimerRunning(false))
                events.send(MainEvent.ShowToast("Timer Stopped"))
            }
        } else {
            events.offer(MainEvent.ShowToast("File not found"))
        }
    }

    private suspend fun processChart(future: Long, delay: Long, simpleCommands: List<String>, loopCommands: List<String>) {
        for (i in simpleCommands.indices) {
            if (simpleCommands[i].contains("delay")) {
                val delayC = simpleCommands[i].replace("delay", "").trim { it <= ' ' }.toLong()
                delay(delayC)
            } else {
                events.send(MainEvent.WriteToUsb(simpleCommands[i]))
            }
        }
        events.send(MainEvent.UpdateTimerRunning(true))

        val len = future / delay
        var count: Long = 0
        if (loopCommands.isNotEmpty()) {
            while (count < len) {
                events.send(MainEvent.IncReadingCount)
                events.send(MainEvent.WriteToUsb(loopCommands[0]))
                val half_delay = delay / 2
                delay(half_delay)
                if (loopCommands.size > 1) {
                    events.send(MainEvent.WriteToUsb(loopCommands[1]))
                    delay(half_delay)
                }

                count++
            }
        }
    }

    fun cacheBytesFromUsb(bytes: ByteArray) = viewModelScope.launch {
        withContext(cacheFilesDispatcher) {
            val strH = String.format(
                "%02X%02X", bytes[3],
                bytes[4]
            )
            val co2 = strH.toInt(16)
            val ppm: Int = prefs.getInt(PrefConstants.KPPM, -1)
            val volumeValue: Int = prefs.getInt(PrefConstants.VOLUME, -1)
            val volume = "_" + if (volumeValue == -1) "" else "" +
                    volumeValue
            val ppmPrefix = if (ppm == -1) {
                "_"
            } else {
                "_$ppm"
            }
            val str_uc: String = prefs.getString(PrefConstants.USER_COMMENT, "")!!
            val fileName: String
            val dirName: String
            val subDirName: String
            if (ppmPrefix == "_") {
                dirName = AppData.MES_FOLDER_NAME
                fileName = "MES_" + chartDate +
                        volume + "_R" + chartIdx + "" +
                        ".csv"
                subDirName = "MES_" + subDirDate + "_" +
                        str_uc
            } else {
                dirName = AppData.CAL_FOLDER_NAME
                fileName = ("CAL_" + chartDate +
                        volume + ppmPrefix + "_R" + chartIdx
                        + ".csv")
                subDirName = "CAL_" + subDirDate + "_" +
                        str_uc
            }
            try {
                var dir = File(Environment.getExternalStorageDirectory(), dirName)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                dir = File(dir, subDirName)
                if (!dir.exists()) {
                    dir.mkdir()
                }
                val formatter = SimpleDateFormat("mm:ss.S", Locale.ENGLISH)
                val file = File(dir, fileName)
                if (!file.exists()) {
                    file.createNewFile()
                }
                val preFormattedTime = formatter.format(Date())
                val arr = preFormattedTime.split("\\.").toTypedArray()
                var formattedTime = ""
                if (arr.size == 1) {
                    formattedTime = arr[0] + ".0"
                } else if (arr.size == 2) {
                    formattedTime = arr[0] + "." + arr[1].substring(0, 1)
                }
                val fos = FileOutputStream(file, true)
                val writer = BufferedWriter(OutputStreamWriter(fos))
                writer.write("$formattedTime,$co2\n")
                writer.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}