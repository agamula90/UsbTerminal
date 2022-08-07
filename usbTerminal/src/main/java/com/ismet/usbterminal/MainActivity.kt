package com.ismet.usbterminal

import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.RemoteException
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.core.view.forEachIndexed
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.ismet.usb.UsbAccessory
import com.ismet.usb.UsbEmitter
import com.ismet.usbterminal.data.*
import com.ismet.usbterminal.utils.GraphPopulatorUtils
import com.ismet.usbterminal.utils.Utils
import com.ismet.usbterminalnew.BuildConfig
import com.ismet.usbterminalnew.R
import com.ismet.usbterminalnew.databinding.LayoutDialogOnOffBinding
import com.ismet.usbterminalnew.databinding.LayoutEditorUpdatedBinding
import com.proggroup.areasquarecalculator.activities.BaseAttachableActivity
import com.proggroup.areasquarecalculator.utils.ToastUtils
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import de.keyboardsurfer.android.widget.crouton.Crouton
import de.keyboardsurfer.android.widget.crouton.Style
import fr.xgouchet.TedOpenActivity
import fr.xgouchet.TedOpenRecentActivity
import fr.xgouchet.TedSaveAsActivity
import fr.xgouchet.TedSettingsActivity
import fr.xgouchet.androidlib.data.FileUtils
import fr.xgouchet.androidlib.ui.Toaster
import fr.xgouchet.androidlib.ui.activity.ActivityDecorator
import fr.xgouchet.texteditor.common.Constants
import fr.xgouchet.texteditor.common.RecentFiles
import fr.xgouchet.texteditor.common.Settings
import fr.xgouchet.texteditor.common.TextFileUtils
import fr.xgouchet.texteditor.undo.TextChangeWatcher
import kotlinx.coroutines.delay
import org.achartengine.GraphicalView
import org.achartengine.chart.XYChart
import org.achartengine.model.XYSeries
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseAttachableActivity(), TextWatcher {

    private val usbReceiver: BroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    findDevice()
                    showCustomisedToast("USB Device Attached")
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.extras!!.getParcelable<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)!!
                    showCustomisedToast("USB Device Detached")
                    val deviceId = UsbDeviceId(vendorId = device.vendorId, productId = device.productId)
                    if (usbDevice?.deviceId == deviceId) {
                        usbDevice?.close()
                        usbDevice = null
                        showCustomisedToast("USB disconnected")
                        setUsbConnected(false)
                    }
                }
                else -> {
                    Log.e(TAG, "unhandled broadcast: ${intent.action}")
                }
            }
        }
    }

    /**
     * the path of the file currently opened
     */
    private var currentFilePath: String? = null

    /**
     * the name of the file currently opened
     */
    private var currentFileName: String? = null

    /**
     * is dirty ?
     */
    private var isDirty = false

    /**
     * is read only
     */
    private var isReadOnly = false

    /**
     * Undo watcher
     */
    private var watcher: TextChangeWatcher? = null
    private var isInUndo = false
    private var isWarnedShouldQuit = false

    /**
     * are we in a post activity result ?
     */
    private var isReadIntent = false

    private var isUsbConnected = false

    private var countMeasure = 0
    private var oldCountMeasure = 0
    var readingCount = 0
        private set

    lateinit var prefs: SharedPreferences
    lateinit var currentSeries: XYSeries
    var chartView: GraphicalView? = null
    var chart: XYChart? = null
    private lateinit var usbDeviceConnection: UsbDeviceConnection

    @Inject
    lateinit var usbEmitter: UsbEmitter

    @Inject
    lateinit var moshi: Moshi

    private var usbDevice: UsbDevice? = null
    private val viewModel: MainViewModel by viewModels()
    private var reportDate: Date? = null
    private lateinit var binding: LayoutEditorUpdatedBinding
    private var coolingDialog: Dialog? = null

    private var usbAccessory: UsbAccessory? = null
    private var isSendServiceConnected = false
    private val sendToAccessoryConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isSendServiceConnected = true
            if (service != null) {
                usbAccessory = UsbAccessory.Stub.asInterface(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isSendServiceConnected = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LayoutEditorUpdatedBinding.bind(findViewById(R.id.content_main))
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        Settings.updateFromPreferences(
            getSharedPreferences(
                Constants.PREFERENCES_NAME,
                MODE_PRIVATE
            )
        )
        val actionBar = supportActionBar
        actionBar!!.setDisplayUseLogoEnabled(true)
        actionBar.setLogo(R.drawable.ic_launcher)
        val titleView = actionBar.customView.findViewById<View>(R.id.title) as TextView
        titleView.setTextColor(Color.WHITE)
        (titleView.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.CENTER_HORIZONTAL, 0)
        observeChartUpdates()
        observeEvents()
        observeUsbEvents()
        observeButtonUpdates()
        isReadIntent = true
        binding.editor.addTextChangedListener(this)
        binding.editor.updateFromSettings()
        watcher = TextChangeWatcher()
        isWarnedShouldQuit = false

        binding.power.setOnClickListener { viewModel.onPowerClick() }
        setPowerOnButtonListeners()
        binding.buttonSend.setOnClickListener { viewModel.onSendClick() }
        binding.editor.setOnEditorActionListener { _, actionId, _ ->
            var handled = false
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                // sendMessage();
                handled = true
            }
            handled
        }
        binding.buttonClear.setOnClickListener {
            if (viewModel.isMeasuring) {
                showCustomisedToast("Timer is running. Please wait")
                return@setOnClickListener
            }
            val items = viewModel.allClearOptions.toTypedArray()
            val checkedItems = viewModel.checkedClearOptions.toBooleanArray()

            val alert = AlertDialog.Builder(this@MainActivity).apply {
                setTitle("Select items")
                setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                    val clearOption = items[which]
                    if (isChecked) {
                        viewModel.addClearOption(clearOption)
                    } else {
                        viewModel.removeClearOption(clearOption)
                    }
                }
                setPositiveButton("Select/Clear") { dialog, _ ->
                    viewModel.clearCheckedOptions()
                    dialog.cancel()
                }
                setNegativeButton("Close") { dialog, _ -> dialog.cancel() }
            }.create()

            alert.setOnCancelListener { viewModel.onClearDialogDismissed() }
            alert.show()
        }
        binding.buttonMeasure.setOnClickListener { measure ->
                if (viewModel.powerCommandsFactory.currentPowerState() != PowerState.ON) {
                    return@setOnClickListener
                }
                if (viewModel.isMeasuring) {
                    showCustomisedToast("Timer is running. Please wait")
                    return@setOnClickListener
                }
                val arrSeries = chart!!.dataset.series
                var isChart1Clear = true
                var isChart2Clear = true
                var isChart3Clear = true
                for ((i, series) in arrSeries.withIndex()) {
                    if (series.itemCount > 0) {
                        when (i) {
                            0 -> isChart1Clear = false
                            1 -> isChart2Clear = false
                            2 -> isChart3Clear = false
                            else -> {}
                        }
                    }
                }
                if (!isChart1Clear && !isChart2Clear && !isChart3Clear) {
                    showCustomisedToast("No chart available. Please clear one of the charts")
                    return@setOnClickListener
                }
                measure.showMeasureDialog(
                    init = {
                        val delay = prefs.getInt(PrefConstants.DELAY, PrefConstants.DELAY_DEFAULT)
                        val duration = prefs.getInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)
                        val volume = prefs.getInt(PrefConstants.VOLUME, PrefConstants.VOLUME_DEFAULT)
                        val kppm = prefs.getInt(PrefConstants.KPPM, -1)
                        val user_comment = prefs.getString(PrefConstants.USER_COMMENT, "")
                        it.editDelay.setText(delay.toString())
                        it.editDuration.setText(duration.toString())
                        it.editVolume.setText(volume.toString())
                        it.editUserComment.setText(user_comment)
                        it.commandsEditText1.setText(prefs.getString(PrefConstants.MEASURE_FILE_NAME1, PrefConstants.MEASURE_FILE_NAME1_DEFAULT))
                        it.commandsEditText2.setText(prefs.getString(PrefConstants.MEASURE_FILE_NAME2, PrefConstants.MEASURE_FILE_NAME2_DEFAULT))
                        it.commandsEditText3.setText(prefs.getString(PrefConstants.MEASURE_FILE_NAME3, PrefConstants.MEASURE_FILE_NAME3_DEFAULT))
                        if (kppm != -1) {
                            it.editKnownPpm.setText(kppm.toString())
                        }
                        val isAuto = prefs.getBoolean(PrefConstants.IS_AUTO, false)
                        it.chkAutoManual.isChecked = isAuto
                        it.chkKnownPpm.setOnCheckedChangeListener { _, isChecked ->
                            it.editKnownPpm.isEnabled = isChecked
                            it.llkppm.isVisible = isChecked
                        }
                    },
                    okClick = { editorBinding, dialog ->
                        val delay = editorBinding.editDelay.text.toString()
                        val duration = editorBinding.editDuration.text.toString()
                        val isKnownPpm = editorBinding.chkKnownPpm.isChecked
                        val knownPpm = editorBinding.editKnownPpm.text.toString()
                        val userComment = editorBinding.editUserComment.text.toString()
                        val volume = editorBinding.editVolume.text.toString()
                        val isAutoMeasurement = editorBinding.chkAutoManual.isChecked
                        val editText1Text = editorBinding.commandsEditText1.text.toString()
                        val editText2Text = editorBinding.commandsEditText2.text.toString()
                        val editText3Text = editorBinding.commandsEditText3.text.toString()
                        val isUseRecentDirectory = editorBinding.chkUseRecentDirectory.isChecked
                        val checkedId = editorBinding.radioGroup.checkedRadioButtonId
                        var checkedRadioButtonIndex = -1
                        editorBinding.radioGroup.forEachIndexed { index, view ->
                            if (view.id == checkedId) checkedRadioButtonIndex = index
                        }
                        if (viewModel.measure(
                                delay = delay,
                                duration = duration,
                                isKnownPpm = isKnownPpm,
                                knownPpm = knownPpm,
                                userComment = userComment,
                                volume = volume,
                                isAutoMeasurement = isAutoMeasurement,
                                isUseRecentDirectory = isUseRecentDirectory,
                                checkedRadioButtonIndex = checkedRadioButtonIndex,
                                editText1Text = editText1Text,
                                editText2Text = editText2Text,
                                editText3Text = editText3Text,
                                editorText = binding.editor.text.toString(),
                                countMeasure = countMeasure)
                        ) {
                            dialog.cancel()
                        }
                    }
                )
        }
        setFilters()
        usbDeviceConnection = UsbDeviceConnection(this) { _, device ->
            usbDevice = device
            when(val notNullDevice = usbDevice) {
                null -> {
                    showCustomisedToast("USB Permission not granted")
                    finish()
                }
                else -> notNullDevice.refreshUi()
            }
        }
        findDevice()
        establishMockedConnections()
    }

    private fun establishMockedConnections() {
        val intent = Intent("com.ismet.usb.accessory").apply { `package` = "com.ismet.usbaccessory" }
        bindService(intent, sendToAccessoryConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setPowerOnButtonListeners() {
        binding.buttonOn1.setOnClickListener { viewModel.onButton1Click() }
        binding.buttonOn1.setOnLongClickListener {
            showOnOffDialog(
                init = {
                    changeTextsForButtons(it)
                    val str_on = prefs.getString(PrefConstants.ON1, "")
                    val str_off = prefs.getString(PrefConstants.OFF1, "")
                    val str_on_name = prefs.getString(
                        PrefConstants.ON_NAME1,
                        PrefConstants.ON_NAME_DEFAULT
                    )
                    val str_off_name = prefs.getString(
                        PrefConstants.OFF_NAME1,
                        PrefConstants.OFF_NAME_DEFAULT
                    )
                    it.editOn.setText(str_on)
                    it.editOff.setText(str_off)
                    it.editOn1.setText(str_on_name)
                    it.editOff1.setText(str_off_name)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val activatedCommand = localBinding.editOff.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val activatedText = localBinding.editOff1.text.toString()
                    val isSuccess = viewModel.changeButton1PersistedInfo(
                        PersistedInfo(text, command, activatedText, activatedCommand)
                    )
                    if (!isSuccess) {
                        showCustomisedToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
        binding.buttonOn2.setOnClickListener { viewModel.onButton2Click() }
        binding.buttonOn2.setOnLongClickListener {
            showOnOffDialog(
                init = {
                    changeTextsForButtons(it)
                    val str_on_name = prefs.getString(
                        PrefConstants.ON_NAME2,
                        PrefConstants.ON_NAME_DEFAULT
                    )
                    val str_off_name = prefs.getString(
                        PrefConstants.OFF_NAME2,
                        PrefConstants.OFF_NAME_DEFAULT
                    )
                    val str_on = prefs.getString(PrefConstants.ON2, "")
                    val str_off = prefs.getString(PrefConstants.OFF2, "")
                    it.editOn.setText(str_on)
                    it.editOff.setText(str_off)
                    it.editOn1.setText(str_on_name)
                    it.editOff1.setText(str_off_name)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val activatedCommand = localBinding.editOff.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val activatedText = localBinding.editOff1.text.toString()
                    val isSuccess = viewModel.changeButton2PersistedInfo(
                        PersistedInfo(text, command, activatedText, activatedCommand)
                    )
                    if (!isSuccess) {
                        showCustomisedToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
        binding.buttonPpm.setOnClickListener { viewModel.onButton3Click() }
        binding.buttonPpm.setOnLongClickListener {
            showOnOffDialog(
                init = {
                    changeTextsForButtons(it)
                    val str_on_name = prefs.getString(
                        PrefConstants.ON_NAME3,
                        PrefConstants.ON_NAME_DEFAULT
                    )
                    val str_off_name = prefs.getString(
                        PrefConstants.OFF_NAME3,
                        PrefConstants.OFF_NAME_DEFAULT
                    )
                    val str_on = prefs.getString(PrefConstants.ON3, "")
                    val str_off = prefs.getString(PrefConstants.OFF3, "")
                    it.editOn.setText(str_on)
                    it.editOff.setText(str_off)
                    it.editOn1.setText(str_on_name)
                    it.editOff1.setText(str_off_name)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val activatedCommand = localBinding.editOff.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val activatedText = localBinding.editOff1.text.toString()
                    val isSuccess = viewModel.changeButton3PersistedInfo(
                        PersistedInfo(text, command, activatedText, activatedCommand)
                    )
                    if (!isSuccess) {
                        showCustomisedToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
        binding.buttonOn3.setOnClickListener { viewModel.onButton4Click() }
        binding.buttonOn3.setOnLongClickListener {
            showOnOffDialog(
                init = {
                    changeTextsForButtons(it)
                    val str_on = prefs.getString(PrefConstants.ON1, "")
                    val str_off = prefs.getString(PrefConstants.OFF1, "")
                    val str_on_name = prefs.getString(
                        PrefConstants.ON_NAME1,
                        PrefConstants.ON_NAME_DEFAULT
                    )
                    val str_off_name = prefs.getString(
                        PrefConstants.OFF_NAME1,
                        PrefConstants.OFF_NAME_DEFAULT
                    )
                    it.editOn.setText(str_on)
                    it.editOff.setText(str_off)
                    it.editOn1.setText(str_on_name)
                    it.editOff1.setText(str_off_name)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val activatedCommand = localBinding.editOff.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val activatedText = localBinding.editOff1.text.toString()
                    val isSuccess = viewModel.changeButton4PersistedInfo(
                        PersistedInfo(text, command, activatedText, activatedCommand)
                    )
                    if (!isSuccess) {
                        showCustomisedToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
        binding.buttonOn4.setOnClickListener { viewModel.onButton5Click() }
        binding.buttonOn4.setOnLongClickListener {
            showOnOffDialog(
                init = {
                    changeTextsForButtons(it)
                    val str_on_name = prefs.getString(
                        PrefConstants.ON_NAME2,
                        PrefConstants.ON_NAME_DEFAULT
                    )
                    val str_off_name = prefs.getString(
                        PrefConstants.OFF_NAME2,
                        PrefConstants.OFF_NAME_DEFAULT
                    )
                    val str_on = prefs.getString(PrefConstants.ON2, "")
                    val str_off = prefs.getString(PrefConstants.OFF2, "")
                    it.editOn.setText(str_on)
                    it.editOff.setText(str_off)
                    it.editOn1.setText(str_on_name)
                    it.editOff1.setText(str_off_name)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val activatedCommand = localBinding.editOff.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val activatedText = localBinding.editOff1.text.toString()
                    val isSuccess = viewModel.changeButton5PersistedInfo(
                        PersistedInfo(text, command, activatedText, activatedCommand)
                    )
                    if (!isSuccess) {
                        showCustomisedToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
        binding.buttonOn5.setOnClickListener { viewModel.onButton6Click() }
        binding.buttonOn5.setOnLongClickListener {
            showOnOffDialog(
                init = {
                    changeTextsForButtons(it)
                    val str_on_name = prefs.getString(
                        PrefConstants.ON_NAME3,
                        PrefConstants.ON_NAME_DEFAULT
                    )
                    val str_off_name = prefs.getString(
                        PrefConstants.OFF_NAME3,
                        PrefConstants.OFF_NAME_DEFAULT
                    )
                    val str_on = prefs.getString(PrefConstants.ON3, "")
                    val str_off = prefs.getString(PrefConstants.OFF3, "")
                    it.editOn.setText(str_on)
                    it.editOff.setText(str_off)
                    it.editOn1.setText(str_on_name)
                    it.editOff1.setText(str_off_name)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val activatedCommand = localBinding.editOff.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val activatedText = localBinding.editOff1.text.toString()
                    val isSuccess = viewModel.changeButton6PersistedInfo(
                        PersistedInfo(text, command, activatedText, activatedCommand)
                    )
                    if (!isSuccess) {
                        showCustomisedToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
    }

    private fun showCustomisedToast(message: String) {
        val customToast = Toast.makeText(this, message, Toast.LENGTH_LONG)
        ToastUtils.wrap(customToast)
        customToast.show()
    }

    private fun changeTextsForButtons(binding: LayoutDialogOnOffBinding) {
        val addTextBuilder = StringBuilder()
        for (i in 0..8) {
            addTextBuilder.append(' ')
        }
        binding.txtOn.text = "${addTextBuilder}Command 1: "
        binding.txtOn1.text = "Button State1 Name: "
        binding.txtOff.text = "${addTextBuilder}Command 2: "
        binding.txtOff1.text = "Button State2 Name: "
    }

    private fun findDevice() {
        try {
            usbDeviceConnection.findDevice()
        } catch (_: FindDeviceException) {
            showCustomisedToast("No USB connected")
            setUsbConnected(false)
        }
    }

    private fun UsbDevice.refreshUi() {
        showCustomisedToast("USB Ready")
        setUsbConnected(true)
        if (!isConnectionEstablished()) {
            showCustomisedToast("USB device not supported")
            setUsbConnected(false)
            close()
            usbDevice = null
        } else {
            readCallback = OnDataReceivedCallback { onDataReceived(it) }
        }
    }

    private fun observeChartUpdates() {
        viewModel.maxY.observe(this) {
            //TODO remove max, move all maxY changes to vm
            val tempChart = chart
            if (tempChart != null) {
                tempChart.renderer.yAxisMax = maxOf(it.toDouble(), tempChart.renderer.yAxisMax)
            }
            chartView?.repaint()
        }
        viewModel.charts.observe(this) { charts ->
            val tempChart = chart ?: return@observe
            val tempChartView = chartView ?: return@observe
            for (modelChart in charts) {
                tempChart.dataset.getSeriesAt(modelChart.id).set(modelChart.points)
            }
            if (charts.all { it.points.isEmpty() }) {
                GraphPopulatorUtils.clearYTextLabels(tempChart.renderer)
            }
            tempChartView.repaint()
        }
        viewModel.currentChartIndex.observe(this) { index ->
            chart?.dataset?.getSeriesAt(index)?.let { currentSeries = it }
        }
    }

    private fun initCharts() {
        val charts = viewModel.charts.value!!
        val tempChart = chart!!
        val tempChartView = chartView!!
        for (modelChart in charts) {
            tempChart.dataset.getSeriesAt(modelChart.id).set(modelChart.points)
        }
        if (charts.all { it.points.isEmpty() }) {
            GraphPopulatorUtils.clearYTextLabels(tempChart.renderer)
        }
        tempChart.renderer.yAxisMax = maxOf(0.0, tempChart.renderer.yAxisMax)
        currentSeries = tempChart.dataset.getSeriesAt(0)
        tempChartView.repaint()
    }

    private fun observeUsbEvents() {
        lifecycleScope.launchWhenResumed {
            for (event in usbEmitter.readEvents) {
                if (event == null) {
                    Log.e("Oops", "null received")
                } else {
                    onDataReceived(event)
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launchWhenResumed {

            for (event in viewModel.events) {
                when(event) {
                    is MainEvent.ShowToast -> showCustomisedToast(event.message)
                    is MainEvent.WriteToUsb -> sendCommand(event.data)
                    is MainEvent.InvokeAutoCalculations -> invokeAutoCalculations()
                    is MainEvent.IncReadingCount -> incReadingCount()
                    is MainEvent.SendCommandsFromEditor -> sendCommandsFromEditor()
                    is MainEvent.ClearEditor -> binding.editor.setText("")
                    is MainEvent.ClearOutput -> binding.output.text = ""
                    is MainEvent.ClearData -> clearData()
                    is MainEvent.DismissCoolingDialog -> coolingDialog?.dismiss()
                    is MainEvent.IncCountMeasure -> incCountMeasure()
                    is MainEvent.SetReadingCount -> readingCount = event.value
                    is MainEvent.UpdateGraphData -> {
                        val graphData = event.graphData
                        chart = graphData.createChart()
                        chartView = GraphPopulatorUtils.attachXYChartIntoLayout(this@MainActivity, chart)
                        initCharts()
                    }
                    is MainEvent.ShowWaitForCoolingDialog -> {
                        dismissProgress()
                        coolingDialog = Dialog(this@MainActivity).apply {
                            requestWindowFeature(Window.FEATURE_NO_TITLE)
                            setContentView(R.layout.layout_cooling)
                            window!!.setBackgroundDrawableResource(android.R.color.transparent)
                            (findViewById<View>(R.id.text) as TextView).text = event.message
                            setCancelable(false)
                            show()
                        }
                    }
                }
            }
        }
    }

    private fun observeButtonUpdates() {
        viewModel.buttonOn1Properties.observe(this) {
            binding.buttonOn1.apply {
                text = if (it.isActivated) it.activatedText else it.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.buttonOn2Properties.observe(this) {
            binding.buttonOn2.apply {
                text = if (it.isActivated) it.activatedText else it.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.buttonOn3Properties.observe(this) {
            binding.buttonPpm.apply {
                text = if (it.isActivated) it.activatedText else it.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.buttonOn4Properties.observe(this) {
            binding.buttonOn3.apply {
                text = if (it.isActivated) it.activatedText else it.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.buttonOn5Properties.observe(this) {
            binding.buttonOn4.apply {
                text = if (it.isActivated) it.activatedText else it.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.buttonOn6Properties.observe(this) {
            binding.buttonOn5.apply {
                text = if (it.isActivated) it.activatedText else it.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.powerProperties.observe(this) {
            binding.power.apply {
                text = if (it.isActivated) it.activatedText else it.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
    }

    @Throws(IOException::class)
    private fun savePreferencesToLocalData() {
        val settingsFolder =
            File(Environment.getExternalStorageDirectory(), AppData.SYSTEM_SETTINGS_FOLDER_NAME)
        if (!settingsFolder.exists()) {
            settingsFolder.mkdir()
        }
        val preferences = prefs
        val button1DataFile = File(settingsFolder, AppData.BUTTON1_DATA)
        button1DataFile.createNewFile()
        val button1DataBuilder = StringBuilder()
        button1DataBuilder.append(
            preferences.getString(
                PrefConstants.ON_NAME1,
                PrefConstants.ON_NAME_DEFAULT
            )
        )
        button1DataBuilder.append(AppData.SPLIT_STRING)
        button1DataBuilder.append(
            preferences.getString(
                PrefConstants.OFF_NAME1,
                PrefConstants.OFF_NAME_DEFAULT
            )
        )
        button1DataBuilder.append(AppData.SPLIT_STRING)
        button1DataBuilder.append(preferences.getString(PrefConstants.ON1, ""))
        button1DataBuilder.append(AppData.SPLIT_STRING)
        button1DataBuilder.append(preferences.getString(PrefConstants.OFF1, ""))
        TextFileUtils.writeTextFile(button1DataFile.absolutePath, button1DataBuilder.toString())
        val button2DataFile = File(settingsFolder, AppData.BUTTON2_DATA)
        button2DataFile.createNewFile()
        val button2DataBuilder = StringBuilder()
        button2DataBuilder.append(
            preferences.getString(
                PrefConstants.ON_NAME2,
                PrefConstants.ON_NAME_DEFAULT
            )
        )
        button2DataBuilder.append(AppData.SPLIT_STRING)
        button2DataBuilder.append(
            preferences.getString(
                PrefConstants.OFF_NAME2,
                PrefConstants.OFF_NAME_DEFAULT
            )
        )
        button2DataBuilder.append(AppData.SPLIT_STRING)
        button2DataBuilder.append(preferences.getString(PrefConstants.ON2, ""))
        button2DataBuilder.append(AppData.SPLIT_STRING)
        button2DataBuilder.append(preferences.getString(PrefConstants.OFF2, ""))
        TextFileUtils.writeTextFile(button2DataFile.absolutePath, button2DataBuilder.toString())
        val button3DataFile = File(settingsFolder, AppData.BUTTON3_DATA)
        button2DataFile.createNewFile()
        val button3DataBuilder = StringBuilder()
        button3DataBuilder.append(
            preferences.getString(
                PrefConstants.ON_NAME3,
                PrefConstants.ON_NAME_DEFAULT
            )
        )
        button3DataBuilder.append(AppData.SPLIT_STRING)
        button3DataBuilder.append(
            preferences.getString(
                PrefConstants.OFF_NAME3,
                PrefConstants.OFF_NAME_DEFAULT
            )
        )
        button3DataBuilder.append(AppData.SPLIT_STRING)
        button3DataBuilder.append(preferences.getString(PrefConstants.ON3, ""))
        button3DataBuilder.append(AppData.SPLIT_STRING)
        button3DataBuilder.append(preferences.getString(PrefConstants.OFF3, ""))
        TextFileUtils.writeTextFile(button3DataFile.absolutePath, button3DataBuilder.toString())

        val measureDefaultFilesFile = File(settingsFolder, AppData.MEASURE_DEFAULT_FILES)
        measureDefaultFilesFile.createNewFile()
        val measureDefaultFilesBuilder = StringBuilder()
        measureDefaultFilesBuilder.append(
            preferences.getString(
                PrefConstants.MEASURE_FILE_NAME1,
                PrefConstants.MEASURE_FILE_NAME1_DEFAULT
            )
        )
        measureDefaultFilesBuilder.append(AppData.SPLIT_STRING)
        measureDefaultFilesBuilder.append(
            preferences.getString(
                PrefConstants.MEASURE_FILE_NAME2,
                PrefConstants.MEASURE_FILE_NAME2_DEFAULT
            )
        )
        measureDefaultFilesBuilder.append(AppData.SPLIT_STRING)
        measureDefaultFilesBuilder.append(
            preferences.getString(
                PrefConstants.MEASURE_FILE_NAME3,
                PrefConstants.MEASURE_FILE_NAME3_DEFAULT
            )
        )
        TextFileUtils.writeTextFile(
            measureDefaultFilesFile.absolutePath,
            measureDefaultFilesBuilder.toString()
        )
    }

    override fun getFragmentContainerId(): Int {
        return R.id.fragment_container
    }

    override fun getDrawerLayout(): DrawerLayout? {
        return null
    }

    override fun getToolbarId(): Int {
        return R.id.toolbar
    }

    override fun getLeftDrawerFragmentId(): Int {
        return LEFT_DRAWER_FRAGMENT_ID_UNDEFINED
    }

    override fun getFrameLayout(): FrameLayout {
        return findViewById<View>(R.id.frame_container) as FrameLayout
    }

    override fun getLayoutId(): Int {
        return R.layout.layout_editor_updated
    }

    override fun getFirstFragment(): Fragment {
        return EmptyFragment()
    }

    override fun getFolderDrawable(): Int {
        return R.drawable.folder
    }

    override fun graphContainer(): LinearLayout {
        return findViewById<View>(R.id.exported_chart_layout) as LinearLayout
    }

    override fun getFileDrawable(): Int {
        return R.drawable.file
    }

    override fun getButtonBackground(): Int {
        return R.drawable.button_drawable
    }

    override fun onGraphAttached() {
        binding.marginLayout.setBackgroundColor(Color.BLACK)
        binding.exportedChartLayout.setBackgroundColor(Color.WHITE)
    }

    override fun onGraphDetached() {
        binding.marginLayout.setBackgroundColor(Color.TRANSPARENT)
        binding.exportedChartLayout.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun toolbarTitle(): String {
        return getString(R.string.app_name_with_version, BuildConfig.VERSION_NAME)
    }

    private var lastCommand: Command? = null
    private var lastSendTime: Long = 0

    private fun sendCommand(command: Command) {
        if (usbDevice != null) {
            usbDevice?.write(command.byteArray)
        } else {
            val newLastSendTime = System.currentTimeMillis()
            if (newLastSendTime - lastSendTime > 1000) {
                try {
                    usbAccessory?.setToUsb(command.byteArray)
                } catch (_: RemoteException) {
                    //ignore
                }
                lastSendTime = newLastSendTime
            }
        }
        //if(Utils.isPullStateNone()) {
        binding.output.append(command)
        binding.scrollView.smoothScrollTo(0, 0)
        //}
    }

    private fun sendCommandsFromEditor() {
        val lines = binding.editor.text.toString().split("\n").map { Command(it) }
        if (lines.size == 1 && lines[0].text.isEmpty()) {
            sendCommand(Command("\r"))
        } else {
            lines.forEach(this::sendCommand)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbDevice?.close()
        usbDevice = null
        usbDeviceConnection.close()
        if (isSendServiceConnected) {
            unbindService(sendToAccessoryConnection)
        }
        Log.e("Oops", "host killed")
    }

    override fun onRestart() {
        super.onRestart()
        isReadIntent = false
    }

    override fun onResume() {
        super.onResume()
        if (isReadIntent) {
            readIntent()
        }
        isReadIntent = false
        binding.editor.updateFromSettings()
    }

    override fun onPause() {
        super.onPause()
        if (Settings.FORCE_AUTO_SAVE && isDirty && !isReadOnly) {
            if (currentFilePath == null || currentFilePath!!.isEmpty()) {
                doAutoSaveFile()
            } else if (Settings.AUTO_SAVE_OVERWRITE) {
                doSaveFile(currentFilePath)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        isReadIntent = false
        if (resultCode == RESULT_CANCELED) {
            return
        }
        if (resultCode != RESULT_OK || data == null) {
            return
        }
        val extras: Bundle = data.extras ?: return
        when (requestCode) {
            Constants.REQUEST_SAVE_AS -> {
                doSaveFile(extras.getString("path"))
            }
            Constants.REQUEST_OPEN -> {
                if (extras.getString("path")!!.endsWith(".txt")) {
                    doOpenFile(File(extras.getString("path")!!), false)
                } else if (extras.getString("path")!!.endsWith(".csv")) {
                    val filePath = extras.getString("path")!!
                    viewModel.readChart(filePath)
                } else {
                    showCustomisedToast("Invalid File")
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.clear()
        menu.close()

        // boolean isUsbConnected = checkUsbConnection();
        if (isUsbConnected) {
            wrapMenuItem(
                ActivityDecorator.addMenuItem(
                    menu,
                    Constants.MENU_ID_CONNECT_DISCONNECT,
                    R.string.menu_disconnect,
                    R.drawable.usb_connected
                ), true
            )
        } else {
            wrapMenuItem(
                ActivityDecorator.addMenuItem(
                    menu,
                    Constants.MENU_ID_CONNECT_DISCONNECT,
                    R.string.menu_connect,
                    R.drawable.usb_disconnected
                ), true
            )
        }
        wrapMenuItem(
            ActivityDecorator.addMenuItem(
                menu,
                Constants.MENU_ID_NEW,
                R.string.menu_new,
                R.drawable.ic_menu_file_new
            ), false
        )
        wrapMenuItem(
            ActivityDecorator.addMenuItem(
                menu,
                Constants.MENU_ID_OPEN,
                R.string.menu_open,
                R.drawable.ic_menu_file_open
            ), false
        )
        wrapMenuItem(
            ActivityDecorator.addMenuItem(
                menu,
                Constants.MENU_ID_OPEN_CHART,
                R.string.menu_open_chart,
                R.drawable.ic_menu_file_open
            ), false
        )
        if (!isReadOnly) wrapMenuItem(
            ActivityDecorator.addMenuItem(
                menu,
                Constants.MENU_ID_SAVE,
                R.string.menu_save,
                R.drawable.ic_menu_save
            ), false
        )

        // if ((!mReadOnly) && Settings.UNDO)
        // addMenuItem(menu, MENU_ID_UNDO, R.string.menu_undo,
        // R.drawable.ic_menu_undo);

        // addMenuItem(menu, MENU_ID_SEARCH, R.string.menu_search,
        // R.drawable.ic_menu_search);
        if (RecentFiles.getRecentFiles().size > 0) wrapMenuItem(
            ActivityDecorator.addMenuItem(
                menu,
                Constants.MENU_ID_OPEN_RECENT,
                R.string.menu_open_recent,
                R.drawable.ic_menu_recent
            ), false
        )
        wrapMenuItem(
            ActivityDecorator.addMenuItem(
                menu,
                Constants.MENU_ID_SAVE_AS,
                R.string.menu_save_as,
                0
            ), false
        )
        wrapMenuItem(
            ActivityDecorator.addMenuItem(
                menu,
                Constants.MENU_ID_SETTINGS,
                R.string.menu_settings,
                0
            ), false
        )
        if (Settings.BACK_BTN_AS_UNDO && Settings.UNDO) wrapMenuItem(
            ActivityDecorator.addMenuItem(
                menu,
                Constants.MENU_ID_QUIT,
                R.string.menu_quit,
                0
            ), false
        )

        // if ((!mReadOnly) && Settings.UNDO) {
        // showMenuItemAsAction(menu.findItem(MENU_ID_UNDO),
        // R.drawable.ic_menu_undo, MenuItem.SHOW_AS_ACTION_IF_ROOM);
        // }

        // showMenuItemAsAction(menu.findItem(MENU_ID_SEARCH),
        // R.drawable.ic_menu_search);
        if (isUsbConnected) {
            ActivityDecorator.showMenuItemAsAction(
                menu.findItem(Constants.MENU_ID_CONNECT_DISCONNECT),
                R.drawable.usb_connected,
                MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT
            )
        } else {
            ActivityDecorator.showMenuItemAsAction(
                menu.findItem(Constants.MENU_ID_CONNECT_DISCONNECT),
                R.drawable.usb_disconnected,
                MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT
            )
        }
        return true
    }

    private fun wrapMenuItem(menuItem: MenuItem, isShow: Boolean) {
        if (isShow) {
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        } else {
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        isWarnedShouldQuit = false
        when (item.itemId) {
            Constants.MENU_ID_CONNECT_DISCONNECT -> {

            }
            Constants.MENU_ID_NEW -> {
                doClearContents()
                return true
            }
            Constants.MENU_ID_SAVE -> saveContent()
            Constants.MENU_ID_SAVE_AS -> saveContentAs()
            Constants.MENU_ID_OPEN -> openFile()
            Constants.MENU_ID_OPEN_CHART -> openFile()
            Constants.MENU_ID_OPEN_RECENT -> openRecentFile()
            Constants.MENU_ID_SETTINGS -> {
                startActivity(Intent(this, TedSettingsActivity::class.java))
                return true
            }
            Constants.MENU_ID_QUIT -> {
                finish()
                return true
            }
            Constants.MENU_ID_UNDO -> {
                if (!undo()) {
                    Crouton.showText(this, R.string.toast_warn_no_undo, Style.INFO)
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        if (Settings.UNDO && !isInUndo && watcher != null) watcher!!.beforeChange(
            s,
            start,
            count,
            after
        )
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        if (isInUndo) return
        if (Settings.UNDO && !isInUndo && watcher != null) watcher!!.afterChange(
            s,
            start,
            before,
            count
        )
    }

    override fun afterTextChanged(s: Editable) {
        if (!isDirty) {
            isDirty = true
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (Settings.UNDO && Settings.BACK_BTN_AS_UNDO) {
                    if (!undo()) warnOrQuit()
                } else if (shouldQuit()) {
                    finish()
                } else {
                    isWarnedShouldQuit = false
                    return super.onKeyUp(keyCode, event)
                }
                return true
            }
        }
        isWarnedShouldQuit = false
        return super.onKeyUp(keyCode, event)
    }

    private fun shouldQuit(): Boolean {
        val entriesCount = supportFragmentManager.backStackEntryCount
        return entriesCount == 0 && binding.exportedChartLayout.childCount == 0
    }

    /**
     * Read the intent used to start this activity (open the text file) as well
     * as the non configuration instance if activity is started after a screen
     * rotate
     */
    private fun readIntent() {
        when (intent.action) {
            null -> doDefaultAction()
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                try {
                    val file = File(URI(intent.data.toString()))
                    doOpenFile(file, false)
                } catch (e: URISyntaxException) {
                    Crouton.showText(this, R.string.toast_intent_invalid_uri, Style.ALERT)
                } catch (e: IllegalArgumentException) {
                    Crouton.showText(this, R.string.toast_intent_illegal, Style.ALERT)
                }
            }
            Constants.ACTION_WIDGET_OPEN -> {
                try {
                    val file = File(URI(intent.data.toString()))
                    doOpenFile(file, intent.getBooleanExtra(Constants.EXTRA_FORCE_READ_ONLY, false))
                } catch (e: URISyntaxException) {
                    Crouton.showText(this, R.string.toast_intent_invalid_uri, Style.ALERT)
                } catch (e: IllegalArgumentException) {
                    Crouton.showText(this, R.string.toast_intent_illegal, Style.ALERT)
                }
            }
            else -> doDefaultAction()
        }
    }

    /**
     * Run the default startup action
     */
    private fun doDefaultAction() {
        val file: File
        var loaded: Boolean
        loaded = false
        if (doOpenBackup()) loaded = true
        if (!loaded && Settings.USE_HOME_PAGE) {
            file = File(Settings.HOME_PAGE_PATH)
            if (!file.exists()) {
                Crouton.showText(this, R.string.toast_open_home_page_error, Style.ALERT)
            } else if (!file.canRead()) {
                Crouton.showText(this, R.string.toast_home_page_cant_read, Style.ALERT)
            } else {
                loaded = doOpenFile(file, false)
            }
        }
        if (!loaded) doClearContents()
    }

    /**
     * Clears the content of the editor. Assumes that user was prompted and
     * previous data was saved
     */
    private fun doClearContents() {
        watcher = null
        isInUndo = true
        binding.editor.setText("")
        currentFilePath = null
        currentFileName = null
        Settings.END_OF_LINE = Settings.DEFAULT_END_OF_LINE
        isDirty = false
        isReadOnly = false
        isWarnedShouldQuit = false
        watcher = TextChangeWatcher()
        isInUndo = false
        TextFileUtils.clearInternal(applicationContext)
    }

    /**
     * Opens the given file and replace the editors content with the file.
     * Assumes that user was prompted and previous data was saved
     *
     * @param file          the file to load
     * @param forceReadOnly force the file to be used as read only
     * @return if the file was loaded successfully
     */
    private fun doOpenFile(file: File?, forceReadOnly: Boolean): Boolean {
        val text: String?
        if (file == null) return false
        try {
            text = TextFileUtils.readTextFile(file)
            if (text != null) {
                isInUndo = true
                if (binding.editor.text.toString() == "") {
                    binding.editor.append(text)
                } else {
                    binding.editor.append(
                        """
                            
                            $text
                            """.trimIndent()
                    )
                }
                watcher = TextChangeWatcher()
                currentFilePath = FileUtils.getCanonizePath(file)
                currentFileName = file.name
                RecentFiles.updateRecentList(currentFilePath)
                RecentFiles.saveRecentList(
                    getSharedPreferences(
                        Constants.PREFERENCES_NAME,
                        MODE_PRIVATE
                    )
                )
                isDirty = false
                isInUndo = false
                if (file.canWrite() && !forceReadOnly) {
                    isReadOnly = false
                    binding.editor.isEnabled = true
                } else {
                    isReadOnly = true
                    binding.editor.isEnabled = false
                }
                return true
            } else {
                Crouton.showText(this, R.string.toast_open_error, Style.ALERT)
            }
        } catch (e: OutOfMemoryError) {
            Crouton.showText(this, R.string.toast_memory_open, Style.ALERT)
        }
        return false
    }

    /**
     * Open the last backup file
     *
     * @return if a backup file was loaded
     */
    private fun doOpenBackup(): Boolean {
        val text: String?
        try {
            text = TextFileUtils.readInternal(this)
            return if (!TextUtils.isEmpty(text)) {
                isInUndo = true
                binding.editor.setText(text)
                watcher = TextChangeWatcher()
                currentFilePath = null
                currentFileName = null
                isDirty = false
                isInUndo = false
                isReadOnly = false
                binding.editor.isEnabled = true
                true
            } else {
                false
            }
        } catch (e: OutOfMemoryError) {
            Crouton.showText(this, R.string.toast_memory_open, Style.ALERT)
        }
        return true
    }

    /**
     * Saves the text editor's content into a file at the given path. If an
     * after save [Runnable] exists, run it
     *
     * @param path the path to the file (must be a valid path and not null)
     */
    private fun doSaveFile(path: String?) {
        if (path == null) {
            Crouton.showText(this, R.string.toast_save_null, Style.ALERT)
            return
        }
        val content: String = binding.editor.text.toString()
        if (!TextFileUtils.writeTextFile("$path.tmp", content)) {
            Crouton.showText(this, R.string.toast_save_temp, Style.ALERT)
            return
        }
        if (!FileUtils.deleteItem(path)) {
            Crouton.showText(this, R.string.toast_save_delete, Style.ALERT)
            return
        }
        if (!FileUtils.renameItem("$path.tmp", path)) {
            Crouton.showText(this, R.string.toast_save_rename, Style.ALERT)
            return
        }
        currentFilePath = FileUtils.getCanonizePath(File(path))
        currentFileName = File(path).name
        RecentFiles.updateRecentList(path)
        RecentFiles.saveRecentList(getSharedPreferences(Constants.PREFERENCES_NAME, MODE_PRIVATE))
        isReadOnly = false
        isDirty = false
        Crouton.showText(this, R.string.toast_save_success, Style.CONFIRM)
    }

    private fun doAutoSaveFile() {
        val text = binding.editor.text.toString()
        if (text.isNotEmpty() && TextFileUtils.writeInternal(this, text)) {
            Toaster.showToast(this, R.string.toast_file_saved_auto, false)
        }
    }

    /**
     * Undo the last change
     *
     * @return if an undo was don
     */
    private fun undo(): Boolean {
        var didUndo = false
        isInUndo = true
        val caret = watcher!!.undo(binding.editor.text)
        if (caret >= 0) {
            binding.editor.setSelection(caret, caret)
            didUndo = true
        }
        isInUndo = false
        return didUndo
    }

    private fun openFile() {
        startActivityForResult(Intent(this, TedOpenActivity::class.java).apply {
            putExtra(Constants.EXTRA_REQUEST_CODE, Constants.REQUEST_OPEN)
        }, Constants.REQUEST_OPEN)
    }

    /**
     * Open the recent files activity to open
     */
    private fun openRecentFile() {
        if (RecentFiles.getRecentFiles().size == 0) {
            Crouton.showText(this, R.string.toast_no_recent_files, Style.ALERT)
            return
        }

        startActivityForResult(Intent(this, TedOpenRecentActivity::class.java), Constants.REQUEST_OPEN)
    }

    /**
     * Warns the user that the next back press will qui the application, or quit
     * if the warning has already been shown
     */
    private fun warnOrQuit() {
        if (isWarnedShouldQuit) {
            finish()
        } else {
            Crouton.showText(this, R.string.toast_warn_no_undo_will_quit, Style.INFO)
            isWarnedShouldQuit = true
        }
    }

    override fun finish() {
        try {
            Crouton.clearCroutonsForActivity(this)
            savePreferencesToLocalData()
        } catch (e: Exception) {
            e.printStackTrace()
            showCustomisedToast(e.message!!)
        }
        super.finish()
    }

    /**
     * General save command : check if a path exist for the current content,
     * then save it , else invoke the [MainActivity.saveContentAs] method
     */
    private fun saveContent() {
        if (currentFilePath == null || currentFilePath!!.isEmpty()) {
            saveContentAs()
        } else {
            doSaveFile(currentFilePath)
        }
    }

    /**
     * General Save as command : prompt the user for a location and file name,
     * then save the editor'd content
     */
    private fun saveContentAs() {
        startActivityForResult(Intent(this, TedSaveAsActivity::class.java), Constants.REQUEST_SAVE_AS)
    }

    private fun setFilters() {
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(usbReceiver, filter)
    }

    private fun createLogEvent(response: ByteArray): ResponseLogEvent {
        val request = lastCommand?.byteArray?.decodeToString() ?: "None"
        return ResponseLogEvent(request, response.decodeToString())
    }

    private fun onDataReceived(bytes: ByteArray) {
        Firebase.analytics.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            bundleOf(
                FirebaseAnalytics.Param.SCREEN_NAME to "responses",
                FirebaseAnalytics.Param.SCREEN_CLASS to moshi.adapter(ResponseLogEvent::class.java).toJson(createLogEvent(bytes))
            )
        )
        var data: String
        val responseForChecking: String
        if (bytes.size == 7) {
            if (String.format("%02X", bytes[0]) == "FE" && String.format("%02X", bytes[1]) == "44") {
                var strHex = ""
                for (b in bytes) {
                    strHex += String.format("%02X-", b)
                }
                val end = strHex.length - 1
                data = strHex.substring(0, end)
                val strH = String.format(
                    "%02X%02X", bytes[3],
                    bytes[4]
                )
                val co2 = strH.toInt(16)
                val tempChart = chart ?: return
                val yMax: Int = tempChart.renderer.yAxisMax.toInt()
                if (co2 >= yMax) {
                    tempChart.renderer.yAxisMax = if (currentSeries.itemCount == 0) {
                        (3 * co2).toDouble()
                    } else {
                        (co2 + co2 * 15 / 100f).toDouble()
                    }
                }

                // auto
                val delay = prefs.getInt(PrefConstants.DELAY, 2)
                val duration = prefs.getInt(PrefConstants.DURATION, 3)
                val isAuto = prefs.getBoolean(PrefConstants.IS_AUTO, false)
                if (isAuto) {
                    if (readingCount == (duration * 60 / delay)) {
                        incCountMeasure()
                        viewModel.setCurrentChartIndex(1)
                    } else if (readingCount == (duration * 60)) {
                        incCountMeasure()
                        viewModel.setCurrentChartIndex(2)
                    }
                }
                val shouldInitDate = countMeasure != oldCountMeasure
                if (countMeasure != oldCountMeasure) {
                    refreshOldCountMeasure()
                }
                viewModel.onCurrentChartWasModified(wasModified = shouldInitDate)
                if (viewModel.isMeasuring) {
                    currentSeries.add(readingCount.toDouble(), co2.toDouble())
                    chartView?.repaint()
                }
                if (co2 == 10000) {
                    showCustomisedToast("Dilute sample")
                }
                data += "\nCO2: $co2 ppm"
                refreshTextAccordToSensor(false, co2.toString())
                responseForChecking = co2.toString()
            } else {
                data = String(bytes)
                data = data.replace("\r", "")
                data = data.replace("\n", "")
                responseForChecking = data
            }
        } else {
            data = String(bytes)
            data = data.replace("\r", "")
            data = data.replace("\n", "")
            refreshTextAccordToSensor(true, data)
            responseForChecking = data
        }

        Utils.appendText(binding.output, "Rx: $data")
        binding.scrollView.smoothScrollTo(0, 0)
        viewModel.onDataReceived(responseForChecking, bytes)
    }

    private fun incCountMeasure() {
        countMeasure++
    }

    private fun refreshOldCountMeasure() {
        oldCountMeasure = countMeasure
    }

    fun setUsbConnected(isUsbConnected: Boolean) {
        this.isUsbConnected = isUsbConnected
        invalidateOptionsMenu()
    }

    private fun refreshTextAccordToSensor(isTemperature: Boolean, text: String?) {
        if (isTemperature) {
            TemperatureData.parse(text).also { temperatureData ->
                if (temperatureData.isCorrect) {
                    val updateRunnable = Runnable {
                        binding.temperature.text = (temperatureData.temperature1 + viewModel.temperatureShift.value!!).toString()
                    }
                    updateRunnable.run()
                } else {
                    binding.temperature.text = temperatureData.wrongPosition.toString()
                }
            }
        } else {
            binding.co2.text = text
        }
    }

    private fun incReadingCount() {
        readingCount++
    }

    private fun invokeAutoCalculations() {
        supportFragmentManager.findFragmentById(R.id.bottom_fragment)!!.view!!.findViewById<View>(R.id.calculate_ppm_auto)
            .performClick()
    }

    private fun clearData() {
        val tempChart = chart
        if (tempChart != null) {
            for (series in tempChart.dataset.series) {
                series.clear()
            }
        }
        oldCountMeasure = 0
        countMeasure = oldCountMeasure
        chartView?.repaint()
    }

    override fun currentDate(): Date {
        return reportDate!!
    }

    override fun reportDateString(): String {
        reportDate = Date()
        return FORMATTER.format(reportDate!!)
    }

    override fun sampleId(): String? {
        return null
    }

    override fun location(): String? {
        return null
    }

    override fun countMinutes(): Int {
        return prefs.getInt(PrefConstants.DURATION, 0)
    }

    override fun volume(): Int {
        return prefs.getInt(PrefConstants.VOLUME, 0)
    }

    override fun operator(): String? {
        return null
    }

    override fun dateString(): String {
        return FORMATTER.format(reportDate!!)
    }

    override fun writeReport(reportHtml: String, fileName: String) {
        val file = File(reportFolders(), "$fileName.html")
        file.parentFile!!.mkdirs()
        try {
            file.createNewFile()
            TextFileUtils.writeTextFile(file.absolutePath, reportHtml)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun reportFolders(): String {
        return File(Environment.getExternalStorageDirectory(), AppData.REPORT_FOLDER_NAME)
            .absolutePath
    }

    companion object {
        private val FORMATTER = SimpleDateFormat("MM.dd.yyyy HH:mm:ss")
        private const val TAG = "MainActivity"
    }
}