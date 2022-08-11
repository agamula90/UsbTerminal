package com.ismet.usbterminal

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Build
import android.os.FileObserver
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ismet.usbterminal.data.*
import com.ismet.usbterminal.di.UsbWriteDispatcher
import com.ismet.usbterminal.di.CacheCo2ValuesDispatcher
import com.ismet.usbterminalnew.BuildConfig
import com.ismet.usbterminalnew.R
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.NoSuchElementException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.max

const val CHART_INDEX_UNSELECTED = -1
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
private const val DEFAULT_BUTTON_TEXT = "Command1"
private const val DEFAULT_BUTTON_ACTIVATED_TEXT = "Command2"
private const val DEFAULT_MAX_X = 9f
private const val DEFAULT_MAX_Y = 10f

private val FORMATTER = SimpleDateFormat("${DATE_FORMAT}${DELIMITER}${TIME_FORMAT}")
val DATE_TIME_FORMATTER = SimpleDateFormat("MM.dd.yyyy HH:mm:ss")
private val CO2_TIME_FORMAT = SimpleDateFormat("mm:ss", Locale.ENGLISH)

@HiltViewModel
class MainViewModel @Inject constructor(
    @UsbWriteDispatcher private val writeDispatcher: CoroutineContext,
    @CacheCo2ValuesDispatcher private val cacheDispatcher: CoroutineContext,
    private val exceptionHandler: CoroutineExceptionHandler,
    private val prefs: SharedPreferences,
    private val moshi: Moshi,
    handle: SavedStateHandle,
    @ApplicationContext val context: Context
): ViewModel() {
    private var shouldSendTemperatureRequest = true
    private var readChartJob: Job? = null
    private var sendTemperatureOrCo2Job: Job? = null
    private var readCommandsJob: Job? = null
    private var chartDate: String = ""
    private var subDirDate: String = ""
    private var currentClearOptions = mutableSetOf<String>()
    private var changeableButtonClickedProperties: MutableLiveData<ButtonProperties?>? = null
    private var sendAndForgetButtonClickedProperties: MutableLiveData<ButtonProperties?>? = null
    var measureFileNames: List<String> = emptyList()
    private set

    val events = Channel<MainEvent>(Channel.UNLIMITED)
    val charts = MutableLiveData(
        Charts(
            charts = List(3) { Chart(it, emptyList()) },
            maxX = DEFAULT_MAX_X,
            maxY = DEFAULT_MAX_Y
        )
    )
    private val applicationSettingsDirectory = DirectoryType.APPLICATION_SETTINGS.getDirectory()
    val accessorySettings = handle.getLiveData<AccessorySettings?>("accessorySettings")
    val buttonOn1Properties = handle.getLiveData<ButtonProperties?>("buttonOn1")
    val buttonOn2Properties = handle.getLiveData<ButtonProperties?>("buttonOn2")
    val buttonOn3Properties = handle.getLiveData<ButtonProperties?>("buttonOn3")
    val buttonOn4Properties = handle.getLiveData<ButtonProperties?>("buttonOn4")
    val buttonOn5Properties = handle.getLiveData<ButtonProperties?>("buttonOn5")
    val buttonOn6Properties = handle.getLiveData<ButtonProperties?>("buttonOn6")
    val powerProperties = handle.getLiveData("power", ButtonProperties.forPower())
    val measureProperties = handle.getLiveData("measure", ButtonProperties.forMeasure())
    val sendProperties = handle.getLiveData("send", ButtonProperties.forSend())
    private val allButtonProperties = listOf(
        buttonOn1Properties, buttonOn2Properties, buttonOn3Properties, buttonOn4Properties, buttonOn5Properties,
        buttonOn6Properties, powerProperties, measureProperties, sendProperties
    )
    private var currentChartIndex = 0
    val allClearOptions = listOf("New Measure", "Tx", "LM", "Chart 1", "Chart 2", "Chart 3")
    val checkedClearOptions = List(allClearOptions.size) { false }
    var isCo2Measuring = false
    var fileObserver: FileObserver? = null
    private var onAccessorySettingsChanged: (AccessorySettings) -> Unit = {}
    private var pingJob: Job? = null
    private var isPowerWaitForLastResponse = false
    private var pendingAccessorySettings: AccessorySettings? = null
    private var currentTemperature = 0

    init {
        Thread.setDefaultUncaughtExceptionHandler { t, e -> exceptionHandler.handleException(EmptyCoroutineContext, e) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            observeAppSettingsDirectoryUpdates()
        }
        readCombinedXyChart()
    }

    fun observeAppSettingsDirectoryUpdates() {
        fileObserver = object: FileObserver(applicationSettingsDirectory, getFileObservationMask()) {
            override fun onEvent(event: Int, path: String?) {
                checkAppSettings(path)
            }
        }.also { it.startWatching() }
        checkAppSettings()
    }

    private fun getFileObservationMask() = FileObserver.CREATE or FileObserver.MODIFY or FileObserver.MOVED_TO or FileObserver.DELETE

    private fun checkAppSettings(path: String? = null) {
        val corruptedFiles = mutableListOf<String>()
        if (path == null || path == ACCESSORY_SETTINGS) {
            try {
                val accessoryDirectory = File(applicationSettingsDirectory, ACCESSORY_SETTINGS)
                if (!accessoryDirectory.exists()) {
                    accessoryDirectory.createNewFile()
                    accessoryDirectory.writeText(
                        moshi.adapter(AccessorySettings::class.java).toJson(AccessorySettings.getDefault())
                    )
                }

                val oldAccessorySettings = accessorySettings.value
                val newAccessorySettings = moshi.adapter(AccessorySettings::class.java)
                    .fromJson(accessoryDirectory.readTextEnhanced())!!
                if (oldAccessorySettings == null) {
                    accessorySettings.postValue(newAccessorySettings)
                    startPing(newAccessorySettings)
                } else if (accessorySettings.value != newAccessorySettings) {
                    onAccessorySettingsChanged = { newSettings ->
                        accessorySettings.postValue(newSettings)
                        startPing(newSettings)
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
                val content = file.readTextEnhanced().split(BUTTON_FILE_FORMAT_DELIMITER)
                require(content.size == 4)
                val buttonPropertiesLiveData = when(file.name) {
                    BUTTON1 -> buttonOn1Properties
                    BUTTON2 -> buttonOn2Properties
                    else -> buttonOn3Properties
                }
                savable = FileSavable(content[0], content[2], content[1], content[3], file.name)
                buttonPropertiesLiveData.postValue(ButtonProperties.getButtonChangeable(savable))
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
                val content = file.readTextEnhanced().split(BUTTON_FILE_FORMAT_DELIMITER)
                require(content.size == 2)
                val buttonPropertiesLiveData = when(file.name) {
                    BUTTON4 -> buttonOn4Properties
                    BUTTON5 -> buttonOn5Properties
                    else -> buttonOn6Properties
                }
                val text = content[0]
                val command = content[1]
                buttonPropertiesLiveData.postValue(ButtonProperties.getButtonStatic(text, command, file.name))
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
                        listOf(MEASUREMENT1_FILE_NAME, MEASUREMENT2_FILE_NAME, MEASUREMENT3_FILE_NAME).joinToString(separator = BUTTON_FILE_FORMAT_DELIMITER)
                    )
                }
                val content = measurementFile.readTextEnhanced().split(BUTTON_FILE_FORMAT_DELIMITER)
                require(content.size == 3)
                measureFileNames = content
            } catch (_: Exception) {
                corruptedFiles.add(measurementFile.name)
            }
        }
        if (corruptedFiles.isNotEmpty()) {
            events.offer(MainEvent.ShowCorruptionDialog("Files ${corruptedFiles.joinToString(separator = ", ")} are corrupted. Please, fix them..."))
        } else {
            events.offer(MainEvent.DismissCorruptionDialog)
        }
    }

    private fun startPing(accessorySettings: AccessorySettings) {
        if (pingJob != null) return
        val ping = accessorySettings.ping
        pingJob = viewModelScope.launch(writeDispatcher) {
            while (isActive) {
                events.send(MainEvent.WriteToUsb(ping.command))
                delay(ping.delay)
            }
        }
    }

    fun readChart(filePath: String) {
        val shouldStartPing = pingJob?.isActive == true
        pingJob?.cancel()
        pingJob = null
        val shouldStartTemperatureCo2Requests = sendTemperatureOrCo2Job?.isActive == true
        stopSendingTemperatureOrCo2Requests()
        readChartJob?.cancel()
        val charts = charts.value!!
        val currentCharts = charts.charts.toMutableList()
        val newChartIndex = currentCharts.indexOfFirst { it.canBeRestoredFromFilePath(filePath) }

        if (newChartIndex == CHART_INDEX_UNSELECTED) {
            // events.offer(MainEvent.ShowToast("Required Log files not available"))
            readChartJob = null
            return
        }

        readChartJob = viewModelScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            val lines = file.readLines()
            var startX = lines.size * currentCharts[newChartIndex].id
            var newMaxY = charts.maxY
            val newChartPoints = mutableListOf<PointF>()
            for (line in lines) {
                if (line.isNotEmpty()) {
                    val arr = line.split(",").toTypedArray()
                    val co2 = arr[1].toInt()
                    val potentialMaxY = co2.toFloat(newChartPoints.isEmpty())
                    if (potentialMaxY >= newMaxY) {
                        newMaxY = potentialMaxY
                    }
                    newChartPoints.add(PointF(startX.toFloat(), co2.toFloat()))
                    delay(50)
                    currentCharts[newChartIndex] = currentCharts[newChartIndex].copy(points = newChartPoints)
                    this@MainViewModel.charts.postValue(charts.copy(charts = currentCharts, maxY = newMaxY))
                    startX++
                }
            }
            events.send(MainEvent.ShowToast("File reading done"))
            if (shouldStartPing) startPing(accessorySettings.value!!)
            if (shouldStartTemperatureCo2Requests) startSendingTemperatureOrCo2Requests()
        }
    }

    fun startSendingTemperatureOrCo2Requests() {
        val accessorySettings = accessorySettings.value!!
        sendTemperatureOrCo2Job?.cancel()
        sendTemperatureOrCo2Job = viewModelScope.launch(writeDispatcher) {
            while (isActive) {
                shouldSendTemperatureRequest = !shouldSendTemperatureRequest
                if (shouldSendTemperatureRequest) {
                    events.send(MainEvent.WriteToUsb(accessorySettings.temperature))
                } else {
                    events.send(MainEvent.WriteToUsb(accessorySettings.co2))
                }
                delay(accessorySettings.syncPeriod / 2)
            }
        }
    }

    private fun stopSendingTemperatureOrCo2Requests() {
        sendTemperatureOrCo2Job?.cancel()
        sendTemperatureOrCo2Job = null
    }

    private fun waitForCooling(accessorySettings: AccessorySettings) = viewModelScope.launch(writeDispatcher) {
        events.send(MainEvent.ShowWaitForCoolingDialog(message = """
            Cooling down.  Do not switch power off.  Please wait . . . ! ! !
            System will turn off automaticaly.
            """.trimIndent())
        )
        while (currentTemperature > accessorySettings.borderTemperature) {
            events.send(MainEvent.WriteToUsb(accessorySettings.temperature))
            delay(accessorySettings.off.coolingPeriod!!)
        }
        events.send(MainEvent.DismissCoolingDialog)
        isPowerWaitForLastResponse = true
        events.send(MainEvent.WriteToUsb(accessorySettings.off.command))
    }

    // return true if operation can succeed, false otherwise
    fun measureCo2Values(
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
        stopSendingTemperatureOrCo2Requests()
        isCo2Measuring = true
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
        events.offer(MainEvent.IncCountMeasure)
        edit.putInt(PrefConstants.DELAY, intDelay)
        edit.putInt(PrefConstants.DURATION, intDuration)
        edit.apply()
        val future = intDuration * 60 * 1000
        val oneLoopTime = intDelay * 1000
        val currentChartIndex: Int
        val readingCount: Int
        val oldCharts = charts.value!!.charts
        when {
            countMeasure == 0 || oldCharts[0].points.isEmpty() -> {
                currentChartIndex = 0
                readingCount = 0
            }
            oldCharts[1].points.isEmpty() -> {
                currentChartIndex = 1
                readingCount = intDuration * 60 / intDelay
            }
            oldCharts[2].points.isEmpty() -> {
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
        if (countMeasure == 0) {
            val maxX = 3f * (intDuration * 60 / intDelay)
            charts.value = Charts(
                charts = List(3) { Chart(it, emptyList())},
                maxX = maxX,
                maxY = DEFAULT_MAX_Y
            )
        }
        if (checkedRadioButtonIndex == -1) {
            readCommandsFromText(
                text = editorText,
                shouldUseRecentDirectory = isUseRecentDirectory,
                runningTime = future,
                oneLoopTime = oneLoopTime,
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
            file = File(DirectoryType.APPLICATION_SETTINGS.getDirectory(), filePath),
            shouldUseRecentDirectory = isUseRecentDirectory,
            runningTime = future,
            oneLoopTime = oneLoopTime,
            newMeasureFiles = newMeasureFiles
        )
        return true
    }

    private fun readCommandsFromFile(
        file: File,
        shouldUseRecentDirectory: Boolean,
        runningTime: Int,
        oneLoopTime: Int,
        newMeasureFiles: List<String>
    ) {
        readCommandsFromText(file.readTextEnhanced(), shouldUseRecentDirectory, runningTime, oneLoopTime, newMeasureFiles)
    }

    private fun readCommandsFromText(
        text: String?,
        shouldUseRecentDirectory: Boolean,
        runningTime: Int,
        oneLoopTime: Int,
        newMeasureFiles: List<String>
    ) {
        if (text != null && text.isNotEmpty()) {
            val commands = text.split("\n").toTypedArray()
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
                        val line1 = lineNos.substring(0, lineNos.length / 2)
                        val line2 = lineNos.substring(lineNos.length / 2, lineNos.length)
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
            readCommandsJob = viewModelScope.launch(writeDispatcher) {
                delay(300)
                if (shouldUseRecentDirectory) {
                    val ppm = prefs.getInt(PrefConstants.KPPM, -1)
                    //cal directory
                    if (ppm != -1) {
                        val directory = DirectoryType.CALCULATIONS.getDirectory()
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

                isCo2Measuring = false
                events.send(MainEvent.ShowToast("Timer Stopped"))
                val measurementFile = File(applicationSettingsDirectory, MEASUREMENT)
                measurementFile.writeText(newMeasureFiles.joinToString(separator = BUTTON_FILE_FORMAT_DELIMITER))
                startSendingTemperatureOrCo2Requests()
            }
        } else {
            startSendingTemperatureOrCo2Requests()
            events.offer(MainEvent.ShowToast("File not found"))
        }
    }

    private suspend fun processChart(future: Int, delay: Int, simpleCommands: List<String>, loopCommands: List<String>) {
        for (i in simpleCommands.indices) {
            if (simpleCommands[i].contains("delay")) {
                val delayC = simpleCommands[i].replace("delay", "").trim { it <= ' ' }.toInt()
                delay(delayC)
            } else {
                events.send(MainEvent.WriteToUsb(simpleCommands[i]))
            }
        }

        val len = future / delay
        var count: Long = 0
        if (loopCommands.isNotEmpty()) {
            while (count < len) {
                events.send(MainEvent.IncReadingCount)
                events.send(MainEvent.WriteToUsb(loopCommands[0]))
                val halfDelay = delay / 2
                delay(halfDelay)
                if (loopCommands.size > 1) {
                    events.send(MainEvent.WriteToUsb(loopCommands[1]))
                    delay(halfDelay)
                }

                count++
            }
        }
    }

    private fun cacheCo2ValuesToFile(bytes: ByteArray) = viewModelScope.launch(cacheDispatcher) {
        try {
            val ppm: Int = prefs.getInt(PrefConstants.KPPM, -1)
            val directoryType = if (ppm == -1) DirectoryType.MEASUREMENT else DirectoryType.CALCULATIONS
            var dir = directoryType.getDirectory()
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val userComment: String = prefs.getString(PrefConstants.USER_COMMENT, "")!!
            val subDirName = directoryType.encodedName + DELIMITER + subDirDate + DELIMITER + userComment
            dir = File(dir, subDirName)
            if (!dir.exists()) {
                dir.mkdir()
            }
            val ppmPart = (if (ppm == -1) "" else DELIMITER + ppm) + DELIMITER + "R"
            val volumeValue: Int = prefs.getInt(PrefConstants.VOLUME, -1)
            val volume = DELIMITER + if (volumeValue == -1) "" else "" + volumeValue
            val fileName = directoryType.encodedName + DELIMITER + chartDate + volume + ppmPart + (currentChartIndex + 1) + ".csv"
            val file = File(dir, fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            val formattedTime = CO2_TIME_FORMAT.formatEnhanced(Date())
            val strH = String.format("%02X%02X", bytes[3], bytes[4])
            val co2 = strH.toInt(16)
            file.appendText("$formattedTime,$co2\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun DateFormat.formatEnhanced(date: Date, countDigitsInMillis: Int = 1): String {
        require(countDigitsInMillis in 0..3)
        if (countDigitsInMillis == 0) return format(date)
        val millis = date.time - (date.time / 1000) * 1000
        val countDigitsToCut = 3 - countDigitsInMillis
        return format(date) + "." + (millis / 10.pow(countDigitsToCut))
    }

    private fun Int.pow(exponent: Int) = when {
        exponent <= 1 -> this
        else -> List(exponent) { this }.reduce { power, value -> power * value }
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
        events.offer(MainEvent.WriteToUsb(command))
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
        events.offer(MainEvent.WriteToUsb(currentButtonProperties.value!!.savable.command))
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
        stopSendingTemperatureOrCo2Requests()
        events.offer(MainEvent.SendCommandsFromEditor)
    }

    fun onCurrentChartWasModified(wasModified: Boolean) {
        val currentDate = Date()
        if (wasModified) {
            chartDate = FORMATTER.format(currentDate)
            charts.value!!.charts[currentChartIndex].tempFilePath = chartDate
        }
        if (subDirDate.isEmpty()) {
            subDirDate = FORMATTER.format(currentDate)
        }
    }

    private fun resetFilePaths() {
        subDirDate = ""
        charts.value!!.charts.forEach { it.tempFilePath = null }
    }

    fun setCurrentChartIndex(index: Int) {
        currentChartIndex = index
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
        val currentCharts = charts.value!!.charts.toMutableList()
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
                clearChartDirectories(tempFilePath, "${DELIMITER}R1")
            }
        }
        if (currentClearOptions.contains("Chart 2")) {
            currentCharts[1] = currentCharts[1].copy(points = emptyList())
            val tempFilePath = currentCharts[1].tempFilePath
            if (tempFilePath != null) {
                clearChartDirectories(tempFilePath, "${DELIMITER}R2")
            }
        }
        if (currentClearOptions.contains("Chart 3")) {
            currentCharts[2] = currentCharts[2].copy(points = emptyList())
            val tempFilePath = currentCharts[2].tempFilePath
            if (tempFilePath != null) {
                clearChartDirectories(tempFilePath, "${DELIMITER}R3")
            }
        }
        if (currentClearOptions.contains("New Measure")) {
            resetFilePaths()
            events.offer(MainEvent.ClearData)
        }
        val maxY = currentCharts.maxOf { chart ->
            try {
                chart.points.maxOf { it.y.toInt() }.toFloat(isChartEmpty = false)
            } catch (_: NoSuchElementException) {
                DEFAULT_MAX_Y
            }
        }
        charts.value = charts.value!!.copy(charts = currentCharts, maxY = maxY)
    }

    private fun clearChartDirectories(chartDate: String, chartIndex: String) {
        DirectoryType.MEASUREMENT.getDirectory().deleteChartFiles(chartDate, chartIndex)
        DirectoryType.CALCULATIONS.getDirectory().deleteChartFiles(chartDate, chartIndex)
    }

    private fun File.deleteChartFiles(chartDate: String, chartIndex: String) {
        val subDirectories = listFiles { file -> file != null && file.isDirectory } ?: return
        for (subDirectory in subDirectories) {
            val chartFiles = subDirectory.listFiles { _, name ->
                name != null && name.contains(chartDate) && name.contains(chartIndex)
            } ?: continue
            chartFiles.forEach(File::delete)
        }
    }

    fun onPowerClick() = when(powerProperties.value!!.isActivated) {
        true -> onPowerOffClick()
        else -> onPowerOnClick()
    }

    private fun onPowerOnClick() {
        powerProperties.value = powerProperties.value!!.copy(isEnabled = false, alpha = 0.6f)
        isPowerWaitForLastResponse = true
        events.offer(MainEvent.WriteToUsb(accessorySettings.value!!.on.command))
    }

    private fun onPowerOffClick(newAccessorySettings: AccessorySettings? = null) {
        stopSendingTemperatureOrCo2Requests()
        readChartJob?.cancel()
        readChartJob = null
        readCommandsJob?.cancel()
        readCommandsJob = null
        pendingAccessorySettings = newAccessorySettings
        val accessorySettings = accessorySettings.value!!
        if (accessorySettings.off.coolingPeriod != null) {
            waitForCooling(accessorySettings)
        } else {
            isPowerWaitForLastResponse = true
            events.offer(MainEvent.WriteToUsb(accessorySettings.off.command))
        }
    }

    private fun readCombinedXyChart() {
        val delay = prefs.getInt(PrefConstants.DELAY, PrefConstants.DELAY_DEFAULT)
        val duration = prefs.getInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)
        if (!prefs.contains(PrefConstants.DELAY)) {
            val editor = prefs.edit()
            editor.putInt(PrefConstants.DELAY, PrefConstants.DELAY_DEFAULT)
            editor.putInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)
            editor.putInt(PrefConstants.VOLUME, PrefConstants.VOLUME_DEFAULT)
            editor.apply()
        }
        charts.value = charts.value!!.copy(maxX = 3f * (duration * 60 / delay))
    }

    fun onDataReceived(bytes: ByteArray) {
        when {
            pingJob != null -> {
                pingJob?.cancel()
                pingJob = null
                powerProperties.value = powerProperties.value!!.copy(isEnabled = true, background = R.drawable.power_off_drawable)
            }
            changeableButtonClickedProperties?.value != null -> {
                val previousClickedProperties = changeableButtonClickedProperties!!.value!!

                for (buttonProperties in allButtonProperties) {
                    buttonProperties.value = buttonProperties.value!!.copy(isEnabled = true)
                }
                changeableButtonClickedProperties!!.value = previousClickedProperties.copy(
                    alpha = 1f,
                    isActivated = !previousClickedProperties.isActivated,
                    background = when (previousClickedProperties.isActivated) {
                        true -> R.drawable.button_drawable
                        false -> R.drawable.power_on_drawable
                    },
                    isEnabled = true
                )
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
                val onResponses = accessorySettings.value!!.on.acceptedResponses
                val response = bytes.decodeToStringEnhanced()
                if (onResponses.contains(response)) {
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
                    events.offer(MainEvent.ShowToast(
                        message = "Wrong response: Got - \"$response\".Expected - ${onResponses.joinToString(separator = " or ") {"\"$it\""}}")
                    )
                    powerProperties.value = powerProperties.value!!.copy(alpha = 1f, isEnabled = true)
                }
            }
            isPowerWaitForLastResponse -> {
                isPowerWaitForLastResponse = false
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
            }
            isCo2Measuring -> {
                if (bytes.decodeToPeriodicResponse() is PeriodicResponse.Co2) {
                    cacheCo2ValuesToFile(bytes)
                }
            }
            else -> {
                val temperature = bytes.decodeToPeriodicResponse() as? PeriodicResponse.Temperature ?: return
                currentTemperature = temperature.value
            }
        }
    }

    private suspend fun delay(timeout: Int) {
        delay(timeout.toLong() + BuildConfig.WRITE_TO_USB_DELAY)
    }

    fun addPointToCurrentChart(point: PointF) {
        val oldCharts = charts.value!!
        val newCharts = oldCharts.charts.toMutableList()
        newCharts[currentChartIndex] = newCharts[currentChartIndex].copy(points = newCharts[currentChartIndex].points + point)
        charts.value = oldCharts.copy(charts = newCharts, maxY = max(oldCharts.maxY, point.y.toInt().toFloat(isChartEmpty = false)))
    }

    fun resetCharts() {
        charts.value = Charts(
            charts = List(3) { Chart(it, emptyList()) },
            maxX = DEFAULT_MAX_X,
            maxY = DEFAULT_MAX_Y
        )
    }

    private fun Int.toFloat(isChartEmpty: Boolean): Float = if (isChartEmpty) {
        (3 * toFloat())
    } else {
        (this + this * 15 / 100f)
    }

    override fun onCleared() {
        fileObserver?.stopWatching()
        super.onCleared()
    }
}