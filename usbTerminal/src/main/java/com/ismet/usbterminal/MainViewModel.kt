package com.ismet.usbterminal

import android.content.SharedPreferences
import android.graphics.PointF
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.util.SparseArray
import androidx.core.util.Pair
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ismet.usbterminal.data.*
import com.ismet.usbterminal.di.AccessoryOperationDispatcher
import com.ismet.usbterminal.di.CacheAccessoryOutputOnMeasureDispatcher
import com.ismet.usbterminal.powercommands.FilePowerCommandsFactory
import com.ismet.usbterminal.powercommands.LocalPowerCommandsFactory
import com.ismet.usbterminal.powercommands.PowerCommandsFactory
import com.ismet.usbterminal.utils.GraphPopulatorUtils
import com.ismet.usbterminal.utils.Utils
import com.ismet.usbterminalnew.R
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
import javax.inject.Inject
import javax.inject.Named

const val CHART_INDEX_UNSELECTED = -1
private const val SEND_TEMPERATURE_OR_CO2_DELAY = 1000L
const val CO2_REQUEST = "(FE-44-00-08-02-9F-25)"
private const val DATE_FORMAT = "yyyyMMdd"
private const val TIME_FORMAT = "HHmmss"
private const val DELIMITER = "_"
private const val MAX_CHARTS = 3

val FORMATTER = SimpleDateFormat("${DATE_FORMAT}${DELIMITER}${TIME_FORMAT}")
private val DATE_TIME_FORMAT = SimpleDateFormat("MM.dd.yyyy HH:mm:ss")

@HiltViewModel
class MainViewModel @Inject constructor(
    @AccessoryOperationDispatcher(operationType = "read") private val readDispatcher: CoroutineDispatcher,
    @AccessoryOperationDispatcher(operationType = "write") private val writeDispatcher: CoroutineDispatcher,
    @CacheAccessoryOutputOnMeasureDispatcher private val cacheDispatcher: CoroutineDispatcher,
    private val prefs: SharedPreferences,
    handle: SavedStateHandle
): ViewModel() {
    private var lastTimePressed: Long = 0
    private var isPowerPressed = false
    private var borderCoolingTemperature = 80
    private var isPreLooping = false
    private var currentTemperatureRequest = prefs.getString(PrefConstants.ON2, "/5H750R")!!
    private var shouldSendTemperatureRequest = true
    private var readChartJob: Job? = null
    private var sendTemperatureOrCo2Job: Job? = null
    private var sendWaitForCoolingJob: Job? = null
    private var readCommandsJob: Job? = null
    private var chartDate: String = ""
    private var subDirDate: String = ""
    private var currentClearOptions = mutableSetOf<String>()

    val powerCommandsFactory: PowerCommandsFactory
    val events = Channel<MainEvent>(Channel.UNLIMITED)
    val charts = MutableLiveData(List(MAX_CHARTS) { Chart(it, emptyList()) })
    val temperatureShift = handle.getLiveData("temperatureShift", 0)
    val buttonOn1Properties = handle.getLiveData("buttonOn1", ButtonProperties.byButtonIndex(prefs, 0))
    val buttonOn2Properties = handle.getLiveData("buttonOn2", ButtonProperties.byButtonIndex(prefs, 1))
    val buttonOn3Properties = handle.getLiveData("buttonOn3", ButtonProperties.byButtonIndex(prefs, 2))
    val buttonOn4Properties = handle.getLiveData("buttonOn4", ButtonProperties.byButtonIndex(prefs, 3))
    val buttonOn5Properties = handle.getLiveData("buttonOn5", ButtonProperties.byButtonIndex(prefs, 4))
    val buttonOn6Properties = handle.getLiveData("buttonOn6", ButtonProperties.byButtonIndex(prefs, 5))
    val powerProperties = handle.getLiveData("power", ButtonProperties.forPower())
    val maxY = handle.getLiveData("maxY",0)
    val currentChartIndex = handle.getLiveData("currentChartIndex", 0)
    val allClearOptions = listOf("New Measure", "Tx", "LM", "Chart 1", "Chart 2", "Chart 3")
    val checkedClearOptions = List(allClearOptions.size) { false }
    var isMeasuring = false

    init {
        val settingsFolder = File(Environment.getExternalStorageDirectory(), AppData.SYSTEM_SETTINGS_FOLDER_NAME)
        powerCommandsFactory = createPowerCommandsFactory(settingsFolder)
        events.offer(MainEvent.ShowToast(powerCommandsFactory.toString()))
        if (settingsFolder.exists()) readSettingsFolder(settingsFolder)
        initPowerAccordToItState()
        initGraphData()
        isPreLooping = true
        waitForCooling()
    }

    private fun createPowerCommandsFactory(settingsFolder: File): PowerCommandsFactory {
        val buttonPowerDataFile = File(settingsFolder, AppData.POWER_DATA)
        var powerData = ""
        if (buttonPowerDataFile.exists()) {
            powerData = TextFileUtils.readTextFile(buttonPowerDataFile).orEmpty()
        }
        return parseCommands(powerData)
    }

    private fun parseCommands(text: String): PowerCommandsFactory {
        var text = text
        text = text.replace("\r", "")
        val rows = text.split("\n").toTypedArray().filterNot { it.isEmpty() }
        val borderTemperatureString = "borderTemperature:"
        val onString = "on:"
        val offString = "off:"
        val borderTemperatures: MutableList<String> = ArrayList()
        val onCommands: MutableList<String> = ArrayList()
        val offCommands: MutableList<String> = ArrayList()
        val delimitedValues: List<String> = mutableListOf<String>().apply {
            add(borderTemperatureString)
            add(onString)
            add(offString)
        }
        var currentList: MutableList<String>? = null
        for (row in rows) {
            val index = delimitedValues.indexOf(row)
            if (index >= 0) {
                currentList = when (index) {
                    0 -> borderTemperatures
                    1 -> onCommands
                    2 -> offCommands
                    else -> null
                }
            } else {
                currentList?.add(row)
            }
        }
        var powerCommandsFactory: PowerCommandsFactory = LocalPowerCommandsFactory(PowerState.INITIAL)
        if (borderTemperatures.size != 1) {
            return powerCommandsFactory
        } else {
            try {
                borderCoolingTemperature = borderTemperatures[0].toInt()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return powerCommandsFactory
            }
            val onCommandsArr = SparseArray<PowerCommand>()
            for (onCommand in onCommands) {
                val parsedRow = parseCommand(onCommand)
                if (parsedRow != null) {
                    onCommandsArr.put(parsedRow.first, parsedRow.second)
                } else {
                    return powerCommandsFactory
                }
            }
            val offCommandsArr = SparseArray<PowerCommand>()
            for (offCommand in offCommands) {
                val parsedRow = parseCommand(offCommand)
                if (parsedRow != null) {
                    offCommandsArr.put(parsedRow.first, parsedRow.second)
                } else {
                    return powerCommandsFactory
                }
            }
            powerCommandsFactory = FilePowerCommandsFactory(PowerState.INITIAL, onCommandsArr, offCommandsArr)
        }
        return powerCommandsFactory
    }

    private fun parseCommand(text: String): Pair<Int, PowerCommand>? {
        var possibleResponses = text.split(";").filterNot { it.isEmpty() }
        val indexOfCommand: Int
        return try {
            indexOfCommand = possibleResponses[0].toInt()
            val delay = possibleResponses[1].toLong()
            val command = possibleResponses[2]
            if (possibleResponses.size > 3) {
                possibleResponses = possibleResponses.subList(3, possibleResponses.size)
                Pair(indexOfCommand, PowerCommand(Command(command), delay, possibleResponses.toTypedArray()))
            } else {
                Pair(indexOfCommand, PowerCommand(Command(command), delay, emptyArray()))
            }
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            null
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace()
            null
        }
    }

    private fun readSettingsFolder(settingsFolder: File) {
        val button1DataFile = File(settingsFolder, AppData.BUTTON1_DATA)
        if (button1DataFile.exists()) {
            val button1Data = TextFileUtils.readTextFile(button1DataFile)
            if (button1Data.isNotEmpty()) {
                val values = button1Data.split(AppData.SPLIT_STRING).toTypedArray()
                if (values.size == 4) {
                    val editor = prefs.edit()
                    editor.putString(PrefConstants.ON_NAME1, values[0])
                    editor.putString(PrefConstants.OFF_NAME1, values[1])
                    editor.putString(PrefConstants.ON1, values[2])
                    editor.putString(PrefConstants.OFF1, values[3])
                    editor.apply()
                }
            }
        }
        val button2DataFile = File(settingsFolder, AppData.BUTTON2_DATA)
        if (button2DataFile.exists()) {
            val button2Data = TextFileUtils.readTextFile(button2DataFile)
            if (button2Data.isNotEmpty()) {
                val values = button2Data.split(AppData.SPLIT_STRING).toTypedArray()
                if (values.size == 4) {
                    val editor = prefs.edit()
                    editor.putString(PrefConstants.ON_NAME2, values[0])
                    editor.putString(PrefConstants.OFF_NAME2, values[1])
                    editor.putString(PrefConstants.ON2, values[2])
                    editor.putString(PrefConstants.OFF2, values[3])
                    editor.apply()
                }
            }
        }
        val button3DataFile = File(settingsFolder, AppData.BUTTON3_DATA)
        if (button3DataFile.exists()) {
            val button3Data = TextFileUtils.readTextFile(button3DataFile)
            if (button3Data.isNotEmpty()) {
                val values = button3Data.split(AppData.SPLIT_STRING).toTypedArray()
                if (values.size == 4) {
                    val editor = prefs.edit()
                    editor.putString(PrefConstants.ON_NAME3, values[0])
                    editor.putString(PrefConstants.OFF_NAME3, values[1])
                    editor.putString(PrefConstants.ON3, values[2])
                    editor.putString(PrefConstants.OFF3, values[3])
                    editor.apply()
                }
            }
        }
        val temperatureShiftFolder = File(settingsFolder, AppData.TEMPERATURE_SHIFT_FILE)
        if (temperatureShiftFolder.exists()) {
            val temperatureData = TextFileUtils.readTextFile(temperatureShiftFolder)
            if (temperatureData.isNotEmpty()) {
                temperatureShift.value = try {
                    temperatureData.toInt()
                } catch (e: NumberFormatException) {
                    0
                }
            }
        }
        val measureDefaultFilesFile = File(settingsFolder, AppData.MEASURE_DEFAULT_FILES)
        if (measureDefaultFilesFile.exists()) {
            val measureFilesData = TextFileUtils.readTextFile(measureDefaultFilesFile)
            if (measureFilesData.isNotEmpty()) {
                val values = measureFilesData.split(AppData.SPLIT_STRING).toTypedArray()
                if (values.size == 3) {
                    val editor = prefs.edit()
                    editor.putString(
                        PrefConstants.MEASURE_FILE_NAME1,
                        values[0]
                    )
                    editor.putString(
                        PrefConstants.MEASURE_FILE_NAME2,
                        values[1]
                    )
                    editor.putString(
                        PrefConstants.MEASURE_FILE_NAME3,
                        values[2]
                    )
                    editor.apply()
                }
            }
        }
    }

    fun readChart(filePath: String) {
        readChartJob?.cancel()
        val currentCharts = charts.value!!.toMutableList()
        val newChartIndex = currentCharts.indexOfFirst { it.canBeRestoredFromFilePath(filePath) }

        if (newChartIndex == CHART_INDEX_UNSELECTED) {
            // events.offer(MainEvent.ShowToast("Required Log files not available"))
            readChartJob = null
            return
        }

        readChartJob = viewModelScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            val lines = file.readLines()
            var startX = maxOf(1, (lines.size + 1) * currentCharts[newChartIndex].id)
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
                    currentCharts[newChartIndex] = currentCharts[newChartIndex].copy(points = newChartPoints)
                    charts.postValue(currentCharts)
                }
            }
            events.send(MainEvent.ShowToast("File reading done"))
        }
    }

    //send periodically with 2 seconds delay
    private fun startSendingTemperatureOrCo2Requests() {
        sendTemperatureOrCo2Job?.cancel()
        sendTemperatureOrCo2Job = viewModelScope.launch(writeDispatcher) {
            while (isActive) {
                shouldSendTemperatureRequest = !shouldSendTemperatureRequest
                if (shouldSendTemperatureRequest) {
                    events.send(MainEvent.WriteToUsb(Command("/5J5R")))
                    delay(350)
                    events.send(MainEvent.WriteToUsb(Command(currentTemperatureRequest)))
                } else {
                    events.send(MainEvent.WriteToUsb(Command(CO2_REQUEST)))
                }
                delay(SEND_TEMPERATURE_OR_CO2_DELAY)
            }
        }
    }

    private fun stopSendingTemperatureOrCo2Requests() {
        sendTemperatureOrCo2Job?.cancel()
        sendTemperatureOrCo2Job = null
    }

    // request -> response
    // 5j1r is checking for controller availability
    // 5j1r is also for power off command
    // /5j1r -> 5j001
    // power is connected
    // 5j5r -> 5j101
    // 5h commands for heater
    //after power is on, heater button clicked
    //change to green if 5,0(0,0,0) if 1st zero received in response
    //Cooling command check temperature, if it's ok, then go next, otherwise wait...
    //InterruptActions do nothing, go next
    //power command - first do, then delay
    //maybe loop only for lines with no response
    private fun waitForCooling() {
        val command = if (isPreLooping) {
            PowerCommand(Command("/5J1R"), 1000)
        } else {
            powerCommandsFactory.currentCommand()!!
        }

        sendWaitForCoolingJob?.cancel()
        sendWaitForCoolingJob = viewModelScope.launch(writeDispatcher) {
            while (isActive) {
                val message = if (isPreLooping) {
                    command.command
                } else {
                    Command("/5H0000R")
                }
                val state = powerCommandsFactory.currentPowerState()
                if (state != PowerState.OFF) {
                    events.send(MainEvent.WriteToUsb(message))
                }
                delay((0.3 * command.delay).toLong())
                delay(command.delay)
            }
        }
    }

    private fun stopWaitForCooling() {
        sendWaitForCoolingJob?.cancel()
        sendWaitForCoolingJob = null
        events.offer(MainEvent.DismissCoolingDialog)
    }

    // return true if operation can succeed, false otherwise
    fun measure(
        delay: String,
        duration: String,
        isKnownPpm: Boolean,
        knownPpm: String,
        userComment: String,
        volume: String,
        isAutoMeasurement: Boolean,
        countMeasure: Int,
        editorText: String,
        editText1Text: String,
        editText2Text: String,
        editText3Text: String,
        isUseRecentDirectory: Boolean,
        checkedRadioButtonIndex: Int
    ): Boolean {
        val errorMessage = when {
            delay == "" || duration == "" -> "Please enter all values"
            delay.toInt() == 0 || duration.toInt() == 0 -> "zero is not allowed"
            isKnownPpm && knownPpm == "" -> "Please enter ppm values"
            userComment == "" -> "Please enter comments"
            volume == "" -> "Please enter volume values"
            editorText == "" && checkedRadioButtonIndex == -1 -> "Please enter command"
            else -> null
        }
        if (errorMessage != null) {
            events.offer(MainEvent.ShowToast(errorMessage))
            return false
        }
        isMeasuring = true
        val edit = prefs.edit()
        if (isKnownPpm) {
            val kppm = knownPpm.toInt()
            edit.putInt(PrefConstants.KPPM, kppm)
        } else {
            edit.remove(PrefConstants.KPPM)
        }
        edit.putString(PrefConstants.USER_COMMENT, userComment)
        val intVolume = volume.toInt()
        edit.putInt(PrefConstants.VOLUME, intVolume)
        edit.putBoolean(PrefConstants.IS_AUTO, isAutoMeasurement)
        edit.putBoolean(PrefConstants.SAVE_AS_CALIBRATION, isKnownPpm)
        val intDuration = duration.toInt()
        val intDelay = delay.toInt()
        val graphData = when(countMeasure) {
            0 -> {
                setCurrentChartIndex(0)
                GraphPopulatorUtils.createXYChart(intDuration, intDelay)
            }
            else -> null
        }
        events.offer(MainEvent.IncCountMeasure)
        edit.putInt(PrefConstants.DELAY, intDelay)
        edit.putInt(PrefConstants.DURATION, intDuration)
        val future = (intDuration * 60 * 1000).toLong()
        val delay_timer = (intDelay * 1000).toLong()

        val currentChartIndex: Int
        val readingCount: Int
        val charts = charts.value!!
        when {
            graphData != null || charts[0].points.isEmpty() -> {
                currentChartIndex = 0
                readingCount = 0
            }
            charts[1].points.isEmpty() -> {
                currentChartIndex = 1
                readingCount = intDuration * 60 / intDelay
            }
            charts[2].points.isEmpty() -> {
                currentChartIndex = 2
                readingCount = intDuration * 60
            }
            else -> {
                currentChartIndex = -1
                readingCount = -1
            }
        }
        if (currentChartIndex != -1) setCurrentChartIndex(currentChartIndex)
        if (readingCount != -1) events.offer(MainEvent.SetReadingCount(readingCount))
        edit.putString(PrefConstants.MEASURE_FILE_NAME1, editText1Text)
        edit.putString(PrefConstants.MEASURE_FILE_NAME2, editText2Text)
        edit.putString(PrefConstants.MEASURE_FILE_NAME3, editText3Text)
        edit.apply()

        if (graphData != null) events.offer(MainEvent.UpdateGraphData(graphData))
        if (checkedRadioButtonIndex == -1) {
            readCommandsFromText(
                text = editorText,
                shouldUseRecentDirectory = isUseRecentDirectory,
                runningTime = future,
                oneLoopTime = delay_timer
            )
            return true
        }

        val filePath = when (checkedRadioButtonIndex) {
            0 -> editText1Text
            1 -> editText2Text
            2 -> editText3Text
            else -> throw IllegalArgumentException("Can't handle chart index")
        }

        readCommandsFromFile(
            file = File(
                File(
                    Environment.getExternalStorageDirectory(),
                    AppData.SYSTEM_SETTINGS_FOLDER_NAME
                ),
                filePath
            ),
            shouldUseRecentDirectory = isUseRecentDirectory,
            runningTime = future,
            oneLoopTime = delay_timer
        )
        return true
    }

    private fun readCommandsFromFile(file: File, shouldUseRecentDirectory: Boolean, runningTime: Long, oneLoopTime: Long) {
        readCommandsFromText(TextFileUtils.readTextFile(file), shouldUseRecentDirectory, runningTime, oneLoopTime)
    }

    private fun readCommandsFromText(text: String?, shouldUseRecentDirectory: Boolean, runningTime: Long, oneLoopTime: Long) {
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
                isMeasuring = false
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
                events.send(MainEvent.WriteToUsb(Command(simpleCommands[i])))
            }
        }

        val len = future / delay
        var count: Long = 0
        if (loopCommands.isNotEmpty()) {
            while (count < len) {
                events.send(MainEvent.IncReadingCount)
                events.send(MainEvent.WriteToUsb(Command(loopCommands[0])))
                val half_delay = delay / 2
                delay(half_delay)
                if (loopCommands.size > 1) {
                    events.send(MainEvent.WriteToUsb(Command(loopCommands[1])))
                    delay(half_delay)
                }

                count++
            }
        }
    }

    private fun cacheBytesFromUsbWhenMeasurePressed(bytes: ByteArray) = viewModelScope.launch(cacheDispatcher) {
        val strH = String.format("%02X%02X", bytes[3], bytes[4])
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
                    volume + "_R" + (currentChartIndex.value!! + 1) + "" +
                    ".csv"
            subDirName = "MES_" + subDirDate + "_" +
                    str_uc
        } else {
            dirName = AppData.CAL_FOLDER_NAME
            fileName = ("CAL_" + chartDate +
                    volume + ppmPrefix + "_R" + (currentChartIndex.value!! + 1)
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

    fun onButton1Click() {
        onClick(0)
    }

    private fun onClick(index: Int) {
        if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
            return
        }
        val command = onPrePullStopped(index)
        val nowTime = SystemClock.uptimeMillis()
        val timeElapsed = Utils.elapsedTimeForSendRequest(nowTime, lastTimePressed)
        if (timeElapsed) {
            lastTimePressed = nowTime
            stopSendingTemperatureOrCo2Requests()
        }
        events.offer(MainEvent.WriteToUsb(Command(command)))
        if (timeElapsed) {
            viewModelScope.launch(Dispatchers.IO) {
                delay(1000)
                if (powerCommandsFactory.currentPowerState() == PowerState.ON) {
                    startSendingTemperatureOrCo2Requests()
                }
                onPostPullStarted(index)
            }
        }
    }

    private fun onPrePullStopped(index: Int): String {
        val buttonProperties = when(index) {
            0 -> buttonOn1Properties
            1 -> buttonOn2Properties
            2 -> buttonOn3Properties
            3 -> buttonOn4Properties
            4 -> buttonOn5Properties
            5 -> buttonOn6Properties
            else -> {
                return ""
            }
        }

        val isActivated = buttonProperties.value!!.isActivated
        val alpha = when {
            isActivated && index.isHighlighteable() -> 0.6f
            else -> buttonProperties.value!!.alpha
        }

        val command = when(index) {
            0, 3 -> {
                val command: String //"/5H1000R";
                if (!isActivated) {
                    command = prefs.getString(PrefConstants.ON1, "")!!
                } else {
                    command = prefs.getString(PrefConstants.OFF1, "")!!
                }
                command
            }
            1, 4 -> {
                //this is heater
                val command: String //"/5H1000R";
                val defaultValue: String
                val prefName: String
                if (!isActivated) {
                    prefName = PrefConstants.OFF2
                    defaultValue = "/5H0000R"
                    command = prefs.getString(PrefConstants.ON2, "")!!
                } else {
                    prefName = PrefConstants.ON2
                    defaultValue = "/5H750R"
                    command = prefs.getString(PrefConstants.OFF2, "")!!
                }
                currentTemperatureRequest = prefs.getString(prefName, defaultValue)!!
                command
            }
            2, 5 -> {
                if (!isActivated) {
                    prefs.getString(PrefConstants.ON3, "")!!
                } else {
                    prefs.getString(PrefConstants.OFF3, "")!!
                }
            }
            else -> ""
        }

        buttonProperties.value = buttonProperties.value!!.copy(isActivated = !isActivated, alpha = alpha)
        return command
    }

    private fun Int.isHighlighteable() = this < 3

    private fun onPostPullStarted(index: Int) {
        if (index.isHighlighteable()) {
            val buttonProperties = when(index) {
                0 -> buttonOn1Properties
                1 -> buttonOn2Properties
                2 -> buttonOn3Properties
                else -> return
            }
            val isActivated = buttonProperties.value!!.isActivated
            val background = if (!isActivated) R.drawable.button_drawable else R.drawable.power_on_drawable
            buttonProperties.postValue(buttonProperties.value!!.copy(alpha = 1f, background = background))
        }
    }

    // true if success, false otherwise
    fun changeButton1PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON1, persistedInfo.command)
            edit.putString(PrefConstants.OFF1, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME1, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME1, persistedInfo.activatedText)
            edit.apply()
            buttonOn1Properties.value = buttonOn1Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
            buttonOn4Properties.value = buttonOn4Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
        }
    }

    fun onButton2Click() {
        onClick(1)
    }

    fun changeButton2PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON2, persistedInfo.command)
            edit.putString(PrefConstants.OFF2, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME2, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME2, persistedInfo.activatedText)
            edit.apply()
            val isActivated = buttonOn2Properties.value!!.isActivated
            val defaultValue: String
            val prefName: String
            if (!isActivated) {
                prefName = PrefConstants.ON2
                defaultValue = "/5H750R"
            } else {
                prefName = PrefConstants.OFF2
                defaultValue = "/5H0000R"
            }
            currentTemperatureRequest = prefs.getString(prefName, defaultValue)!!

            buttonOn2Properties.value = buttonOn2Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
            buttonOn5Properties.value = buttonOn5Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
        }
    }

    fun onButton3Click() {
        onClick(2)
    }

    fun changeButton3PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON3, persistedInfo.command)
            edit.putString(PrefConstants.OFF3, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME3, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME3, persistedInfo.activatedText)
            edit.apply()
            buttonOn3Properties.value = buttonOn3Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
            buttonOn6Properties.value = buttonOn6Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
        }
    }

    fun onButton4Click() {
        onClick(3)
    }

    fun changeButton4PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON1, persistedInfo.command)
            edit.putString(PrefConstants.OFF1, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME1, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME1, persistedInfo.activatedText)
            edit.apply()
            buttonOn1Properties.value = buttonOn1Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
            buttonOn4Properties.value = buttonOn4Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
        }
    }

    fun onButton5Click() {
        onClick(4)
    }

    fun changeButton5PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON2, persistedInfo.command)
            edit.putString(PrefConstants.OFF2, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME2, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME2, persistedInfo.activatedText)
            edit.apply()
            val isActivated = buttonOn2Properties.value!!.isActivated
            val defaultValue: String
            val prefName: String
            if (!isActivated) {
                prefName = PrefConstants.ON2
                defaultValue = "/5H750R"
            } else {
                prefName = PrefConstants.OFF2
                defaultValue = "/5H0000R"
            }
            currentTemperatureRequest = prefs.getString(prefName, defaultValue)!!

            buttonOn2Properties.value = buttonOn2Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
            buttonOn5Properties.value = buttonOn5Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
        }
    }

    fun onButton6Click() {
        onClick(5)
    }

    fun changeButton6PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON3, persistedInfo.command)
            edit.putString(PrefConstants.OFF3, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME3, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME3, persistedInfo.activatedText)
            edit.apply()
            buttonOn3Properties.value = buttonOn3Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
            buttonOn6Properties.value = buttonOn6Properties.value!!.copy(
                text = persistedInfo.text,
                activatedText = persistedInfo.activatedText
            )
        }
    }

    fun onSendClick() {
        if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
            return
        }
        val nowTime = SystemClock.uptimeMillis()
        val timeElapsed = Utils.elapsedTimeForSendRequest(nowTime, lastTimePressed)
        if (timeElapsed) {
            lastTimePressed = nowTime
            stopSendingTemperatureOrCo2Requests()
        }
        events.offer(MainEvent.SendCommandsFromEditor)
    }

    fun onCurrentChartWasModified(wasModified: Boolean) {
        val currentDate = Date()
        if (wasModified) {
            chartDate = DATE_TIME_FORMAT.format(currentDate)
            charts.value!![currentChartIndex.value!!].tempFilePath = chartDate
        }
        if (subDirDate.isEmpty()) {
            subDirDate = DATE_TIME_FORMAT.format(currentDate)
        }
    }

    fun reset() {
        subDirDate = ""
        charts.value!!.forEach { it.tempFilePath = null }
    }

    fun setCurrentChartIndex(index: Int) {
        currentChartIndex.value = index
    }

    fun addClearOption(option: String) {
        currentClearOptions.add(option)
    }

    fun removeClearOption(option: String) {
        currentClearOptions.remove(option)
    }

    fun onClearDialogDismissed() {
        currentClearOptions.clear()
    }

    fun clearCheckedOptions() {
        val currentCharts = charts.value!!.toMutableList()
        if (currentClearOptions.contains("Tx")) {
            events.offer(MainEvent.ClearEditor)
        }
        if (currentClearOptions.contains("LM")) {
            events.offer(MainEvent.ClearOutput)
        }
        if (currentClearOptions.contains("Chart 1")) {
            currentCharts[0] = currentCharts[0].copy(points = emptyList())
            val tempFilePath = currentCharts[0].tempFilePath
            if (tempFilePath != null) {
                Utils.deleteFiles(tempFilePath, "_R1")
            }
        }
        if (currentClearOptions.contains("Chart 2")) {
            currentCharts[1] = currentCharts[1].copy(points = emptyList())
            val tempFilePath = currentCharts[1].tempFilePath
            if (tempFilePath != null) {
                Utils.deleteFiles(tempFilePath, "_R2")
            }
        }
        if (currentClearOptions.contains("Chart 3")) {
            currentCharts[2] = currentCharts[2].copy(points = emptyList())
            val tempFilePath = currentCharts[2].tempFilePath
            if (tempFilePath != null) {
                Utils.deleteFiles(tempFilePath, "_R3")
            }
        }
        if (currentClearOptions.contains("New Measure")) {
            reset()
            events.offer(MainEvent.ClearData)
        }
        charts.value = currentCharts
    }

    fun onPowerClick() {
        when (powerCommandsFactory.currentPowerState()) {
            PowerState.OFF -> {
                powerProperties.value = powerProperties.value!!.copy(isEnabled = false)
                //make power on
                //"/5H0000R" "respond as ->" "@5,0(0,0,0,0),750,25,25,25,25"
                // 0.5 second wait -> repeat
                // "/5J5R" "respond as ->" "@5J4"
                // 1 second wait ->
                // "(FE............)" "respond as ->" "lala"
                // 2 second wait ->
                // "/1ZR" "respond as ->" "blasad" -> power on
                isPowerPressed = true
                powerProperties.value = powerProperties.value!!.copy(alpha = 0.6f)
                powerCommandsFactory.moveStateToNext()
                viewModelScope.launch(writeDispatcher) {
                    sendRequest()
                    //simulateClick2();
                }
            }
            PowerState.ON -> {
                powerProperties.value = powerProperties.value!!.copy(isEnabled = false)
                //make power off
                //interrupt all activities by software (mean measure process etc)
                // 1 second wait ->
                // "/5H0000R" "respond as ->" "@5,0(0,0,0,0),750,25,25,25,25"
                // around 75C -> "/5J5R" -> "@5J5" -> then power off
                // bigger, then
                //You can do 1/2 second for the temperature and 1/2 second for the power and then co2
                isPowerPressed = true
                powerProperties.value = powerProperties.value!!.copy(alpha = 0.6f)
                val isButton2Activated = buttonOn2Properties.value!!.isActivated
                viewModelScope.launch(writeDispatcher) {
                    if (isButton2Activated) {
                        onButton2Click()
                        delay(1200)
                        powerCommandsFactory.moveStateToNext()
                        sendRequest()
                    } else {
                        powerCommandsFactory.moveStateToNext()
                        sendRequest()
                    }
                    //simulateClick1();
                }
            }
            else -> {
                //do nothing
            }
        }
    }

    private suspend fun simulateClick2() {
        delay(800)
        val temperatureData = "@5,0(0,0,0,0),750,25,25,25,25"
        simulateResponse(temperatureData)
        delay(800)
        simulateResponse("@5J001 ")
        delay(1400)
        simulateResponse("@5J101 ")
        delay(1800)
        simulateResponse("255")
        delay(600)
        simulateResponse("1ZR")
    }

    private suspend fun simulateClick1() {
        delay(3800)
        //temperature out of range
        var temperatureData = "@5,0(0,0,0,0),25,750,25,25,25"
        simulateResponse(temperatureData)
        delay(1200)
        //temperature out of range
        temperatureData = "@5,0(0,0,0,0),25,750,25,25,25"
        simulateResponse(temperatureData)
        delay(15000)
        //temperature in of range
        temperatureData = "@5,0(0,0,0,0),25,74,25,25,25"
        simulateResponse(temperatureData)
        delay(4000)
        simulateResponse(null)
    }

    private suspend fun simulateResponse(response: String?) {
        val notNullResponse = response.orEmpty()

        if (isPowerPressed) {
            handleResponse(notNullResponse)
        } else {
            val powerState = powerCommandsFactory.currentPowerState()
            if (powerState == PowerState.INITIAL) {
                isPreLooping = false
                stopWaitForCooling()
                powerCommandsFactory.moveStateToNext()
            }
        }
    }

    private fun initPowerAccordToItState() {
        val text: String
        val background: Int
        when (powerCommandsFactory.currentPowerState()) {
            PowerState.OFF, PowerState.INITIAL -> {
                text = prefs.getString(PrefConstants.POWER_OFF_NAME, PrefConstants.POWER_OFF_NAME_DEFAULT)!!
                background = R.drawable.power_off_drawable
            }
            PowerState.ON -> {
                text = prefs.getString(PrefConstants.POWER_ON_NAME, PrefConstants.POWER_ON_NAME_DEFAULT)!!
                background = R.drawable.power_on_drawable
            }
            else -> {
                isPowerPressed = false
                powerProperties.postValue(powerProperties.value!!.copy(isEnabled = true))
                return
            }
        }
        isPowerPressed = false
        powerProperties.postValue(
            powerProperties.value!!.copy(
                isEnabled = true,
                text = text,
                activatedText = text,
                alpha = 1f,
                background = background
            )
        )
    }

    private fun initGraphData() {
        val delay = prefs.getInt(PrefConstants.DELAY, PrefConstants.DELAY_DEFAULT)
        val duration = prefs.getInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)
        if (!prefs.contains(PrefConstants.DELAY)) {
            val editor = prefs.edit()
            editor.putInt(PrefConstants.DELAY, PrefConstants.DELAY_DEFAULT)
            editor.putInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)
            editor.putInt(PrefConstants.VOLUME, PrefConstants.VOLUME_DEFAULT)
            editor.apply()
        }
        events.offer(MainEvent.UpdateGraphData(GraphPopulatorUtils.createXYChart(duration, delay)))
    }

    //TODO check if should run on 1 thread or not?
    private suspend fun sendRequest() {
        val currentCommand = powerCommandsFactory.currentCommand()
        val powerState = powerCommandsFactory.currentPowerState()
        val nextPowerState = powerCommandsFactory.nextPowerState()
        when {
            powerState == PowerState.OFF_INTERRUPTING -> {
                handleResponse(response = "")
            }
            powerState == PowerState.OFF_WAIT_FOR_COOLING -> {
                waitForCooling()
                events.send(MainEvent.ShowWaitForCoolingDialog(
                    message = """  Cooling down.  Do not switch power off.  Please wait . . . ! ! !    
System will turn off automaticaly."""))
            }
            currentCommand == null -> {
                //ignore
            }
            currentCommand.hasResponses() || nextPowerState in arrayOf(PowerState.ON, PowerState.OFF) -> {
                events.send(MainEvent.WriteToUsb(currentCommand.command))
            }
            else -> {
                events.send(MainEvent.WriteToUsb(currentCommand.command))
                delay(currentCommand.delay)
                powerCommandsFactory.moveStateToNext()
                sendRequest()
            }
        }
    }

    private suspend fun handleResponse(response: String) {
        val previousPowerState = powerCommandsFactory.currentPowerState()
        val temperatureData = TemperatureData.parse(response)
        if (previousPowerState !in arrayOf(PowerState.ON_STAGE1, PowerState.ON_STAGE1_REPEAT, PowerState.ON_STAGE3A, PowerState.ON_STAGE3B, PowerState.ON_STAGE2B, PowerState.ON_STAGE2, PowerState.ON_STAGE3, PowerState.ON_STAGE4, PowerState.ON_RUNNING,
                PowerState.OFF_INTERRUPTING, PowerState.OFF_STAGE1, PowerState.OFF_WAIT_FOR_COOLING, PowerState.OFF_RUNNING, PowerState.OFF_FINISHING) ||
            (previousPowerState == PowerState.OFF_WAIT_FOR_COOLING && (!temperatureData.isCorrect || temperatureData.temperature1 > borderCoolingTemperature)) ||
            (previousPowerState == PowerState.OFF_STAGE1 && !temperatureData.isCorrect)) {
            return
        }
        if (previousPowerState == PowerState.OFF_STAGE1 && temperatureData.temperature1 <= borderCoolingTemperature) {
            powerCommandsFactory.moveStateToNext()
        }
        var currentCommand = powerCommandsFactory.currentCommand()
        powerCommandsFactory.moveStateToNext()
        val powerState = powerCommandsFactory.currentPowerState()
        if (previousPowerState !in arrayOf(PowerState.ON_STAGE1, PowerState.ON_STAGE1_REPEAT, PowerState.ON_STAGE3A, PowerState.ON_STAGE3B, PowerState.ON_STAGE2B, PowerState.ON_STAGE2, PowerState.ON_STAGE3, PowerState.ON_STAGE4, PowerState.ON_RUNNING)) {
            currentCommand = powerCommandsFactory.currentCommand()
        }

        when (previousPowerState) {
            PowerState.ON_STAGE1, PowerState.ON_STAGE1_REPEAT, PowerState.ON_STAGE3A, PowerState.ON_STAGE3B, PowerState.ON_STAGE2B, PowerState.ON_STAGE2, PowerState.ON_STAGE3, PowerState.ON_STAGE4, PowerState.ON_RUNNING -> {
                when {
                    currentCommand?.hasResponses() != true && powerState != PowerState.ON -> sendRequest()
                    currentCommand?.hasResponses() != true -> {
                        initPowerAccordToItState()
                        startSendingTemperatureOrCo2Requests()
                    }
                    !currentCommand.isResponseCorrect(response) -> {
                        events.send(MainEvent.ShowToast(message = buildWrongResponseMessage(response, currentCommand)))
                    }
                    powerState != PowerState.ON -> {
                        delay(currentCommand.delay)
                        sendRequest()
                    }
                    else -> {
                        initPowerAccordToItState()
                        startSendingTemperatureOrCo2Requests()
                    }
                }
            }
            PowerState.OFF_INTERRUPTING -> {
                stopSendingTemperatureOrCo2Requests()
                delay(currentCommand!!.delay * 2)
                if (powerState != PowerState.OFF) {
                    sendRequest()
                }
            }
            //we can get here only from local power factory
            PowerState.OFF_STAGE1 -> {
                delay(currentCommand!!.delay)
                if (powerState != PowerState.OFF) {
                    sendRequest()
                }
            }
            PowerState.OFF_WAIT_FOR_COOLING -> {
                stopWaitForCooling()
                delay(currentCommand!!.delay)
                if (powerState != PowerState.OFF) {
                    sendRequest()
                }
            }
            PowerState.OFF_RUNNING, PowerState.OFF_FINISHING -> {
                when {
                    powerState == PowerState.OFF -> {
                        initPowerAccordToItState()
                    }
                    currentCommand?.hasResponses() != true -> {
                        //ignore
                    }
                    currentCommand.isResponseCorrect(response) -> {
                        delay(currentCommand.delay)
                        sendRequest()
                    }
                    else -> {
                        events.send(MainEvent.ShowToast(message = buildWrongResponseMessage(response, currentCommand)))
                    }
                }
            }
            else -> {
                //do nothing
            }
        }
    }

    private fun buildWrongResponseMessage(response: String, command: PowerCommand): String {
        val responseBuilder = StringBuilder()
        for (possibleResponse in command.possibleResponses) {
            responseBuilder.append("\"$possibleResponse\" or ")
        }
        responseBuilder.delete(
            responseBuilder.length - 4, responseBuilder
                .length
        )
        return "Wrong response: Got - \"$response\".Expected - $responseBuilder"
    }

    fun onDataReceived(response: String, bytes: ByteArray) {
        if (bytes.size == 7 && isMeasuring) {
            cacheBytesFromUsbWhenMeasurePressed(bytes)
        }
        if (isPowerPressed) {
            viewModelScope.launch(readDispatcher) {
                handleResponse(response)
            }
        } else {
            val powerState = powerCommandsFactory.currentPowerState()
            if (powerState == PowerState.INITIAL) {
                isPreLooping = false
                stopWaitForCooling()
                powerCommandsFactory.moveStateToNext()
            }
        }
    }
}