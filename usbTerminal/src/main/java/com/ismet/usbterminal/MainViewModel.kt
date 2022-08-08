package com.ismet.usbterminal

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.SystemClock
import androidx.lifecycle.*
import com.ismet.usbterminal.data.*
import com.ismet.usbterminal.di.AccessoryOperationDispatcher
import com.ismet.usbterminal.di.CacheAccessoryOutputOnMeasureDispatcher
import com.ismet.usbterminal.utils.GraphPopulatorUtils
import com.ismet.usbterminal.utils.Utils
import com.ismet.usbterminalnew.R
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

const val CHART_INDEX_UNSELECTED = -1
const val APPLICATION_SETTINGS = "AEToC_SYS_Files"
private const val ACCESSORY_SETTINGS = "AccessorySettings.json"
private const val BUTTON1 = "Button1.txt"
private const val BUTTON2 = "Button2.txt"
private const val BUTTON3 = "Button3.txt"
private const val BUTTON4 = "Button4.txt"
private const val BUTTON5 = "Button5.txt"
private const val BUTTON6 = "Button6.txt"
private const val MEASUREMENT = "MeasureFiles.txt"
private const val MEASUREMENT1_FILE_NAME = "MScript1.txt"
private const val MEASUREMENT2_FILE_NAME = "MScript2.txt"
private const val MEASUREMENT3_FILE_NAME = "MScript3.txt"
const val BUTTON_FILE_FORMAT_DELIMITER = ";"
private const val DATE_FORMAT = "yyyyMMdd"
private const val TIME_FORMAT = "HHmmss"
private const val DELIMITER = "_"
private const val MAX_CHARTS = 3
private const val DEFAULT_BUTTON_TEXT = "Command1"
private const val DEFAULT_BUTTON_ACTIVATED_TEXT = "Command2"

val FORMATTER = SimpleDateFormat("${DATE_FORMAT}${DELIMITER}${TIME_FORMAT}")
private val DATE_TIME_FORMAT = SimpleDateFormat("MM.dd.yyyy HH:mm:ss")

@HiltViewModel
class MainViewModel @Inject constructor(
    @AccessoryOperationDispatcher(operationType = "read") private val readDispatcher: CoroutineDispatcher,
    @AccessoryOperationDispatcher(operationType = "write") private val writeDispatcher: CoroutineDispatcher,
    @CacheAccessoryOutputOnMeasureDispatcher private val cacheDispatcher: CoroutineDispatcher,
    private val prefs: SharedPreferences,
    private val moshi: Moshi,
    handle: SavedStateHandle,
    @ApplicationContext val context: Context
): ViewModel() {
    private var lastTimePressed: Long = 0
    private var shouldSendTemperatureRequest = true
    private var readChartJob: Job? = null
    private var sendTemperatureOrCo2Job: Job? = null
    private var sendWaitForCoolingJob: Job? = null
    private var readCommandsJob: Job? = null
    private var chartDate: String = ""
    private var subDirDate: String = ""
    private var currentClearOptions = mutableSetOf<String>()
    private var changeableButtonClickedProperties: MutableLiveData<ButtonProperties?>? = null
    private var sendAndForgetButtonClickedProperties: MutableLiveData<ButtonProperties?>? = null
    var measureFileNames: List<String> = emptyList()
    private set

    val events = Channel<MainEvent>(Channel.UNLIMITED)
    val charts = MutableLiveData(List(MAX_CHARTS) { Chart(it, emptyList()) })
    private val applicationSettingsDirectory = File(Environment.getExternalStorageDirectory(), APPLICATION_SETTINGS)
    val accessorySettings = handle.getLiveData<AccessorySettings?>("accessorySettings")
    val buttonOn1Properties = handle.getLiveData<ButtonProperties?>("buttonOn1")
    val buttonOn2Properties = handle.getLiveData<ButtonProperties?>("buttonOn2")
    val buttonOn3Properties = handle.getLiveData<ButtonProperties?>("buttonOn3")
    val buttonOn4Properties = handle.getLiveData<ButtonProperties?>("buttonOn4")
    val buttonOn5Properties = handle.getLiveData<ButtonProperties?>("buttonOn5")
    val buttonOn6Properties = handle.getLiveData<ButtonProperties?>("buttonOn6")
    val powerProperties = handle.getLiveData("power", ButtonProperties.forPower())
    val measureProperties = handle.getLiveData("measure", ButtonProperties.forMeasure())
    private val allButtonProperties = listOf(
        buttonOn1Properties, buttonOn2Properties, buttonOn3Properties, buttonOn4Properties, buttonOn5Properties,
        buttonOn6Properties, powerProperties, measureProperties
    )
    val maxY = handle.getLiveData("maxY",0)
    val currentChartIndex = handle.getLiveData("currentChartIndex", 0)
    val allClearOptions = listOf("New Measure", "Tx", "LM", "Chart 1", "Chart 2", "Chart 3")
    val checkedClearOptions = List(allClearOptions.size) { false }
    var isMeasuring = false
    var fileObserver: FileObserver? = null
    private var onAccessorySettingsChanged: (AccessorySettings) -> Unit = {}
    private var pingJob: Job? = null
    private var isPowerInProgress = false
    private var isPowerWaitForLastResponse = false
    private var pendingAccessorySettings: AccessorySettings? = null

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            observeAppSettingsDirectoryUpdates()
        }
        initGraphData()
    }

    fun observeAppSettingsDirectoryUpdates() {
        fileObserver = object: FileObserver(applicationSettingsDirectory, getFileObservationMask()) {
            override fun onEvent(event: Int, path: String?) {
                checkAppSettings(path)
            }
        }.also { it.startWatching() }
        checkAppSettings()
    }

    private fun getFileObservationMask() = FileObserver.CREATE or FileObserver.MODIFY or FileObserver.DELETE

    private fun checkAppSettings(path: String? = null) {
        val corruptedFiles = mutableListOf<String>()
        if (path == null || path == ACCESSORY_SETTINGS) {
            try {
                val accessoryDirectory = File(applicationSettingsDirectory, ACCESSORY_SETTINGS)
                if (!accessoryDirectory.exists()) {
                    accessoryDirectory.createNewFile()
                    accessoryDirectory.writeText(
                        moshi.adapter(AccessorySettings::class.java)
                            .toJson(AccessorySettings.getDefault())
                    )
                }

                val oldAccessorySettings = accessorySettings.value
                val newAccessorySettings = moshi.adapter(AccessorySettings::class.java)
                    .fromJson(accessoryDirectory.readText())!!
                if (oldAccessorySettings == null) {
                    accessorySettings.value = newAccessorySettings
                    startPing()
                } else if (accessorySettings.value != newAccessorySettings) {
                    onAccessorySettingsChanged = { newSettings ->
                        accessorySettings.value = newSettings
                        startPing()
                    }
                    onPowerOffClick(newAccessorySettings)
                }
            } catch (_: Exception) {
                corruptedFiles.add(ACCESSORY_SETTINGS)
            }
        }
        val buttonChangeableFiles = when(path) {
            null -> listOf(BUTTON1, BUTTON2, BUTTON3)
            BUTTON1 -> listOf(BUTTON1)
            BUTTON2 -> listOf(BUTTON2)
            BUTTON3 -> listOf(BUTTON3)
            else -> emptyList()
        }.map { File(applicationSettingsDirectory, it) }
        for (file in buttonChangeableFiles) {
            try {
                var savable: FileSavable
                if (!file.exists()) {
                    file.createNewFile()
                    savable = FileSavable(DEFAULT_BUTTON_TEXT, "", DEFAULT_BUTTON_ACTIVATED_TEXT, "", file.name)
                    savable.save(false)
                }
                val content = file.readText().split(BUTTON_FILE_FORMAT_DELIMITER)
                require(content.size == 4)
                val buttonPropertiesLiveData = when(file.name) {
                    BUTTON1 -> buttonOn1Properties
                    BUTTON2 -> buttonOn2Properties
                    else -> buttonOn3Properties
                }
                savable = FileSavable(content[0], content[2], content[1], content[3], file.name)
                buttonPropertiesLiveData.value = ButtonProperties.getButtonChangeable(savable)
            } catch (_: Exception) {
                corruptedFiles.add(file.name)
            }
        }
        val buttonStaticFiles = when(path) {
            null -> listOf(BUTTON4, BUTTON5, BUTTON6)
            BUTTON4 -> listOf(BUTTON4)
            BUTTON5 -> listOf(BUTTON5)
            BUTTON6 -> listOf(BUTTON6)
            else -> emptyList()
        }.map { File(applicationSettingsDirectory, it) }
        for (file in buttonStaticFiles) {
            try {
                if (!file.exists()) {
                    file.createNewFile()
                    val savable = FileSavable(DEFAULT_BUTTON_TEXT, "", DEFAULT_BUTTON_ACTIVATED_TEXT, "", file.name)
                    savable.save(true)
                }
                val content = file.readText().split(BUTTON_FILE_FORMAT_DELIMITER)
                require(content.size == 2)
                val buttonPropertiesLiveData = when(file.name) {
                    BUTTON4 -> buttonOn4Properties
                    BUTTON5 -> buttonOn5Properties
                    else -> buttonOn6Properties
                }
                val text = content[0]
                val command = content[1]
                buttonPropertiesLiveData.value = ButtonProperties.getButtonStatic(text, command, file.name)
            } catch (_: Exception) {
                corruptedFiles.add(file.name)
            }
        }
        if (path == null || path == MEASUREMENT) {
            val measurementFile = File(applicationSettingsDirectory, MEASUREMENT)
            try {
                if (!measurementFile.exists()) {
                    measurementFile.createNewFile()
                    measurementFile.writeText(
                        """
                        $MEASUREMENT1_FILE_NAME$BUTTON_FILE_FORMAT_DELIMITER
                        $MEASUREMENT2_FILE_NAME$BUTTON_FILE_FORMAT_DELIMITER
                        $MEASUREMENT3_FILE_NAME
                        """.trimIndent()
                    )
                }
                val content = measurementFile.readText().split(BUTTON_FILE_FORMAT_DELIMITER)
                require(content.size == 3)
                measureFileNames = content
            } catch (_: Exception) {
                corruptedFiles.add(measurementFile.name)
            }
        }
        if (corruptedFiles.isNotEmpty()) {
            events.offer(MainEvent.ShowCorruptionDialog("Files ${corruptedFiles.joinToString(separator = ",")} are corrupted. Please, fix them..."))
        }
    }

    private fun startPing() {
        val ping = accessorySettings.value!!.ping
        pingJob = viewModelScope.launch(writeDispatcher) {
            while (isActive) {
                events.send(MainEvent.WriteToUsb(Command(ping.command)))
                delay(ping.delay.toLong())
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

    private fun startSendingTemperatureOrCo2Requests() {
        val accessorySettings = accessorySettings.value!!
        sendTemperatureOrCo2Job?.cancel()
        sendTemperatureOrCo2Job = viewModelScope.launch(writeDispatcher) {
            while (isActive) {
                shouldSendTemperatureRequest = !shouldSendTemperatureRequest
                if (shouldSendTemperatureRequest) {
                    events.send(MainEvent.WriteToUsb(Command(accessorySettings.temperature)))
                } else {
                    events.send(MainEvent.WriteToUsb(Command(accessorySettings.co2)))
                }
                delay(accessorySettings.syncPeriod / 2L)
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
    //wait for any response if there is no possible responses
    private fun waitForCooling() {

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
        edit.apply()
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

        val newMeasureFiles = listOf(editText1Text, editText2Text, editText3Text)

        if (graphData != null) events.offer(MainEvent.UpdateGraphData(graphData))
        if (checkedRadioButtonIndex == -1) {
            readCommandsFromText(
                text = editorText,
                shouldUseRecentDirectory = isUseRecentDirectory,
                runningTime = future,
                oneLoopTime = delay_timer,
                newMeasureFiles = newMeasureFiles
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
            file = File(File(Environment.getExternalStorageDirectory(), APPLICATION_SETTINGS), filePath),
            shouldUseRecentDirectory = isUseRecentDirectory,
            runningTime = future,
            oneLoopTime = delay_timer,
            newMeasureFiles = newMeasureFiles
        )
        return true
    }

    private fun readCommandsFromFile(
        file: File,
        shouldUseRecentDirectory: Boolean,
        runningTime: Long,
        oneLoopTime: Long,
        newMeasureFiles: List<String>
    ) {
        readCommandsFromText(file.readText(), shouldUseRecentDirectory, runningTime, oneLoopTime, newMeasureFiles)
    }

    private fun readCommandsFromText(
        text: String?,
        shouldUseRecentDirectory: Boolean,
        runningTime: Long,
        oneLoopTime: Long,
        newMeasureFiles: List<String>
    ) {
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
                val measurementFile = File(applicationSettingsDirectory, MEASUREMENT)
                measurementFile.writeText(newMeasureFiles.joinToString(separator = BUTTON_FILE_FORMAT_DELIMITER))
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
        onChangeableClick(0)
    }

    private fun onChangeableClick(index: Int) {
        val currentButtonProperties = allButtonProperties[index]
        changeableButtonClickedProperties = currentButtonProperties
        for (buttonProperties in allButtonProperties.withIndex()) {
            val oldProperties = buttonProperties.value.value!!
            buttonProperties.value.value = when (buttonProperties.index) {
                index -> {
                    oldProperties.copy(isEnabled = false, alpha = 0.6f)
                }
                else -> oldProperties.copy(isEnabled = false)
            }
        }
        stopSendingTemperatureOrCo2Requests()
        val command = if (currentButtonProperties.value!!.isActivated) currentButtonProperties.value!!.savable.activatedCommand else currentButtonProperties.value!!.savable.command
        events.offer(MainEvent.WriteToUsb(Command(command)))
    }

    // true if success, false otherwise
    fun changeButton1PersistedInfo(fileSavable: FileSavable): Boolean = fileSavable.isValid().apply {
        if (this) fileSavable.withName(buttonOn1Properties.value!!.savable.fileName).save(false)
    }

    fun onButton2Click() {
        onChangeableClick(1)
    }

    fun changeButton2PersistedInfo(fileSavable: FileSavable): Boolean = fileSavable.isValid().apply {
        if (this) fileSavable.withName(buttonOn2Properties.value!!.savable.fileName).save(false)
    }

    fun onButton3Click() {
        onChangeableClick(2)
    }

    fun changeButton3PersistedInfo(fileSavable: FileSavable): Boolean = fileSavable.isValid().apply {
        if (this) fileSavable.withName(buttonOn3Properties.value!!.savable.fileName).save(false)
    }

    fun onButton4Click() {
        onSendAndForgetClick(3)
    }

    private fun onSendAndForgetClick(index: Int) {
        val currentButtonProperties = allButtonProperties[index]
        sendAndForgetButtonClickedProperties = currentButtonProperties
        for (buttonProperties in allButtonProperties.withIndex()) {
            val oldProperties = buttonProperties.value.value!!
            buttonProperties.value.value = when (buttonProperties.index) {
                index -> {
                    oldProperties.copy(isEnabled = false, alpha = 0.6f)
                }
                else -> oldProperties.copy(isEnabled = false)
            }
        }
        stopSendingTemperatureOrCo2Requests()
        events.offer(MainEvent.WriteToUsb(Command(currentButtonProperties.value!!.savable.command)))
    }

    fun changeButton4PersistedInfo(fileSavable: FileSavable): Boolean = fileSavable.isValid().apply {
        if (this) fileSavable.withName(buttonOn4Properties.value!!.savable.fileName).save(true)
    }

    fun onButton5Click() {
        onSendAndForgetClick(4)
    }

    fun changeButton5PersistedInfo(fileSavable: FileSavable): Boolean = fileSavable.isValid().apply {
        if (this) fileSavable.withName(buttonOn5Properties.value!!.savable.fileName).save(true)
    }

    fun onButton6Click() {
        onSendAndForgetClick(5)
    }

    fun changeButton6PersistedInfo(fileSavable: FileSavable): Boolean = fileSavable.isValid().apply {
        if (this) fileSavable.withName(buttonOn6Properties.value!!.savable.fileName).save(true)
    }

    fun onSendClick() {
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

    //interrupt actions = cancel all running jobs
    fun onPowerClick() = when(powerProperties.value!!.isActivated) {
        true -> onPowerOffClick()
        else -> onPowerOnClick()
    }

    private fun onPowerOnClick() {
        TODO("implement power on")
    }

    private fun onPowerOffClick(accessorySettings: AccessorySettings? = null) {
        pendingAccessorySettings = accessorySettings
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

    private suspend fun handleResponse(response: String) {
        val borderCoolingTemperature = accessorySettings.value!!.borderTemperature
        TODO("handle response from usb")
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
        when {
            pingJob?.isActive == true -> {
                pingJob?.cancel()
                pingJob = null
                powerProperties.value = powerProperties.value!!.copy(isEnabled = true, background = R.drawable.power_off_drawable)
            }
            changeableButtonClickedProperties?.value != null -> {
                val previousClickedProperties = changeableButtonClickedProperties!!.value!!

                for (buttonProperties in allButtonProperties) {
                    buttonProperties.value = buttonProperties.value!!.copy(isEnabled = true)
                }
                //TODO if response is correct
                if (true) {
                    changeableButtonClickedProperties!!.value = previousClickedProperties.copy(
                        alpha = 1f,
                        isActivated = !previousClickedProperties.isActivated,
                        background = when (previousClickedProperties.isActivated) {
                            true -> R.drawable.button_drawable
                            false -> R.drawable.power_on_drawable
                        },
                        isEnabled = true
                    )
                } else {
                    changeableButtonClickedProperties!!.value = previousClickedProperties.copy(
                        alpha = 1f,
                        isEnabled = true
                    )
                    //TODO show message about wrong response
                }
                changeableButtonClickedProperties = null
                startSendingTemperatureOrCo2Requests()
            }
            sendAndForgetButtonClickedProperties?.value != null -> {
                for (buttonProperties in allButtonProperties) {
                    buttonProperties.value = buttonProperties.value!!.copy(isEnabled = true, alpha = 1f)
                }
                sendAndForgetButtonClickedProperties = null
                startSendingTemperatureOrCo2Requests()
            }
            !powerProperties.value!!.isActivated && isPowerWaitForLastResponse -> {
                isPowerWaitForLastResponse = false
                isPowerInProgress = false
                //TODO check last response, if it's ok, then
                if (true) {
                    for (buttonProperties in allButtonProperties) {
                        buttonProperties.value = buttonProperties.value!!.copy(isEnabled = true)
                    }
                    powerProperties.value = powerProperties.value!!.copy(
                        alpha = 1f,
                        isActivated = true,
                        background = R.drawable.power_on_drawable,
                        isEnabled = true
                    )
                    startSendingTemperatureOrCo2Requests()
                } else {
                    powerProperties.value = powerProperties.value!!.copy(alpha = 1f, isEnabled = true)
                }
            }
            isPowerWaitForLastResponse -> {
                isPowerWaitForLastResponse = false
                isPowerInProgress = false
                //TODO check last response, if it's ok, then
                if (true) {
                    for (buttonProperties in allButtonProperties) {
                        buttonProperties.value = buttonProperties.value!!.copy(isEnabled = false)
                    }
                    powerProperties.value = powerProperties.value!!.copy(
                        alpha = 1f,
                        isActivated = false,
                        background = R.drawable.power_off_drawable,
                        isEnabled = true
                    )
                    if (pendingAccessorySettings != null) {
                        onAccessorySettingsChanged.invoke(pendingAccessorySettings!!)
                        onAccessorySettingsChanged = {}
                        pendingAccessorySettings = null
                    }
                } else {
                    for (buttonProperties in allButtonProperties) {
                        buttonProperties.value = buttonProperties.value!!.copy(isEnabled = true, alpha = 1f)
                    }
                    startSendingTemperatureOrCo2Requests()
                }
            }
            bytes.size == 7 && isMeasuring -> {
                cacheBytesFromUsbWhenMeasurePressed(bytes)
                viewModelScope.launch(readDispatcher) {
                    handleResponse(response)
                }
            }
            else -> {
                viewModelScope.launch(readDispatcher) {
                    handleResponse(response)
                }
            }
        }
    }

    override fun onCleared() {
        fileObserver?.stopWatching()
        super.onCleared()
    }
}