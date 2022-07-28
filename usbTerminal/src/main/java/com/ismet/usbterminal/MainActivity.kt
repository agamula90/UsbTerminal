package com.ismet.usbterminal

import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.hardware.usb.UsbManager
import android.os.*
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.SparseBooleanArray
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnLongClickListener
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.viewModels
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ismet.usbterminal.data.*
import com.ismet.usbterminal.mainscreen.powercommands.FilePowerCommandsFactory
import com.ismet.usbterminal.mainscreen.powercommands.PowerCommandsFactory
import com.ismet.usbterminal.utils.AlertDialogTwoButtonsCreator
import com.ismet.usbterminal.utils.AlertDialogTwoButtonsCreator.OnInitLayoutListener
import com.ismet.usbterminal.utils.GraphPopulatorUtils
import com.ismet.usbterminal.utils.Utils
import com.ismet.usbterminalnew.BuildConfig
import com.ismet.usbterminalnew.R
import com.ismet.usbterminalnew.databinding.LayoutEditorUpdatedBinding
import com.proggroup.areasquarecalculator.activities.BaseAttachableActivity
import com.proggroup.areasquarecalculator.utils.ToastUtils
import dagger.hilt.android.AndroidEntryPoint
import de.neofonie.mobile.app.android.widget.crouton.Crouton
import de.neofonie.mobile.app.android.widget.crouton.Style
import fr.xgouchet.androidlib.data.FileUtils
import fr.xgouchet.androidlib.ui.Toaster
import fr.xgouchet.androidlib.ui.activity.ActivityDecorator
import fr.xgouchet.texteditor.common.Constants
import fr.xgouchet.texteditor.common.RecentFiles
import fr.xgouchet.texteditor.common.Settings
import fr.xgouchet.texteditor.common.TextFileUtils
import fr.xgouchet.texteditor.undo.TextChangeWatcher
import org.achartengine.GraphicalView
import org.achartengine.model.XYMultipleSeriesDataset
import org.achartengine.model.XYSeries
import org.achartengine.renderer.XYMultipleSeriesRenderer
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.*

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

    private lateinit var handler: Handler
    private var isUsbConnected = false

    var countMeasure = 0
        private set
    private var oldCountMeasure = 0
    var isTimerRunning = false
    var readingCount = 0
        private set

    private val chartIndexToDate: MutableMap<Int, String> = HashMap()
    lateinit var prefs: SharedPreferences
    lateinit var currentSeries: XYSeries
    lateinit var chartView: GraphicalView
    private lateinit var graphSeriesDataset: XYMultipleSeriesDataset
    lateinit var renderer: XYMultipleSeriesRenderer
    private var chartSeries: XYSeries? = null
    private lateinit var usbDeviceConnection: UsbDeviceConnection
    private var usbDevice: UsbDevice? = null
    private val viewModel: MainViewModel by viewModels()
    private var temperatureShift = 0
    private var lastTimePressed: Long = 0
    private var reportDate: Date? = null
    private var isPowerPressed = false
    private lateinit var binding: LayoutEditorUpdatedBinding
    lateinit var powerCommandsFactory: PowerCommandsFactory

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
        loadPreferencesFromLocalData()
        observeChartUpdates()
        observeEvents()
        handler = object: Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                if (msg.what == MESSAGE_INTERRUPT_ACTIONS) {
                    handleResponse( "")
                }
            }
        }

        isReadIntent = true
        binding.editor.addTextChangedListener(this)
        binding.editor.updateFromSettings()
        watcher = TextChangeWatcher()
        isWarnedShouldQuit = false
        initPowerAccordToItState()

        binding.power.setOnClickListener { v ->
            when (powerCommandsFactory.currentPowerState()) {
                PowerState.OFF -> {
                    v.isEnabled = false
                    powerOn()
                    //TODO uncomment for simulating
                    //simulateClick2();
                }
                PowerState.ON -> {
                    v.isEnabled = false
                    powerOff()
                    //TODO uncomment for simulating
                    //simulateClick1();
                }
                else -> {
                    //do nothing
                }
            }
        }

        changeBackground(binding.temperatureBackground)
        changeBackground(binding.co2Background)
        binding.buttonOn1.text = prefs.getString(PrefConstants.ON_NAME1, PrefConstants.ON_NAME_DEFAULT)
        binding.buttonOn1.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
        binding.buttonOn1.setOnClickListener(AutoPullResolverListener(object : AutoPullResolverCallback {
            private var command: String? = null
            override fun onPrePullStopped() {
                val str_on_name1t =
                    prefs.getString(PrefConstants.ON_NAME1, PrefConstants.ON_NAME_DEFAULT)
                val str_off_name1t =
                    prefs.getString(PrefConstants.OFF_NAME1, PrefConstants.OFF_NAME_DEFAULT)
                val s = binding.buttonOn1.tag.toString()
                command = "" //"/5H1000R";
                if (s == PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                    command = prefs.getString(PrefConstants.ON1, "")
                    binding.buttonOn1.text = str_off_name1t
                    binding.buttonOn1.tag = PrefConstants.OFF_NAME_DEFAULT.lowercase(Locale.getDefault())
                } else {
                    command = prefs.getString(PrefConstants.OFF1, "")
                    binding.buttonOn1.text = str_on_name1t
                    binding.buttonOn1.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
                }
            }

            override fun onPostPullStopped() {
                sendCommand(command)
            }

            override fun onPostPullStarted() {}
        }))
        binding.buttonOn1.setOnLongClickListener(object : OnLongClickListener {
            private lateinit var editOn: EditText
            private lateinit var editOff: EditText
            private lateinit var editOn1: EditText
            private lateinit var editOff1: EditText
            override fun onLongClick(v: View): Boolean {
                val initLayoutListener =
                    OnInitLayoutListener { contentView ->
                        editOn = contentView.findViewById<View>(R.id.editOn) as EditText
                        editOff = contentView.findViewById<View>(R.id.editOff) as EditText
                        editOn1 = contentView.findViewById<View>(R.id.editOn1) as EditText
                        editOff1 = contentView.findViewById<View>(R.id.editOff1) as EditText
                        changeTextsForButtons(contentView)
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
                        editOn.setText(str_on)
                        editOff.setText(str_off)
                        editOn1.setText(str_on_name)
                        editOff1.setText(str_off_name)
                    }
                val okListener =
                    DialogInterface.OnClickListener { dialog, _ ->
                        val inputManager =
                            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        inputManager.hideSoftInputFromWindow(
                            (dialog as AlertDialog)
                                .currentFocus!!.windowToken, 0
                        )
                        val strOn = editOn.text.toString()
                        val strOff = editOff.text.toString()
                        val strOn1 = editOn1.text.toString()
                        val strOff1 = editOff1.text.toString()
                        if (strOn == "" || strOff == "" || strOn1 == "" || strOff1 == "") {
                            showCustomisedToast("Please enter all values")
                            return@OnClickListener
                        }
                        val edit = prefs.edit()
                        edit.putString(PrefConstants.ON1, strOn)
                        edit.putString(PrefConstants.OFF1, strOff)
                        edit.putString(PrefConstants.ON_NAME1, strOn1)
                        edit.putString(PrefConstants.OFF_NAME1, strOff1)
                        edit.apply()
                        val s = binding.buttonOn1.tag.toString()
                        if (s == "on") {
                            binding.buttonOn1.text = strOn1
                        } else {
                            binding.buttonOn1.text = strOff1
                        }
                        dialog.cancel()
                    }
                val cancelListener =
                    DialogInterface.OnClickListener { dialog, _ ->
                        val inputManager =
                            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        inputManager.hideSoftInputFromWindow(
                            (dialog as AlertDialog)
                                .currentFocus!!.windowToken, 0
                        )
                        dialog.cancel()
                    }
                AlertDialogTwoButtonsCreator.createTwoButtonsAlert(
                    this@MainActivity,
                    R.layout.layout_dialog_on_off,
                    "Set On/Off " + "commands",
                    okListener,
                    cancelListener,
                    initLayoutListener
                ).create().show()
                return true
            }
        })
        binding.buttonOn2.text = prefs.getString(PrefConstants.ON_NAME2, PrefConstants.ON_NAME_DEFAULT)
        binding.buttonOn2.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
        EToCApplication.getInstance().currentTemperatureRequest = prefs.getString(PrefConstants.ON2, "/5H750R")
        binding.buttonOn2.setOnClickListener(AutoPullResolverListener(object : AutoPullResolverCallback {
            private var command: String? = null
            override fun onPrePullStopped() {
                val str_on_name2t =
                    prefs.getString(PrefConstants.ON_NAME2, PrefConstants.ON_NAME_DEFAULT)
                val str_off_name2t =
                    prefs.getString(PrefConstants.OFF_NAME2, PrefConstants.OFF_NAME_DEFAULT)
                val s = binding.buttonOn2.tag.toString()
                command = "" //"/5H1000R";
                val defaultValue: String
                val prefName: String
                if (s == PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                    prefName = PrefConstants.OFF2
                    defaultValue = "/5H0000R"
                    command = prefs.getString(PrefConstants.ON2, "")
                    binding.buttonOn2.text = str_off_name2t
                    binding.buttonOn2.tag = PrefConstants.OFF_NAME_DEFAULT.lowercase(Locale.getDefault())
                } else {
                    prefName = PrefConstants.ON2
                    defaultValue = "/5H750R"
                    command = prefs.getString(PrefConstants.OFF2, "")
                    binding.buttonOn2.text = str_on_name2t
                    binding.buttonOn2.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
                    binding.buttonOn2.alpha = 0.6f
                }
                EToCApplication.getInstance().currentTemperatureRequest =
                    prefs.getString(prefName, defaultValue)
            }

            override fun onPostPullStopped() {
                sendCommand(command)
            }

            override fun onPostPullStarted() {
                val tag = binding.buttonOn2.tag.toString()
                binding.buttonOn2.post {
                    if (tag == PrefConstants.ON_NAME_DEFAULT.lowercase(
                            Locale.getDefault()
                        )
                    ) {
                        binding.buttonOn2.alpha = 1f
                        binding.buttonOn2.setBackgroundResource(R.drawable.button_drawable)
                    } else {
                        binding.buttonOn2.alpha = 1f
                        binding.buttonOn2.setBackgroundResource(R.drawable.power_on_drawable)
                    }
                }
            }
        }))
        binding.buttonOn2.setOnLongClickListener(object : OnLongClickListener {
            private lateinit var editOn: EditText
            private lateinit var editOff: EditText
            private lateinit var editOn1: EditText
            private lateinit var editOff1: EditText
            override fun onLongClick(v: View): Boolean {
                val initLayoutListener =
                    OnInitLayoutListener { contentView ->
                        editOn = contentView.findViewById<View>(R.id.editOn) as EditText
                        editOff = contentView.findViewById<View>(R.id.editOff) as EditText
                        editOn1 = contentView.findViewById<View>(R.id.editOn1) as EditText
                        editOff1 = contentView.findViewById<View>(R.id.editOff1) as EditText
                        changeTextsForButtons(contentView)
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
                        editOn.setText(str_on)
                        editOff.setText(str_off)
                        editOn1.setText(str_on_name)
                        editOff1.setText(str_off_name)
                    }
                val okListener =
                    DialogInterface.OnClickListener { dialog, _ ->
                        val inputManager =
                            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        inputManager.hideSoftInputFromWindow(
                            (dialog as AlertDialog)
                                .currentFocus!!.windowToken, 0
                        )
                        val strOn = editOn.text.toString()
                        val strOff = editOff.text.toString()
                        val strOn1 = editOn1.text.toString()
                        val strOff1 = editOff1.text.toString()
                        if (strOn == "" || strOff == "" || strOn1 == "" || strOff1 == "") {
                            showCustomisedToast("Please enter all values")
                            return@OnClickListener
                        }
                        val edit = prefs.edit()
                        edit.putString(PrefConstants.ON2, strOn)
                        edit.putString(PrefConstants.OFF2, strOff)
                        edit.putString(PrefConstants.ON_NAME2, strOn1)
                        edit.putString(PrefConstants.OFF_NAME2, strOff1)
                        edit.apply()
                        val s = binding.buttonOn2.tag.toString()
                        val defaultValue: String
                        val prefName: String
                        if (s == PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                            prefName = PrefConstants.ON2
                            defaultValue = "/5H750R"
                            binding.buttonOn2.text = strOn1
                        } else {
                            prefName = PrefConstants.OFF2
                            defaultValue = "/5H0000R"
                            binding.buttonOn2.text = strOff1
                        }
                        EToCApplication.getInstance().currentTemperatureRequest = prefs
                            .getString(prefName, defaultValue)
                        dialog.cancel()
                    }
                val cancelListener =
                    DialogInterface.OnClickListener { dialog, _ ->
                        val inputManager =
                            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        inputManager.hideSoftInputFromWindow(
                            (dialog as AlertDialog)
                                .currentFocus!!.windowToken, 0
                        )
                        dialog.cancel()
                    }
                AlertDialogTwoButtonsCreator.createTwoButtonsAlert(
                    this@MainActivity,
                    R.layout.layout_dialog_on_off,
                    "Set On/Off commands",
                    okListener,
                    cancelListener,
                    initLayoutListener
                ).create().show()
                return true
            }
        })
        binding.buttonPpm.text = prefs.getString(PrefConstants.ON_NAME3, PrefConstants.ON_NAME_DEFAULT)
        binding.buttonPpm.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
        binding.buttonPpm.setOnClickListener(AutoPullResolverListener(object : AutoPullResolverCallback {
            private var command: String? = null
            override fun onPrePullStopped() {
                val str_on_name3 =
                    prefs.getString(PrefConstants.ON_NAME3, PrefConstants.ON_NAME_DEFAULT)
                val str_off_name3 =
                    prefs.getString(PrefConstants.OFF_NAME3, PrefConstants.OFF_NAME_DEFAULT)
                val s = binding.buttonPpm.tag.toString()
                if (s == PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                    binding.buttonPpm.text = str_off_name3
                    binding.buttonPpm.tag = PrefConstants.OFF_NAME_DEFAULT.lowercase(Locale.getDefault())
                    command = prefs.getString(PrefConstants.ON3, "")
                } else {
                    binding.buttonPpm.text = str_on_name3
                    binding.buttonPpm.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
                    command = prefs.getString(PrefConstants.OFF3, "")
                }
            }

            override fun onPostPullStopped() {
                sendCommand(command)
            }

            override fun onPostPullStarted() {}
        }))
        binding.buttonPpm.setOnLongClickListener(object : OnLongClickListener {
            private lateinit var editOn: EditText
            private lateinit var editOff: EditText
            private lateinit var editOn1: EditText
            private lateinit var editOff1: EditText
            override fun onLongClick(v: View): Boolean {
                val initLayoutListener =
                    OnInitLayoutListener { contentView ->
                        editOn = contentView.findViewById<View>(R.id.editOn) as EditText
                        editOff = contentView.findViewById<View>(R.id.editOff) as EditText
                        editOn1 = contentView.findViewById<View>(R.id.editOn1) as EditText
                        editOff1 = contentView.findViewById<View>(R.id.editOff1) as EditText
                        changeTextsForButtons(contentView)
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
                        editOn.setText(str_on)
                        editOff.setText(str_off)
                        editOn1.setText(str_on_name)
                        editOff1.setText(str_off_name)
                    }
                val okListener =
                    DialogInterface.OnClickListener { dialog, _ ->
                        val inputManager =
                            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        inputManager.hideSoftInputFromWindow(
                            (dialog as AlertDialog)
                                .currentFocus!!.windowToken, 0
                        )
                        val strOn = editOn.text.toString()
                        val strOff = editOff.text.toString()
                        val strOn1 = editOn1.text.toString()
                        val strOff1 = editOff1.text.toString()
                        if (strOn == "" || strOff == "" || strOn1 == "" || strOff1 == "") {
                            showCustomisedToast("Please enter all values")
                            return@OnClickListener
                        }
                        val edit = prefs.edit()
                        edit.putString(PrefConstants.ON3, strOn)
                        edit.putString(PrefConstants.OFF3, strOff)
                        edit.putString(PrefConstants.ON_NAME3, strOn1)
                        edit.putString(PrefConstants.OFF_NAME3, strOff1)
                        edit.apply()
                        val s = binding.buttonPpm.tag.toString()
                        if (s == PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                            binding.buttonPpm.text = strOn1
                        } else {
                            binding.buttonPpm.text = strOff1
                        }
                        dialog.cancel()
                    }
                val cancelListener =
                    DialogInterface.OnClickListener { dialog, _ ->
                        val inputManager =
                            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        inputManager.hideSoftInputFromWindow(
                            (dialog as AlertDialog)
                                .currentFocus!!.windowToken, 0
                        )
                        dialog.cancel()
                    }
                AlertDialogTwoButtonsCreator.createTwoButtonsAlert(
                    this@MainActivity,
                    R.layout.layout_dialog_on_off,
                    "Set On/Off commands",
                    okListener,
                    cancelListener,
                    initLayoutListener
                ).create().show()
                return true
            }
        })
        binding.buttonSend.setOnClickListener(AutoPullResolverListener(object :
            AutoPullResolverCallback {
            override fun onPrePullStopped() {
            }

            override fun onPostPullStopped() {
                sendMessage()
            }

            override fun onPostPullStarted() {}
        }))
        binding.editor.setOnEditorActionListener { _, actionId, _ ->
            var handled = false
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                // sendMessage();
                handled = true
            }
            handled
        }
        binding.buttonClear.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                if (isTimerRunning) {
                    showCustomisedToast("Timer is running. Please wait")
                    return
                }
                val items = arrayOf<CharSequence>(
                    "New Measure", "Tx", "LM", "Chart 1",
                    "Chart 2", "Chart" + " 3"
                )
                val checkedItems = booleanArrayOf(false, false, false, false, false, false)
                val mItemsChecked = SparseBooleanArray(checkedItems.size)
                for (i in checkedItems.indices) {
                    mItemsChecked.put(i, checkedItems[i])
                }
                val alert = AlertDialog.Builder(this@MainActivity)
                alert.setTitle("Select items")
                alert.setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                    mItemsChecked.put(which, isChecked)
                }
                alert.setPositiveButton("Select/Clear") { dialog, _ ->
                    if (mItemsChecked[1]) {
                        binding.editor.setText("")
                    }
                    if (mItemsChecked[2]) {
                        binding.output.text = ""
                    }
                    var isCleared = false
                    if (mItemsChecked[3]) {
                        graphSeriesDataset.getSeriesAt(0).clear()
                        isCleared = true
                        if (chartIndexToDate.containsKey(1)) {
                            Utils.deleteFiles(chartIndexToDate[1], "_R1")
                        }
                    }
                    if (mItemsChecked[4]) {
                        graphSeriesDataset.getSeriesAt(1).clear()
                        isCleared = true
                        if (chartIndexToDate.containsKey(2)) {
                            Utils.deleteFiles(chartIndexToDate[2], "_R2")
                        }
                    }
                    if (mItemsChecked[5]) {
                        graphSeriesDataset.getSeriesAt(2).clear()
                        isCleared = true
                        if (chartIndexToDate.containsKey(3)) {
                            Utils.deleteFiles(chartIndexToDate[3], "_R3")
                        }
                    }
                    if (isCleared) {
                        var existInitedGraphCurve = false
                        for (i in 0 until graphSeriesDataset.seriesCount) {
                            if (graphSeriesDataset.getSeriesAt(i).itemCount != 0) {
                                existInitedGraphCurve = true
                                break
                            }
                        }
                        if (!existInitedGraphCurve) {
                            GraphPopulatorUtils.clearYTextLabels(renderer)
                        }
                        chartView.repaint()
                    }
                    if (mItemsChecked[0]) {
                        clearData()
                    }
                    dialog.cancel()
                }
                alert.setNegativeButton(
                    "Close"
                ) { dialog, _ -> dialog.cancel() }
                alert.create().show()
            }
        })
        binding.buttonMeasure.setOnClickListener(object : View.OnClickListener {
            lateinit var editDelay: EditText
            lateinit var editDuration: EditText
            lateinit var editKnownPpm: EditText
            lateinit var editVolume: EditText
            lateinit var editUserComment: EditText
            lateinit var commandsEditText1: EditText
            lateinit var commandsEditText2: EditText
            lateinit var commandsEditText3: EditText
            lateinit var chkAutoManual: CheckBox
            lateinit var chkKnownPpm: CheckBox
            lateinit var chkUseRecentDirectory: CheckBox
            lateinit var llkppm: LinearLayout
            lateinit var ll_user_comment: LinearLayout
            lateinit var mRadioGroup: RadioGroup
            lateinit var mRadio1: RadioButton
            lateinit var mRadio2: RadioButton
            lateinit var mRadio3: RadioButton
            override fun onClick(v: View) {
                if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
                    return
                }
                if (isTimerRunning) {
                    showCustomisedToast("Timer is running. Please wait")
                    return
                }
                val arrSeries = graphSeriesDataset.series
                var i = 0
                var isChart1Clear = true
                var isChart2Clear = true
                var isChart3Clear = true
                for (series in arrSeries) {
                    if (series.itemCount > 0) {
                        when (i) {
                            0 -> isChart1Clear = false
                            1 -> isChart2Clear = false
                            2 -> isChart3Clear = false
                            else -> {}
                        }
                    }
                    i++
                }
                if (!isChart1Clear && !isChart2Clear && !isChart3Clear) {
                    showCustomisedToast("No chart available. Please clear one of the charts")
                    return
                }
                v.isEnabled = false
                val initLayoutListener =
                    OnInitLayoutListener { contentView ->
                        editDelay = contentView.findViewById<View>(R.id.editDelay) as EditText
                        editDuration = contentView.findViewById<View>(R.id.editDuration) as EditText
                        editKnownPpm = contentView.findViewById<View>(R.id.editKnownPpm) as EditText
                        editVolume = contentView.findViewById<View>(R.id.editVolume) as EditText
                        editUserComment =
                            contentView.findViewById<View>(R.id.editUserComment) as EditText

                        chkAutoManual =
                            contentView.findViewById<View>(R.id.chkAutoManual) as CheckBox
                        chkKnownPpm = contentView.findViewById<View>(R.id.chkKnownPpm) as CheckBox
                        chkUseRecentDirectory =
                            contentView.findViewById<View>(R.id.chkUseRecentDirectory) as CheckBox
                        llkppm = contentView.findViewById<View>(R.id.llkppm) as LinearLayout
                        ll_user_comment =
                            contentView.findViewById<View>(R.id.ll_user_comment) as LinearLayout
                        commandsEditText1 =
                            contentView.findViewById<View>(R.id.commandsEditText1) as EditText
                        commandsEditText2 =
                            contentView.findViewById<View>(R.id.commandsEditText2) as EditText
                        commandsEditText3 =
                            contentView.findViewById<View>(R.id.commandsEditText3) as EditText
                        mRadio1 = contentView.findViewById<View>(R.id.radio1) as RadioButton
                        mRadio2 = contentView.findViewById<View>(R.id.radio2) as RadioButton
                        mRadio3 = contentView.findViewById<View>(R.id.radio3) as RadioButton
                        mRadioGroup = contentView.findViewById<View>(R.id.radio_group) as RadioGroup
                        val delay_v = prefs.getInt(PrefConstants.DELAY, PrefConstants.DELAY_DEFAULT)
                        val duration_v = prefs.getInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)
                        val volume = prefs.getInt(PrefConstants.VOLUME, PrefConstants.VOLUME_DEFAULT)
                        val kppm = prefs.getInt(PrefConstants.KPPM, -1)
                        val user_comment = prefs.getString(PrefConstants.USER_COMMENT, "")
                        editDelay.setText(delay_v.toString())
                        editDuration.setText(duration_v.toString())
                        editVolume.setText(volume.toString())
                        editUserComment.setText(user_comment)
                        commandsEditText1.setText(prefs.getString(PrefConstants.MEASURE_FILE_NAME1, PrefConstants.MEASURE_FILE_NAME1_DEFAULT))
                        commandsEditText2.setText(prefs.getString(PrefConstants.MEASURE_FILE_NAME2, PrefConstants.MEASURE_FILE_NAME2_DEFAULT))
                        commandsEditText3.setText(prefs.getString(PrefConstants.MEASURE_FILE_NAME3, PrefConstants.MEASURE_FILE_NAME3_DEFAULT))
                        if (kppm != -1) {
                            editKnownPpm.setText(kppm.toString())
                        }
                        val isAuto = prefs.getBoolean(PrefConstants.IS_AUTO, false)
                        chkAutoManual.isChecked = isAuto
                        chkKnownPpm.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                editKnownPpm.isEnabled = true
                                llkppm.visibility = View.VISIBLE
                            } else {
                                editKnownPpm.isEnabled = false
                                llkppm.visibility = View.GONE
                            }
                        }
                    }
                val okListener: DialogInterface.OnClickListener =
                    object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface, which: Int) {
                            v.isEnabled = true
                            val inputManager =
                                getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                            inputManager.hideSoftInputFromWindow(
                                (dialog as AlertDialog)
                                    .currentFocus!!.windowToken, 0
                            )
                            val strDelay = editDelay.text.toString()
                            val strDuration = editDuration.text.toString()
                            if (strDelay == "" || strDuration == "") {
                                showCustomisedToast("Please enter all values")
                                return
                            }
                            if (chkKnownPpm.isChecked) {
                                val strkPPM = editKnownPpm.text.toString()
                                if (strkPPM == "") {
                                    showCustomisedToast("Please enter ppm values")
                                    return
                                } else {
                                    val kppm = strkPPM.toInt()
                                    val edit = prefs.edit()
                                    edit.putInt(PrefConstants.KPPM, kppm)
                                    edit.apply()
                                }
                            } else {
                                val edit = prefs.edit()
                                edit.remove(PrefConstants.KPPM)
                                edit.apply()
                            }

                            run {
                                val str_uc = editUserComment.text.toString()
                                if (str_uc == "") {
                                    showCustomisedToast("Please enter comments")
                                    return
                                } else {
                                    val edit = prefs.edit()
                                    edit.putString(
                                        PrefConstants.USER_COMMENT,
                                        str_uc
                                    )
                                    edit.apply()
                                }
                            }
                            val strVolume = editVolume.text.toString()
                            if (strVolume == "") {
                                showCustomisedToast("Please enter volume values")
                                return
                            } else {
                                val volume = strVolume.toInt()
                                val edit = prefs.edit()
                                edit.putInt(PrefConstants.VOLUME, volume)
                                edit.apply()
                            }
                            val b = chkAutoManual.isChecked
                            var edit = prefs.edit()
                            edit.putBoolean(PrefConstants.IS_AUTO, b)
                            edit.putBoolean(
                                PrefConstants.SAVE_AS_CALIBRATION,
                                chkKnownPpm.isChecked
                            )
                            edit.apply()
                            val delay = strDelay.toInt()
                            val duration = strDuration.toInt()
                            if (delay == 0 || duration == 0) {
                                showCustomisedToast("zero is not allowed")
                                return
                            } else {
                                if (countMeasure == 0) {
                                    val graphData = GraphPopulatorUtils.createXYChart(
                                        duration,
                                        delay
                                    )
                                    renderer = graphData.renderer
                                    graphSeriesDataset = graphData.seriesDataset
                                    currentSeries = graphData.xySeries
                                    chartView = GraphPopulatorUtils.attachXYChartIntoLayout(
                                        this@MainActivity,
                                        graphData.createChart()
                                    )
                                }
                                countMeasure++
                                edit = prefs.edit()
                                edit.putInt(PrefConstants.DELAY, delay)
                                edit.putInt(PrefConstants.DURATION, duration)
                                edit.apply()
                                val future = (duration * 60 * 1000).toLong()
                                val delay_timer = (delay * 1000).toLong()
                                if (graphSeriesDataset.getSeriesAt(0).itemCount == 0) {
                                    viewModel.chartIdx = 1
                                    readingCount = 0
                                    currentSeries = graphSeriesDataset.getSeriesAt(0)
                                } else if (graphSeriesDataset.getSeriesAt(1).itemCount == 0) {
                                    viewModel.chartIdx = 2
                                    readingCount = duration * 60 / delay
                                    currentSeries = graphSeriesDataset.getSeriesAt(1)
                                } else if (graphSeriesDataset.getSeriesAt(2).itemCount == 0) {
                                    viewModel.chartIdx = 3
                                    readingCount = duration * 60
                                    currentSeries = graphSeriesDataset.getSeriesAt(2)
                                }
                                val checkedId = mRadioGroup.checkedRadioButtonId
                                if (binding.editor.text.toString() == "" && checkedId == -1) {
                                    showCustomisedToast("Please enter command")
                                    return
                                }
                                val editor = prefs.edit()
                                editor.putString(PrefConstants.MEASURE_FILE_NAME1, commandsEditText1.text.toString())
                                editor.putString(PrefConstants.MEASURE_FILE_NAME2, commandsEditText2.text.toString())
                                editor.putString(PrefConstants.MEASURE_FILE_NAME3, commandsEditText3.text.toString())
                                editor.apply()

                                val shouldUseRecentDirectory = chkUseRecentDirectory.isChecked

                                val filePath = when {
                                    checkedId == mRadio1.id -> commandsEditText1.text.toString()
                                    checkedId == mRadio2.id -> commandsEditText2.text.toString()
                                    checkedId == mRadio3.id -> commandsEditText3.text.toString()
                                    checkedId == -1 && binding.editor.text.toString().isEmpty() -> {
                                        showCustomisedToast("File not found")
                                        return
                                    }
                                    checkedId == -1 -> {
                                        viewModel.readCommandsFromText(
                                            text = binding.editor.text.toString(),
                                            shouldUseRecentDirectory = shouldUseRecentDirectory,
                                            runningTime = future,
                                            oneLoopTime = delay_timer
                                        )
                                        return
                                    }
                                    else -> {
                                        showCustomisedToast("Unexpected error")
                                        return
                                    }
                                }
                                viewModel.readCommandsFromFile(
                                    file = File(
                                        File(Environment.getExternalStorageDirectory(), AppData.SYSTEM_SETTINGS_FOLDER_NAME),
                                        filePath
                                    ),
                                    shouldUseRecentDirectory = shouldUseRecentDirectory,
                                    runningTime = future,
                                    oneLoopTime = delay_timer
                                )
                            }
                            dialog.cancel()
                        }
                    }
                val cancelListener =
                    DialogInterface.OnClickListener { dialog, _ ->
                        val inputManager =
                            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        inputManager.hideSoftInputFromWindow(
                            (dialog as AlertDialog)
                                .currentFocus!!.windowToken, 0
                        )
                        dialog.cancel()
                        v.isEnabled = true
                    }
                val alertDialog = AlertDialogTwoButtonsCreator.createTwoButtonsAlert(
                    this@MainActivity, R.layout.layout_dialog_measure, "Start Measure",
                    okListener, cancelListener, initLayoutListener
                ).create()
                alertDialog.setOnCancelListener { v.isEnabled = true }
                alertDialog.show()
            }
        })
        setFilters()
        usbDeviceConnection = UsbDeviceConnection(this)
        usbDeviceConnection.permissionCallback = PermissionCallback { _, device ->
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
        val delay_v = prefs.getInt(PrefConstants.DELAY, PrefConstants.DELAY_DEFAULT)
        val duration_v = prefs.getInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)
        val preferences = prefs
        if (powerCommandsFactory.currentPowerState() == PowerState.PRE_LOOPING) {
            EToCApplication.getInstance().isPreLooping = true
            viewModel.waitForCooling()

            //TODO uncomment for simulating
            /*Message message = mHandler.obtainMessage(EToCMainHandler.MESSAGE_SIMULATE_RESPONSE);
			message.obj = "";
			mHandler.sendMessageDelayed(message, 10800);*/
        }
        if (!preferences.contains(PrefConstants.DELAY)) {
            val editor = prefs.edit()
            editor.putInt(PrefConstants.DELAY, PrefConstants.DELAY_DEFAULT)
            editor.putInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)
            editor.putInt(PrefConstants.VOLUME, PrefConstants.VOLUME_DEFAULT)
            editor.apply()
        }
        val graphData = GraphPopulatorUtils.createXYChart(duration_v, delay_v)
        renderer = graphData.renderer
        graphSeriesDataset = graphData.seriesDataset
        currentSeries = graphData.xySeries
        chartView = GraphPopulatorUtils.attachXYChartIntoLayout(this, graphData.createChart())
        val actionBar = supportActionBar
        actionBar!!.setDisplayUseLogoEnabled(true)
        actionBar.setLogo(R.drawable.ic_launcher)
        val titleView = actionBar.customView.findViewById<View>(R.id.title) as TextView
        titleView.setTextColor(Color.WHITE)
        (titleView.layoutParams as RelativeLayout.LayoutParams).addRule(
            RelativeLayout.CENTER_HORIZONTAL,
            0
        )

        //Intent timeIntent = GraphPopulatorUtils.createTimeChart(this);
        //GraphPopulatorUtils.attachTimeChartIntoLayout(this, (AbstractChart)timeIntent.getExtras
        //		 ().get("chart"));
    }

    private fun showCustomisedToast(message: String) {
        val customToast = Toast.makeText(this, message, Toast.LENGTH_LONG)
        ToastUtils.wrap(customToast)
        customToast.show()
    }

    @Deprecated("Use showCustomisedToast instead")
    fun showToastMessage(message: String?) {
        // showCustomisedToast(message.toString())
    }

    fun startSendingTemperatureOrCo2Requests() {
        viewModel.startSendingTemperatureOrCo2Requests()
    }

    fun stopSendingTemperatureOrCo2Requests() {
        viewModel.stopSendingTemperatureOrCo2Requests()
    }

    fun stopPullingForTemperature() {
        viewModel.stopWaitForCooling()
        powerCommandsFactory.coolingDialog?.dismiss()
    }

    fun waitForCooling() {
        viewModel.waitForCooling()
    }

    fun initPowerAccordToItState() {
        val powerText: String?
        val drawableResource: Int
        when (powerCommandsFactory.currentPowerState()) {
            PowerState.OFF, PowerState.PRE_LOOPING -> {
                powerText = prefs.getString(
                    PrefConstants.POWER_OFF_NAME,
                    PrefConstants.POWER_OFF_NAME_DEFAULT
                )
                drawableResource = R.drawable.power_off_drawable
            }
            PowerState.ON -> {
                powerText = prefs.getString(
                    PrefConstants.POWER_ON_NAME,
                    PrefConstants.POWER_ON_NAME_DEFAULT
                )
                drawableResource = R.drawable.power_on_drawable
            }
            else -> {
                isPowerPressed = false
                binding.power.isEnabled = true
                return
            }
        }
        isPowerPressed = false
        binding.power.isEnabled = true
        if (powerText != null) {
            binding.power.text = powerText
            binding.power.post {
                binding.power.alpha = 1f
                binding.power.setBackgroundResource(drawableResource)
            }
        }
    }

    private fun changeTextsForButtons(contentView: View) {
        val addTextBuilder = StringBuilder()
        for (i in 0..8) {
            addTextBuilder.append(' ')
        }
        (contentView.findViewById<View>(R.id.txtOn) as TextView).text = "${addTextBuilder}Command 1: "
        (contentView.findViewById<View>(R.id.txtOn1) as TextView).text = "Button State1 Name: "
        (contentView.findViewById<View>(R.id.txtOff) as TextView).text = "${addTextBuilder}Command 2: "
        (contentView.findViewById<View>(R.id.txtOff1) as TextView).text = "Button State2 Name: "
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
            readCallback = OnDataReceivedCallback { sendMessageWithUsbDataReceived(it) }
        }
    }

    private fun loadPreferencesFromLocalData() {
        val settingsFolder =
            File(Environment.getExternalStorageDirectory(), AppData.SYSTEM_SETTINGS_FOLDER_NAME)
        val buttonPowerDataFile = File(settingsFolder, AppData.POWER_DATA)
        var powerData: String? = ""
        if (buttonPowerDataFile.exists()) {
            powerData = TextFileUtils.readTextFile(buttonPowerDataFile)
        }
        powerCommandsFactory = EToCApplication.getInstance().parseCommands(powerData)
        val commandFactory: String = if (powerCommandsFactory is FilePowerCommandsFactory) {
            "FilePowerCommand"
        } else {
            "DefaultPowerCommand"
        }
        Toast.makeText(this, commandFactory, Toast.LENGTH_LONG).show()
        if (!settingsFolder.exists()) {
            return
        }
        val button1DataFile = File(settingsFolder, AppData.BUTTON1_DATA)
        if (button1DataFile.exists()) {
            val button1Data = TextFileUtils.readTextFile(button1DataFile)
            if (!button1Data.isEmpty()) {
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
            if (!button2Data.isEmpty()) {
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
            if (!button3Data.isEmpty()) {
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
            if (!temperatureData.isEmpty()) {
                temperatureShift = try {
                    temperatureData.toInt()
                } catch (e: NumberFormatException) {
                    0
                }
            }
        } else {
            temperatureShift = 0
        }
        val measureDefaultFilesFile = File(settingsFolder, AppData.MEASURE_DEFAULT_FILES)
        if (measureDefaultFilesFile.exists()) {
            val measureFilesData = TextFileUtils.readTextFile(measureDefaultFilesFile)
            if (!measureFilesData.isEmpty()) {
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

    private fun observeChartUpdates() {
        viewModel.maxY.observe(this) {
            //TODO remove max, move all maxY changes to vm
            renderer.yAxisMax = maxOf(it.toDouble(), renderer.yAxisMax)
            chartView.repaint()
        }
        viewModel.chartPoints.observe(this) {
            chartSeries?.set(it)
            chartView.repaint()
        }
    }

    private fun observeEvents() {
        lifecycleScope.launchWhenResumed {
            for (event in viewModel.events) {
                when(event) {
                    is MainEvent.ShowToast -> showCustomisedToast(event.message)
                    is MainEvent.WriteToUsb -> sendCommand(event.data)
                    is MainEvent.InvokeAutoCalculations -> invokeAutoCalculations()
                    is MainEvent.UpdateTimerRunning -> isTimerRunning = event.isRunning
                    is MainEvent.IncReadingCount -> incReadingCount()
                }
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

    //TODO
    //make power on
    //"/5H0000R" "respond as ->" "@5,0(0,0,0,0),750,25,25,25,25"
    // 0.5 second wait -> repeat
    // "/5J5R" "respond as ->" "@5J4"
    // 1 second wait ->
    // "(FE............)" "respond as ->" "lala"
    // 2 second wait ->
    // "/1ZR" "respond as ->" "blasad" -> power on
    fun powerOn() {
        if (powerCommandsFactory.currentPowerState() == PowerState.OFF) {
            isPowerPressed = true
            binding.power.alpha = 0.6f
            powerCommandsFactory.moveStateToNext()
            powerCommandsFactory.sendRequest(this, handler)
        } else {
            throw IllegalStateException()
        }
    }

    private fun simulateClick1() {
        powerOff()
        handler.postDelayed({
            //TODO temperature out of range
            val temperatureData = "@5,0(0,0,0,0),25,750,25,25,25"
            simulateResponse(temperatureData)
        }, 3800)
        handler.postDelayed({
            //TODO temperature out of range
            val temperatureData = "@5,0(0,0,0,0),25,750,25,25,25"
            simulateResponse(temperatureData)
        }, 5000)
        handler.postDelayed({
            //TODO temperature in of range
            val temperatureData = "@5,0(0,0,0,0),25,74,25,25,25"
            simulateResponse(temperatureData)
        }, 20000)
        handler.postDelayed({
            simulateResponse(null)
        }, 24000)
    }

    private fun simulateResponse(response: String?) {
        var sVal = ""
        if (response != null) {
            sVal = response
        }

        if (isPowerPressed) {
            handleResponse(sVal)
        } else {
            val powerState = powerCommandsFactory.currentPowerState()
            if (powerState == PowerState.PRE_LOOPING) {
                EToCApplication.getInstance().isPreLooping = false
                stopPullingForTemperature()
                powerCommandsFactory.moveStateToNext()
            }
        }
    }

    private fun simulateClick2() {
        powerOn()
        handler.postDelayed({
            val temperatureData = "@5,0(0,0,0,0),750,25,25,25,25"
            simulateResponse(temperatureData)
        }, 800)
        handler.postDelayed({
            simulateResponse("@5J001 ")
        }, 1600)
        handler.postDelayed({
            simulateResponse("@5J101 ")
        }, 3000)
        handler.postDelayed({
            simulateResponse("255")
        }, 4800)
        handler.postDelayed({
            simulateResponse("1ZR")
        }, 5400)
    }

    //TODO
    //make power off
    //interrupt all activities by software (mean measure process etc)
    // 1 second wait ->
    // "/5H0000R" "respond as ->" "@5,0(0,0,0,0),750,25,25,25,25"
    // around 75C -> "/5J5R" -> "@5J5" -> then power off
    // bigger, then
    //You can do 1/2 second for the temperature and 1/2 second for the power and then co2
    fun powerOff() {
        if (powerCommandsFactory.currentPowerState() == PowerState.ON) {
            isPowerPressed = true
            binding.power.alpha = 0.6f
            if (binding.buttonOn2.tag.toString() != PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                binding.buttonOn2.performClick()
                handler.postDelayed({
                    powerCommandsFactory.moveStateToNext()
                    powerCommandsFactory.sendRequest(this, handler)
                }, 1200)
            } else {
                powerCommandsFactory.moveStateToNext()
                powerCommandsFactory.sendRequest(this, handler)
            }
        } else {
            throw IllegalStateException()
        }
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

    fun sendCommand(newCommand: String?) {
        var command = newCommand
        if (command != null && command != "" && command != "\n") {
            command = command.replace("\r", "")
            command = command.replace("\n", "")
            command = command.trim { it <= ' ' }
            val tempUsbDevice = usbDevice
            if (tempUsbDevice != null) {
                if (command.contains("(") && command.contains(")")) {
                    command = command.replace("(", "")
                    command = command.replace(")", "")
                    command = command.trim { it <= ' ' }
                    val arr = command.split("-").toTypedArray()
                    val bytes = ByteArray(arr.size)
                    for (j in bytes.indices) {
                        bytes[j] = arr[j].toInt(16).toByte()
                    }
                    tempUsbDevice.write(bytes)
                } else {
                    tempUsbDevice.write(command.toByteArray())
                    tempUsbDevice.write("\r".toByteArray())
                }
            }

            //if(Utils.isPullStateNone()) {
            Utils.appendText(binding.output, "Tx: $command")
            binding.scrollView.smoothScrollTo(0, 0)
        //}
        }
    }

    private fun sendMessage() {
        if (binding.editor.text.toString() != "") {
            val multiLines = binding.editor.text.toString()
            val commands: Array<String>
            val delimiter = "\n"
            commands = multiLines.split(delimiter).toTypedArray()
            for (i in commands.indices) {
                val command = commands[i]
                sendCommand(command)
            }
        } else {
            usbDevice?.write("\r".toByteArray())
            // binding.output.append("Tx: " + data + "\n");
            // binding.scrollView.smoothScrollTo(0, binding.output.getBottom());
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbDevice?.close()
        usbDevice = null
        usbDeviceConnection.close()
        handler.removeCallbacksAndMessages(null)
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
                    readChart(filePath)
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
                settingsActivity()
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
        val intent: Intent?
        val action: String?
        val file: File
        intent = getIntent()
        if (intent == null) {
            doDefaultAction()
            return
        }
        action = intent.action
        if (action == null) {
            doDefaultAction()
        } else if (action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) {
            try {
                file = File(URI(intent.data.toString()))
                doOpenFile(file, false)
            } catch (e: URISyntaxException) {
                Crouton.showText(this, R.string.toast_intent_invalid_uri, Style.ALERT)
            } catch (e: IllegalArgumentException) {
                Crouton.showText(this, R.string.toast_intent_illegal, Style.ALERT)
            }
        } else if (action == Constants.ACTION_WIDGET_OPEN) {
            try {
                file = File(URI(intent.data.toString()))
                doOpenFile(file, intent.getBooleanExtra(Constants.EXTRA_FORCE_READ_ONLY, false))
            } catch (e: URISyntaxException) {
                Crouton.showText(this, R.string.toast_intent_invalid_uri, Style.ALERT)
            } catch (e: IllegalArgumentException) {
                Crouton.showText(this, R.string.toast_intent_illegal, Style.ALERT)
            }
        } else {
            doDefaultAction()
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
        val content: String
        if (path == null) {
            Crouton.showText(this, R.string.toast_save_null, Style.ALERT)
            return
        }
        content = binding.editor.text.toString()
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
        if (text.length == 0) return
        if (TextFileUtils.writeInternal(this, text)) {
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

    /**
     * Starts an activity to choose a file to open
     */
    private fun openFile() {
        val open = Intent()
        open.setClass(applicationContext, TedOpenActivity::class.java)
        // open = new Intent(ACTION_OPEN);
        open.putExtra(Constants.EXTRA_REQUEST_CODE, Constants.REQUEST_OPEN)
        try {
            startActivityForResult(open, Constants.REQUEST_OPEN)
        } catch (e: ActivityNotFoundException) {
            Crouton.showText(this@MainActivity, R.string.toast_activity_open, Style.ALERT)
        }
    }

    private fun readChart(filePath: String) {
        val chartIndex = viewModel.readChart(filePath)
        chartSeries = if (chartIndex != CHART_INDEX_UNSELECTED) {
            graphSeriesDataset.getSeriesAt(chartIndex).clear()
            graphSeriesDataset.getSeriesAt(chartIndex)
        } else {
            null
        }
    }

    /**
     * Open the recent files activity to open
     */
    private fun openRecentFile() {
        if (RecentFiles.getRecentFiles().size == 0) {
            Crouton.showText(this, R.string.toast_no_recent_files, Style.ALERT)
            return
        }

        val open = Intent()
        open.setClass(this@MainActivity, TedOpenRecentActivity::class.java)
        try {
            startActivityForResult(open, Constants.REQUEST_OPEN)
        } catch (e: ActivityNotFoundException) {
            Crouton.showText(
                this@MainActivity, R.string.toast_activity_open_recent,
                Style.ALERT
            )
        }
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
        val saveAs = Intent()
        saveAs.setClass(this, TedSaveAsActivity::class.java)
        try {
            startActivityForResult(saveAs, Constants.REQUEST_SAVE_AS)
        } catch (e: ActivityNotFoundException) {
            Crouton.showText(this, R.string.toast_activity_save_as, Style.ALERT)
        }
    }

    /**
     * Opens the settings activity
     */
    private fun settingsActivity() {
        val settings = Intent()
        settings.setClass(this, TedSettingsActivity::class.java)
        try {
            startActivity(settings)
        } catch (e: ActivityNotFoundException) {
            Crouton.showText(this, R.string.toast_activity_settings, Style.ALERT)
        }
    }

    private fun setFilters() {
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(usbReceiver, filter)
    }

    private fun sendMessageWithUsbDataReceived(bytes: ByteArray) {
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
                val yMax: Int = renderer.yAxisMax.toInt()
                if (co2 >= yMax) {
                    renderer.yAxisMax = if (currentSeries.itemCount == 0) {
                        (3 * co2).toDouble()
                    } else {
                        (co2 + co2 * 15 / 100f).toDouble()
                    }
                }

                // auto
                val delay_v = prefs.getInt(PrefConstants.DELAY, 2)
                val duration_v = prefs.getInt(PrefConstants.DURATION, 3)
                val isAuto = prefs.getBoolean(PrefConstants.IS_AUTO, false)
                if (isAuto) {
                    if (readingCount == (duration_v * 60 / delay_v)) {
                        incCountMeasure()
                        viewModel.chartIdx = 2
                        setCurrentSeries(1)
                    } else if (readingCount == (duration_v * 60)) {
                        incCountMeasure()
                        viewModel.chartIdx = 3
                        setCurrentSeries(2)
                    }
                }
                val currentDate = Date()
                if (countMeasure != oldCountMeasure) {
                    viewModel.chartDate = FORMATTER.format(currentDate)
                    refreshOldCountMeasure()
                    chartIndexToDate[viewModel.chartIdx] = viewModel.chartDate
                }
                if (viewModel.subDirDate.isEmpty()) {
                    viewModel.subDirDate = FORMATTER.format(currentDate)
                }
                if (isTimerRunning) {
                    currentSeries.add(readingCount.toDouble(), co2.toDouble())
                    repaintChartView()
                    viewModel.cacheBytesFromUsb(bytes)
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

        if (isPowerPressed) {
            handleResponse(responseForChecking)
        } else {
            val powerState = powerCommandsFactory.currentPowerState()
            if (powerState == PowerState.PRE_LOOPING) {
                EToCApplication.getInstance().isPreLooping = false
                stopPullingForTemperature()
                powerCommandsFactory.moveStateToNext()
            }
        }
    }

    private fun handleResponse(response: String) {
        when (powerCommandsFactory.currentPowerState()) {
            PowerState.ON_STAGE1, PowerState.ON_STAGE1_REPEAT, PowerState.ON_STAGE3A, PowerState.ON_STAGE3B, PowerState.ON_STAGE2B, PowerState.ON_STAGE2, PowerState.ON_STAGE3, PowerState.ON_STAGE4, PowerState.ON_RUNNING -> {
                val currentCommand = powerCommandsFactory.currentCommand()
                powerCommandsFactory.moveStateToNext()
                if (currentCommand?.hasSelectableResponses() == true) {
                    if (currentCommand.isResponseCorrect(response)) {
                        if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
                            handler.postDelayed({
                                if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
                                    powerCommandsFactory.sendRequest(this, handler)
                                }
                            }, currentCommand.delay)
                        }
                    } else {
                        val responseBuilder = java.lang.StringBuilder()
                        for (possibleResponse in currentCommand.possibleResponses) {
                            responseBuilder.append("\"$possibleResponse\" or ")
                        }
                        responseBuilder.delete(
                            responseBuilder.length - 4, responseBuilder
                                .length
                        )
                        showCustomisedToast("Wrong response: Got - \"${response}\".Expected - $responseBuilder")
                        return
                    }
                } else if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
                    powerCommandsFactory.sendRequest(this, handler)
                }
                if (powerCommandsFactory.currentPowerState() == PowerState.ON) {
                    initPowerAccordToItState()
                    startSendingTemperatureOrCo2Requests()
                }
            }
            PowerState.OFF_INTERRUPTING -> {
                stopSendingTemperatureOrCo2Requests()
                powerCommandsFactory.moveStateToNext()
                val delayForPausing = powerCommandsFactory.currentCommand()!!.delay
                handler.postDelayed( {
                    if (powerCommandsFactory.currentPowerState() != PowerState.OFF) {
                        powerCommandsFactory.sendRequest(this, handler)
                    }
                }, delayForPausing * 2)
            }
            //we can get here only from local power factory
            PowerState.OFF_STAGE1 -> {
                val temperatureData = TemperatureData.parse(response)
                if (temperatureData.isCorrect) {
                    val curTemperature = temperatureData.temperature1
                    if (curTemperature <= EToCApplication.getInstance().borderCoolingTemperature) {
                        powerCommandsFactory.moveStateToNext()
                    }
                    powerCommandsFactory.moveStateToNext()
                    handler.postDelayed({
                        if (powerCommandsFactory.currentPowerState() != PowerState.OFF) {
                            powerCommandsFactory.sendRequest(this, handler)
                        }
                    }, powerCommandsFactory.currentCommand()!!.delay)
                }
            }
            PowerState.OFF_WAIT_FOR_COOLING -> {
                val temperatureData = TemperatureData.parse(response)
                if (temperatureData.isCorrect) {
                    val curTemperature: Int = temperatureData.temperature1
                    if (curTemperature <= EToCApplication.getInstance().borderCoolingTemperature) {
                        stopPullingForTemperature()
                        powerCommandsFactory.moveStateToNext()
                        handler.postDelayed({
                            if (powerCommandsFactory.currentPowerState() != PowerState.OFF) {
                                powerCommandsFactory.sendRequest(this, handler)
                            }
                        }, powerCommandsFactory.currentCommand()!!.delay)
                    }
                }
            }
            PowerState.OFF_RUNNING, PowerState.OFF_FINISHING -> {
                powerCommandsFactory.moveStateToNext()
                if (powerCommandsFactory.currentPowerState() == PowerState.OFF) {
                    initPowerAccordToItState()
                    return
                }
                val currentCommand = powerCommandsFactory.currentCommand()
                if (currentCommand?.hasSelectableResponses() == true) {
                    if (currentCommand.isResponseCorrect(response)) {
                        handler.postDelayed({
                            if (powerCommandsFactory.currentPowerState() != PowerState.OFF) {
                                powerCommandsFactory.sendRequest(this, handler)
                            }
                        }, currentCommand.delay)
                    } else {
                        val responseBuilder = java.lang.StringBuilder()
                        for (possibleResponse in currentCommand.possibleResponses) {
                            responseBuilder.append("\"$possibleResponse\" or ")
                        }
                        responseBuilder.delete(
                            responseBuilder.length - 4, responseBuilder
                                .length
                        )
                        showCustomisedToast("Wrong response: Got - \"response\".Expected - $responseBuilder")
                        return
                    }
                }
            }
            else -> {
                //do nothing
            }
        }
    }

    fun setCurrentSeries(index: Int) {
        currentSeries = graphSeriesDataset.getSeriesAt(index)
    }

    fun incCountMeasure() {
        countMeasure++
    }

    fun repaintChartView() {
        chartView.repaint()
    }

    fun refreshOldCountMeasure() {
        oldCountMeasure = countMeasure
    }

    fun setUsbConnected(isUsbConnected: Boolean) {
        this.isUsbConnected = isUsbConnected
        invalidateOptionsMenu()
    }

    private fun changeBackground(button: View) {
        button.setBackgroundResource(R.drawable.temperature_button_drawable_unpressed)
    }

    fun refreshTextAccordToSensor(isTemperature: Boolean, text: String?) {
        if (isTemperature) {
            TemperatureData.parse(text).also { temperatureData ->
                if (temperatureData.isCorrect) {
                    val updateRunnable = Runnable {
                        binding.temperature.text = (temperatureData.temperature1 + temperatureShift).toString()
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

    fun incReadingCount() {
        readingCount++
    }

    fun invokeAutoCalculations() {
        supportFragmentManager.findFragmentById(R.id.bottom_fragment)!!.view!!.findViewById<View>(R.id.calculate_ppm_auto)
            .performClick()
    }

    fun clearData() {
        viewModel.subDirDate = ""
        for (series in graphSeriesDataset.series) {
            series.clear()
        }
        oldCountMeasure = 0
        countMeasure = oldCountMeasure
        chartIndexToDate.clear()
        GraphPopulatorUtils.clearYTextLabels(renderer)
        repaintChartView()
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

    fun deliverCommand(command: String) {
        sendCommand(command)
    }

    interface AutoPullResolverCallback {
        fun onPrePullStopped()
        fun onPostPullStopped()
        fun onPostPullStarted()
    }

    private inner class AutoPullResolverListener(
        private val mAutoPullResolverCallback: AutoPullResolverCallback
    ) : View.OnClickListener {
        override fun onClick(v: View) {
            if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
                return
            }
            mAutoPullResolverCallback.onPrePullStopped()
            val nowTime = SystemClock.uptimeMillis()
            val timeElapsed = Utils.elapsedTimeForSendRequest(nowTime, lastTimePressed)
            if (timeElapsed) {
                lastTimePressed = nowTime
                viewModel.stopSendingTemperatureOrCo2Requests()
            }
            mAutoPullResolverCallback.onPostPullStopped()
            if (timeElapsed) {
                handler.postDelayed({
                    if (powerCommandsFactory.currentPowerState() == PowerState.ON) {
                        viewModel.startSendingTemperatureOrCo2Requests()
                    }
                    mAutoPullResolverCallback.onPostPullStarted()
                }, 1000)
            }
        }
    }

    companion object {
        private val FORMATTER = SimpleDateFormat("MM.dd.yyyy HH:mm:ss")
        const val MESSAGE_INTERRUPT_ACTIONS = 123
        private const val TAG = "MainActivity"
    }
}