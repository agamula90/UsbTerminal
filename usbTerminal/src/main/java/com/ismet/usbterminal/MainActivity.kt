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
import com.ismet.usbterminal.data.*
import com.ismet.usbterminal.mainscreen.EToCMainHandler
import com.ismet.usbterminal.mainscreen.powercommands.CommandsDeliverer
import com.ismet.usbterminal.mainscreen.powercommands.FilePowerCommandsFactory
import com.ismet.usbterminal.mainscreen.powercommands.PowerCommandsFactory
import com.ismet.usbterminal.mainscreen.tasks.SendDataToUsbTask
import com.ismet.usbterminal.services.PullStateManagingService
import com.ismet.usbterminal.utils.AlertDialogTwoButtonsCreator
import com.ismet.usbterminal.utils.AlertDialogTwoButtonsCreator.OnInitLayoutListener
import com.ismet.usbterminal.utils.GraphPopulatorUtils
import com.ismet.usbterminal.utils.Utils
import com.ismet.usbterminalnew.BuildConfig
import com.ismet.usbterminalnew.R
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
import fr.xgouchet.texteditor.ui.view.AdvancedEditText
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class MainActivity : BaseAttachableActivity(), TextWatcher, CommandsDeliverer {
    /*
	 * Notifications from UsbService will be received here.
	 */
    private val mUsbReceiver: BroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                EToCMainHandler.USB_DATA_READY -> {
                    val isToast = intent.getBooleanExtra(EToCMainHandler.IS_TOAST, false)
                    val data = intent.getStringExtra(EToCMainHandler.DATA_EXTRA)
                    if (isToast) {
                        sendMessageForToast(data)
                    } else {
                        sendMessageWithUsbDataReady(data)
                    }
                }
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
                    //TODO handle event
                }
            }
        }
    }

    /**
     * the path of the file currently opened
     */
    private var mCurrentFilePath: String? = null

    /**
     * the name of the file currently opened
     */
    private var mCurrentFileName: String? = null

    /**
     * is dirty ?
     */
    private var mDirty = false

    /**
     * is read only
     */
    private var mReadOnly = false

    /**
     * Undo watcher
     */
    private var mWatcher: TextChangeWatcher? = null
    private var mInUndo = false
    private var mWarnedShouldQuit = false

    /**
     * are we in a post activity result ?
     */
    private var mReadIntent = false

    /**
     * the text editor
     */
    private lateinit var mAdvancedEditText: AdvancedEditText
    private lateinit var mHandler: Handler
    private var mIsUsbConnected = false

    var countMeasure = 0
        private set
    var oldCountMeasure = 0
        private set
    var isTimerRunning = false
    var readingCount = 0
        private set

    var chartIdx = 0
    var chartDate = ""
    var subDirDate: String? = null
    private val mMapChartIndexToDate: MutableMap<Int, String> = HashMap()
    lateinit var prefs: SharedPreferences
    lateinit var currentSeries: XYSeries
    lateinit var chartView: GraphicalView
    private lateinit var mGraphSeriesDataset: XYMultipleSeriesDataset
    lateinit var renderer: XYMultipleSeriesRenderer
    var chartSeries: XYSeries? = null
        private set
    private lateinit var usbDeviceConnection: UsbDeviceConnection
    private var usbDevice: UsbDevice? = null
    private val viewModel: MainViewModel by viewModels()

    private lateinit var mExecutor: ExecutorService
    private var mSendDataToUsbTask: SendDataToUsbTask? = null
    lateinit var txtOutput: TextView
    lateinit var scrollView: ScrollView
    private lateinit var mButtonOn1: Button
    private lateinit var mButtonOn2: Button
    private lateinit var mButtonOn3: Button
    private lateinit var mSendButton: Button
    private lateinit var mButtonClear: Button
    private lateinit var mButtonMeasure: Button
    private lateinit var mPower: Button
    private lateinit var mTemperature: TextView
    private lateinit var mCo2: TextView
    private lateinit var mTemperatureBackground: View
    private lateinit var mCo2Background: View
    private lateinit var mExportLayout: LinearLayout
    private lateinit var mMarginLayout: LinearLayout
    private var mTemperatureShift = 0
    private var mLastTimePressed: Long = 0
    private var mReportDate: Date? = null
    var isPowerPressed = false
        private set
    lateinit var powerCommandsFactory: PowerCommandsFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mExecutor = Executors.newSingleThreadExecutor()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        Settings.updateFromPreferences(
            getSharedPreferences(
                Constants.PREFERENCES_NAME,
                MODE_PRIVATE
            )
        )
        loadPreferencesFromLocalData()
        observeChartUpdates()
        mHandler = EToCMainHandler(this)

        mReadIntent = true
        mExportLayout = exportLayout
        mMarginLayout = findViewById<View>(R.id.margin_layout) as LinearLayout
        mAdvancedEditText = findViewById<View>(R.id.editor) as AdvancedEditText
        mAdvancedEditText.addTextChangedListener(this)
        mAdvancedEditText.updateFromSettings()
        mWatcher = TextChangeWatcher()
        mWarnedShouldQuit = false
        txtOutput = findViewById<View>(R.id.output) as TextView
        scrollView = findViewById<View>(R.id.mScrollView) as ScrollView
        mPower = findViewById<View>(R.id.power) as Button
        initPowerAccordToItState()

        mPower.setOnClickListener { v ->
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
            }
        }

        mTemperature = findViewById<View>(R.id.temperature) as TextView
        mTemperatureBackground = findViewById(R.id.temperature_background)
        changeBackground(mTemperatureBackground, false)
        mCo2 = findViewById<View>(R.id.co2) as TextView
        mCo2Background = findViewById(R.id.co2_background)
        changeBackground(mCo2Background, false)
        mButtonOn1 = findViewById<View>(R.id.buttonOn1) as Button
        mButtonOn1.text = prefs.getString(PrefConstants.ON_NAME1, PrefConstants.ON_NAME_DEFAULT)
        mButtonOn1.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
        mButtonOn1.setOnClickListener(AutoPullResolverListener(object : AutoPullResolverCallback {
            private var command: String? = null
            override fun onPrePullStopped() {
                val str_on_name1t =
                    prefs.getString(PrefConstants.ON_NAME1, PrefConstants.ON_NAME_DEFAULT)
                val str_off_name1t =
                    prefs.getString(PrefConstants.OFF_NAME1, PrefConstants.OFF_NAME_DEFAULT)
                val s = mButtonOn1.tag.toString()
                command = "" //"/5H1000R";
                if (s == PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                    command = prefs.getString(PrefConstants.ON1, "")
                    mButtonOn1.text = str_off_name1t
                    mButtonOn1.tag = PrefConstants.OFF_NAME_DEFAULT.lowercase(Locale.getDefault())
                } else {
                    command = prefs.getString(PrefConstants.OFF1, "")
                    mButtonOn1.text = str_on_name1t
                    mButtonOn1.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
                }
            }

            override fun onPostPullStopped() {
                sendCommand(command)
            }

            override fun onPostPullStarted() {}
        }))
        mButtonOn1.setOnLongClickListener(object : OnLongClickListener {
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
                        val s = mButtonOn1.tag.toString()
                        if (s == "on") {
                            mButtonOn1.text = strOn1
                        } else {
                            mButtonOn1.text = strOff1
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
        mButtonOn2 = findViewById<View>(R.id.buttonOn2) as Button
        mButtonOn2.text = prefs.getString(PrefConstants.ON_NAME2, PrefConstants.ON_NAME_DEFAULT)
        mButtonOn2.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
        EToCApplication.getInstance().currentTemperatureRequest =
            prefs.getString(PrefConstants.ON2, "/5H750R")
        mButtonOn2.setOnClickListener(AutoPullResolverListener(object : AutoPullResolverCallback {
            private var command: String? = null
            override fun onPrePullStopped() {
                val str_on_name2t =
                    prefs.getString(PrefConstants.ON_NAME2, PrefConstants.ON_NAME_DEFAULT)
                val str_off_name2t =
                    prefs.getString(PrefConstants.OFF_NAME2, PrefConstants.OFF_NAME_DEFAULT)
                val s = mButtonOn2.tag.toString()
                command = "" //"/5H1000R";
                val defaultValue: String
                val prefName: String
                if (s == PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                    prefName = PrefConstants.OFF2
                    defaultValue = "/5H0000R"
                    command = prefs.getString(PrefConstants.ON2, "")
                    mButtonOn2.text = str_off_name2t
                    mButtonOn2.tag = PrefConstants.OFF_NAME_DEFAULT.lowercase(Locale.getDefault())
                } else {
                    prefName = PrefConstants.ON2
                    defaultValue = "/5H750R"
                    command = prefs.getString(PrefConstants.OFF2, "")
                    mButtonOn2.text = str_on_name2t
                    mButtonOn2.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
                    mButtonOn2.alpha = 0.6f
                }
                EToCApplication.getInstance().currentTemperatureRequest =
                    prefs.getString(prefName, defaultValue)
            }

            override fun onPostPullStopped() {
                sendCommand(command)
            }

            override fun onPostPullStarted() {
                val tag = mButtonOn2.tag.toString()
                mButtonOn2.post {
                    if (tag == PrefConstants.ON_NAME_DEFAULT.lowercase(
                            Locale.getDefault()
                        )
                    ) {
                        mButtonOn2.alpha = 1f
                        mButtonOn2.setBackgroundResource(R.drawable.button_drawable)
                    } else {
                        mButtonOn2.alpha = 1f
                        mButtonOn2.setBackgroundResource(R.drawable.power_on_drawable)
                    }
                }
            }
        }))
        mButtonOn2.setOnLongClickListener(object : OnLongClickListener {
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
                        val s = mButtonOn2.tag.toString()
                        val defaultValue: String
                        val prefName: String
                        if (s == PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                            prefName = PrefConstants.ON2
                            defaultValue = "/5H750R"
                            mButtonOn2.text = strOn1
                        } else {
                            prefName = PrefConstants.OFF2
                            defaultValue = "/5H0000R"
                            mButtonOn2.text = strOff1
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
        mButtonOn3 = findViewById<View>(R.id.buttonPpm) as Button
        mButtonOn3.text = prefs.getString(PrefConstants.ON_NAME3, PrefConstants.ON_NAME_DEFAULT)
        mButtonOn3.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
        mButtonOn3.setOnClickListener(AutoPullResolverListener(object : AutoPullResolverCallback {
            private var command: String? = null
            override fun onPrePullStopped() {
                val str_on_name3 =
                    prefs.getString(PrefConstants.ON_NAME3, PrefConstants.ON_NAME_DEFAULT)
                val str_off_name3 =
                    prefs.getString(PrefConstants.OFF_NAME3, PrefConstants.OFF_NAME_DEFAULT)
                val s = mButtonOn3.tag.toString()
                if (s == PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                    mButtonOn3.text = str_off_name3
                    mButtonOn3.tag = PrefConstants.OFF_NAME_DEFAULT.lowercase(Locale.getDefault())
                    command = prefs.getString(PrefConstants.ON3, "")
                } else {
                    mButtonOn3.text = str_on_name3
                    mButtonOn3.tag = PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())
                    command = prefs.getString(PrefConstants.OFF3, "")
                }
            }

            override fun onPostPullStopped() {
                sendCommand(command)
            }

            override fun onPostPullStarted() {}
        }))
        mButtonOn3.setOnLongClickListener(object : OnLongClickListener {
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
                        val s = mButtonOn3.tag.toString()
                        if (s == PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                            mButtonOn3.text = strOn1
                        } else {
                            mButtonOn3.text = strOff1
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
        mSendButton = findViewById<View>(R.id.buttonSend) as Button
        mSendButton.setOnClickListener(AutoPullResolverListener(object :
            AutoPullResolverCallback {
            override fun onPrePullStopped() {
            }

            override fun onPostPullStopped() {
                sendMessage()
            }

            override fun onPostPullStarted() {}
        }))
        mAdvancedEditText.setOnEditorActionListener { _, actionId, _ ->
            var handled = false
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                // sendMessage();
                handled = true
            }
            handled
        }
        mButtonClear = findViewById<View>(R.id.buttonClear) as Button
        mButtonMeasure = findViewById<View>(R.id.buttonMeasure) as Button
        mButtonClear.setOnClickListener(object : View.OnClickListener {
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
                alert.setMultiChoiceItems(items, checkedItems) { dialog, which, isChecked ->
                    mItemsChecked.put(which, isChecked)
                }
                alert.setPositiveButton("Select/Clear") { dialog, which ->
                    if (mItemsChecked[1]) {
                        mAdvancedEditText.setText("")
                    }
                    if (mItemsChecked[2]) {
                        txtOutput.text = ""
                    }
                    var isCleared = false
                    if (mItemsChecked[3]) {
                        mGraphSeriesDataset.getSeriesAt(0).clear()
                        isCleared = true
                        if (mMapChartIndexToDate.containsKey(1)) {
                            Utils.deleteFiles(mMapChartIndexToDate[1], "_R1")
                        }
                    }
                    if (mItemsChecked[4]) {
                        mGraphSeriesDataset.getSeriesAt(1).clear()
                        isCleared = true
                        if (mMapChartIndexToDate.containsKey(2)) {
                            Utils.deleteFiles(mMapChartIndexToDate[2], "_R2")
                        }
                    }
                    if (mItemsChecked[5]) {
                        mGraphSeriesDataset.getSeriesAt(2).clear()
                        isCleared = true
                        if (mMapChartIndexToDate.containsKey(3)) {
                            Utils.deleteFiles(mMapChartIndexToDate[3], "_R3")
                        }
                    }
                    if (isCleared) {
                        var existInitedGraphCurve = false
                        for (i in 0 until mGraphSeriesDataset.seriesCount) {
                            if (mGraphSeriesDataset.getSeriesAt(i).itemCount != 0) {
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
                ) { dialog, which -> dialog.cancel() }
                alert.create().show()
            }
        })
        mButtonMeasure.setOnClickListener(object : View.OnClickListener {
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
                val arrSeries = mGraphSeriesDataset.series
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
                        chkKnownPpm.setOnCheckedChangeListener { buttonView, isChecked ->
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
                                    mGraphSeriesDataset = graphData.seriesDataset
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
                                if (mGraphSeriesDataset.getSeriesAt(0).itemCount == 0) {
                                    chartIdx = 1
                                    readingCount = 0
                                    currentSeries = mGraphSeriesDataset.getSeriesAt(0)
                                } else if (mGraphSeriesDataset.getSeriesAt(1).itemCount == 0) {
                                    chartIdx = 2
                                    readingCount = duration * 60 / delay
                                    currentSeries = mGraphSeriesDataset.getSeriesAt(1)
                                } else if (mGraphSeriesDataset.getSeriesAt(2).itemCount == 0) {
                                    chartIdx = 3
                                    readingCount = duration * 60
                                    currentSeries = mGraphSeriesDataset.getSeriesAt(2)
                                }
                                val checkedId = mRadioGroup.checkedRadioButtonId
                                if (mAdvancedEditText.text.toString() == "" && checkedId ==
                                    -1
                                ) {
                                    showCustomisedToast( "Please enter command")
                                    return
                                }
                                val contentForUpload: String?
                                val success: Boolean
                                if (checkedId != -1) {
                                    if (mRadio1.id == checkedId) {
                                        contentForUpload = TextFileUtils.readTextFile(
                                            File(
                                                File(
                                                    Environment.getExternalStorageDirectory(),
                                                    AppData.SYSTEM_SETTINGS_FOLDER_NAME
                                                ),
                                                commandsEditText1.text.toString()
                                            )
                                        )
                                        success = true
                                    } else if (mRadio2.id == checkedId) {
                                        contentForUpload = TextFileUtils.readTextFile(
                                            File(
                                                File(
                                                    Environment.getExternalStorageDirectory(),
                                                    AppData.SYSTEM_SETTINGS_FOLDER_NAME
                                                ),
                                                commandsEditText2.text.toString()
                                            )
                                        )
                                        success = true
                                    } else if (mRadio3.id == checkedId) {
                                        contentForUpload = TextFileUtils.readTextFile(
                                            File(
                                                File(
                                                    Environment.getExternalStorageDirectory(),
                                                    AppData.SYSTEM_SETTINGS_FOLDER_NAME
                                                ),
                                                commandsEditText3.text.toString()
                                            )
                                        )
                                        success = true
                                    } else {
                                        contentForUpload = null
                                        success = false
                                    }
                                } else {
                                    contentForUpload = mAdvancedEditText.text.toString()
                                    success = true
                                }
                                val editor = prefs.edit()
                                editor.putString(
                                    PrefConstants.MEASURE_FILE_NAME1, commandsEditText1
                                        .getText().toString()
                                )
                                editor.putString(
                                    PrefConstants.MEASURE_FILE_NAME2, commandsEditText2
                                        .getText().toString()
                                )
                                editor.putString(
                                    PrefConstants.MEASURE_FILE_NAME3, commandsEditText3
                                        .getText().toString()
                                )
                                editor.apply()

                                if (contentForUpload != null && !contentForUpload.isEmpty()) {
                                    startService(
                                        PullStateManagingService.intentForService(
                                            this@MainActivity,
                                            false
                                        )
                                    )
                                    val multiLines: String = contentForUpload
                                    val commands: Array<String>
                                    val delimiter = "\n"
                                    commands = multiLines.split(delimiter).toTypedArray()
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
                                    val autoPpmCalculate = autoPpm
                                    mHandler.postDelayed({
                                        if (mSendDataToUsbTask != null && mSendDataToUsbTask!!
                                                .status == AsyncTask.Status.RUNNING
                                        ) {
                                            mSendDataToUsbTask!!.cancel(true)
                                        }
                                        mSendDataToUsbTask = SendDataToUsbTask(
                                            simpleCommands,
                                            loopCommands,
                                            autoPpmCalculate,
                                            this@MainActivity,
                                            chkUseRecentDirectory.isChecked
                                        )
                                        mSendDataToUsbTask!!.execute(future, delay_timer)
                                    }, 300)
                                } else if (success) {
                                    showCustomisedToast("File not found")
                                    return
                                } else {
                                    showCustomisedToast("Unexpected error")
                                    return
                                }
                            }
                            dialog.cancel()
                        }
                    }
                val cancelListener =
                    DialogInterface.OnClickListener { dialog, which ->
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
            val i = PullStateManagingService.intentForService(this, true)
            i.action = PullStateManagingService.WAIT_FOR_COOLING_ACTION
            startService(i)

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
        mGraphSeriesDataset = graphData.seriesDataset
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

    fun showToastMessage(message: String?) {
        // showCustomisedToast(message.toString())
    }

    fun initPowerAccordToItState() {
        val powerText: String?
        val powerTag: String?
        val drawableResource: Int
        when (powerCommandsFactory.currentPowerState()) {
            PowerState.OFF, PowerState.PRE_LOOPING -> {
                powerText = prefs.getString(
                    PrefConstants.POWER_OFF_NAME,
                    PrefConstants.POWER_OFF_NAME_DEFAULT
                )
                powerTag = PrefConstants.POWER_OFF_NAME_DEFAULT.lowercase(Locale.getDefault())
                drawableResource = R.drawable.power_off_drawable
            }
            PowerState.ON -> {
                powerText = prefs.getString(
                    PrefConstants.POWER_ON_NAME,
                    PrefConstants.POWER_ON_NAME_DEFAULT
                )
                powerTag = PrefConstants.POWER_ON_NAME_DEFAULT.lowercase(Locale.getDefault())
                drawableResource = R.drawable.power_on_drawable
            }
            else -> {
                run {
                    powerTag = null
                    powerText = powerTag
                }
                drawableResource = 0
            }
        }
        isPowerPressed = false
        mPower.isEnabled = true
        if (powerTag != null && powerText != null) {
            mPower.text = powerText
            mPower.tag = powerTag
            mPower.post {
                mPower.alpha = 1f
                mPower.setBackgroundResource(drawableResource)
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
                mTemperatureShift = try {
                    temperatureData.toInt()
                } catch (e: NumberFormatException) {
                    0
                }
            }
        } else {
            mTemperatureShift = 0
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
            mPower.alpha = 0.6f
            powerCommandsFactory.moveStateToNext()
            powerCommandsFactory.sendRequest(this, mHandler, this)
        } else {
            throw IllegalStateException()
        }
    }

    private fun simulateClick1() {
        powerOff()
        var temperatureData = "@5,0(0,0,0,0),25,750,25,25,25"
        var message = mHandler.obtainMessage(EToCMainHandler.MESSAGE_SIMULATE_RESPONSE)
        message.obj = temperatureData
        //TODO temperature out of range
        mHandler.sendMessageDelayed(message, 3800)
        message = mHandler.obtainMessage(EToCMainHandler.MESSAGE_SIMULATE_RESPONSE)
        message.obj = temperatureData
        //TODO temperature out of range
        mHandler.sendMessageDelayed(message, 5000)
        message = mHandler.obtainMessage(EToCMainHandler.MESSAGE_SIMULATE_RESPONSE)
        temperatureData = "@5,0(0,0,0,0),25,74,25,25,25"
        message.obj = temperatureData
        //TODO temperature in of range
        mHandler.sendMessageDelayed(message, 20000)
        message = mHandler.obtainMessage(EToCMainHandler.MESSAGE_SIMULATE_RESPONSE)
        mHandler.sendMessageDelayed(message, 24000)
    }

    private fun simulateClick2() {
        powerOn()
        val temperatureData = "@5,0(0,0,0,0),750,25,25,25,25"
        var message = mHandler.obtainMessage(EToCMainHandler.MESSAGE_SIMULATE_RESPONSE)
        mHandler.sendMessageDelayed(message, 800)
        message = mHandler.obtainMessage(EToCMainHandler.MESSAGE_SIMULATE_RESPONSE)
        message.obj = "@5J001 "
        mHandler.sendMessageDelayed(message, 1600)
        message = mHandler.obtainMessage(EToCMainHandler.MESSAGE_SIMULATE_RESPONSE)
        message.obj = "@5J101 "
        mHandler.sendMessageDelayed(message, 3000)
        message = mHandler.obtainMessage(EToCMainHandler.MESSAGE_SIMULATE_RESPONSE)
        message.obj = "255"
        mHandler.sendMessageDelayed(message, 4800)
        message = mHandler.obtainMessage(EToCMainHandler.MESSAGE_SIMULATE_RESPONSE)
        message.obj = "/1ZR"
        mHandler.sendMessageDelayed(message, 5400)
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
            mPower.alpha = 0.6f
            if (mButtonOn2.tag.toString() != PrefConstants.ON_NAME_DEFAULT.lowercase(Locale.getDefault())) {
                mButtonOn2.performClick()
                mHandler.postDelayed({
                    powerCommandsFactory.moveStateToNext()
                    powerCommandsFactory.sendRequest(
                        this@MainActivity, mHandler,
                        this@MainActivity
                    )
                }, 1200)
            } else {
                powerCommandsFactory.moveStateToNext()
                powerCommandsFactory.sendRequest(this, mHandler, this)
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
        mMarginLayout.setBackgroundColor(Color.BLACK)
        mExportLayout.setBackgroundColor(Color.WHITE)
    }

    override fun onGraphDetached() {
        mMarginLayout.setBackgroundColor(Color.TRANSPARENT)
        mExportLayout.setBackgroundColor(Color.TRANSPARENT)
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
            Utils.appendText(txtOutput, "Tx: $command")
            scrollView.smoothScrollTo(0, 0)
        //}
        }
    }

    private fun sendMessage() {
        if (mAdvancedEditText.text.toString() != "") {
            val multiLines = mAdvancedEditText.text.toString()
            val commands: Array<String>
            val delimiter = "\n"
            commands = multiLines.split(delimiter).toTypedArray()
            for (i in commands.indices) {
                val command = commands[i]
                sendCommand(command)
            }
        } else {
            usbDevice?.write("\r".toByteArray())
            // mTxtOutput.append("Tx: " + data + "\n");
            // mScrollView.smoothScrollTo(0, mTxtOutput.getBottom());
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mUsbReceiver)
        mExecutor.shutdown()
        while (!mExecutor.isTerminated) { }
        if (mSendDataToUsbTask != null && mSendDataToUsbTask!!.status == AsyncTask.Status.RUNNING) {
            mSendDataToUsbTask!!.cancel(true)
            mSendDataToUsbTask = null
        }
        usbDevice?.close()
        usbDevice = null
        usbDeviceConnection.close()
        mHandler.removeCallbacksAndMessages(null)
        stopService(Intent(this, PullStateManagingService::class.java))
        prefs.edit().putBoolean(IS_SERVICE_RUNNING, false).apply()
    }

    override fun onRestart() {
        super.onRestart()
        mReadIntent = false
    }

    override fun onResume() {
        super.onResume()
        if (mReadIntent) {
            readIntent()
        }
        mReadIntent = false
        mAdvancedEditText.updateFromSettings()
        val isServiceRunning = prefs.getBoolean(IS_SERVICE_RUNNING, false)
        if (!isServiceRunning) {
            EToCApplication.getInstance().pullState = PullState.NONE
            //startService(PullStateManagingService.intentForService(this, true));
        }
    }

    override fun onPause() {
        super.onPause()
        if (Settings.FORCE_AUTO_SAVE && mDirty && !mReadOnly) {
            if (mCurrentFilePath == null || mCurrentFilePath!!.isEmpty()) doAutoSaveFile() else if (Settings.AUTO_SAVE_OVERWRITE) doSaveFile(
                mCurrentFilePath
            )
        }
        prefs.edit().putBoolean(IS_SERVICE_RUNNING, true).apply()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        mReadIntent = false
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
                    openChart(extras.getString("path")!!)
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
        if (mIsUsbConnected) {
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
        if (!mReadOnly) wrapMenuItem(
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
        if (mIsUsbConnected) {
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
        mWarnedShouldQuit = false
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
        if (Settings.UNDO && !mInUndo && mWatcher != null) mWatcher!!.beforeChange(
            s,
            start,
            count,
            after
        )
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        if (mInUndo) return
        if (Settings.UNDO && !mInUndo && mWatcher != null) mWatcher!!.afterChange(
            s,
            start,
            before,
            count
        )
    }

    override fun afterTextChanged(s: Editable) {
        if (!mDirty) {
            mDirty = true
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
                    mWarnedShouldQuit = false
                    return super.onKeyUp(keyCode, event)
                }
                return true
            }
        }
        mWarnedShouldQuit = false
        return super.onKeyUp(keyCode, event)
    }

    private fun shouldQuit(): Boolean {
        val entriesCount = supportFragmentManager.backStackEntryCount
        return entriesCount == 0 && mExportLayout.childCount == 0
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
        mWatcher = null
        mInUndo = true
        mAdvancedEditText.setText("")
        mCurrentFilePath = null
        mCurrentFileName = null
        Settings.END_OF_LINE = Settings.DEFAULT_END_OF_LINE
        mDirty = false
        mReadOnly = false
        mWarnedShouldQuit = false
        mWatcher = TextChangeWatcher()
        mInUndo = false
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
                mInUndo = true
                if (mAdvancedEditText.text.toString() == "") {
                    mAdvancedEditText.append(text)
                } else {
                    mAdvancedEditText.append(
                        """
                            
                            $text
                            """.trimIndent()
                    )
                }
                mWatcher = TextChangeWatcher()
                mCurrentFilePath = FileUtils.getCanonizePath(file)
                mCurrentFileName = file.name
                RecentFiles.updateRecentList(mCurrentFilePath)
                RecentFiles.saveRecentList(
                    getSharedPreferences(
                        Constants.PREFERENCES_NAME,
                        MODE_PRIVATE
                    )
                )
                mDirty = false
                mInUndo = false
                if (file.canWrite() && !forceReadOnly) {
                    mReadOnly = false
                    mAdvancedEditText.isEnabled = true
                } else {
                    mReadOnly = true
                    mAdvancedEditText.isEnabled = false
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
                mInUndo = true
                mAdvancedEditText.setText(text)
                mWatcher = TextChangeWatcher()
                mCurrentFilePath = null
                mCurrentFileName = null
                mDirty = false
                mInUndo = false
                mReadOnly = false
                mAdvancedEditText.isEnabled = true
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
        content = mAdvancedEditText.text.toString()
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
        mCurrentFilePath = FileUtils.getCanonizePath(File(path))
        mCurrentFileName = File(path).name
        RecentFiles.updateRecentList(path)
        RecentFiles.saveRecentList(getSharedPreferences(Constants.PREFERENCES_NAME, MODE_PRIVATE))
        mReadOnly = false
        mDirty = false
        Crouton.showText(this, R.string.toast_save_success, Style.CONFIRM)
    }

    private fun doAutoSaveFile() {
        val text = mAdvancedEditText.text.toString()
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
        mInUndo = true
        val caret = mWatcher!!.undo(mAdvancedEditText.text)
        if (caret >= 0) {
            mAdvancedEditText.setSelection(caret, caret)
            didUndo = true
        }
        mInUndo = false
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

    private fun openChart(filePath: String) {
        chartSeries = when {
            filePath.contains("R1") -> {
                mGraphSeriesDataset.getSeriesAt(0).clear()
                mGraphSeriesDataset.getSeriesAt(0)
            }
            filePath.contains("R2") -> {
                mGraphSeriesDataset.getSeriesAt(1).clear()
                mGraphSeriesDataset.getSeriesAt(1)
            }
            filePath.contains("R3") -> {
                mGraphSeriesDataset.getSeriesAt(2).clear()
                mGraphSeriesDataset.getSeriesAt(2)
            }
            else -> {
                //			Toast.makeText(MainActivity.this,
                //					"Required Log files not available",
                //					Toast.LENGTH_SHORT).show();
                return
            }
        }

        viewModel.readCharts(filePath)
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
        if (mWarnedShouldQuit) {
            finish()
        } else {
            Crouton.showText(this, R.string.toast_warn_no_undo_will_quit, Style.INFO)
            mWarnedShouldQuit = true
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
        if (mCurrentFilePath == null || mCurrentFilePath!!.isEmpty()) {
            saveContentAs()
        } else {
            doSaveFile(mCurrentFilePath)
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
        filter.addAction(EToCMainHandler.USB_DATA_READY)
        registerReceiver(mUsbReceiver, filter)
    }

    fun sendMessageWithUsbDataReceived(bytes: ByteArray?) {
        val message = mHandler.obtainMessage()
        message.obj = bytes
        message.what = EToCMainHandler.MESSAGE_USB_DATA_RECEIVED
        message.sendToTarget()
    }

    fun sendOpenChartDataToHandler(value: String?) {
        val message = mHandler.obtainMessage()
        message.obj = value
        message.what = EToCMainHandler.MESSAGE_OPEN_CHART
        message.sendToTarget()
    }

    fun sendMessageWithUsbDataReady(dataForSend: String?) {
        val message = mHandler.obtainMessage()
        message.obj = dataForSend
        message.what = EToCMainHandler.MESSAGE_USB_DATA_READY
        message.sendToTarget()
    }

    fun sendMessageForToast(dataForSend: String?) {
        val message = mHandler.obtainMessage()
        message.obj = dataForSend
        message.what = EToCMainHandler.MESSAGE_SHOW_TOAST
        message.sendToTarget()
    }

    fun interruptActionsIfAny() {
        //TODO interrupt actions
    }

    fun setCurrentSeries(index: Int) {
        currentSeries = mGraphSeriesDataset.getSeriesAt(index)
    }

    // TODO remove executor, send data to usb task to be job
    // cancel previous job when measure clicked again
    // write to file with append option only when "send data to usb" task is running
    fun execute(runnable: Runnable?) {
        mExecutor.execute(runnable)
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

    val mapChartDate: Map<Int, String>
        get() = mMapChartIndexToDate

    fun setUsbConnected(isUsbConnected: Boolean) {
        mIsUsbConnected = isUsbConnected
        invalidateOptionsMenu()
    }

    private fun changeBackground(button: View?, isPressed: Boolean) {
        if (isPressed) {
            button!!.setBackgroundResource(R.drawable.temperature_button_drawable_pressed)
        } else {
            button!!.setBackgroundResource(R.drawable.temperature_button_drawable_unpressed)
        }
    }

    fun refreshTextAccordToSensor(isTemperature: Boolean, text: String?) {
        if (isTemperature) {
            TemperatureData.parse(text).also { temperatureData ->
                if (temperatureData.isCorrect) {
                    val updateRunnable = Runnable {
                        mTemperature.text = (temperatureData.temperature1 + mTemperatureShift).toString()
                    }
                    updateRunnable.run()
                } else {
                    mTemperature.text = temperatureData.wrongPosition.toString()
                }
            }
        } else {
            mCo2.text = text
        }
    }

    fun incReadingCount() {
        readingCount++
    }

    fun refreshCurrentSeries() {
        currentSeries = GraphPopulatorUtils.addNewSet(renderer, mGraphSeriesDataset)
    }

    fun invokeAutoCalculations() {
        supportFragmentManager.findFragmentById(R.id.bottom_fragment)!!.view!!.findViewById<View>(R.id.calculate_ppm_auto)
            .performClick()
    }

    fun clearData() {
        subDirDate = null
        for (series in mGraphSeriesDataset.series) {
            series.clear()
        }
        oldCountMeasure = 0
        countMeasure = oldCountMeasure
        mMapChartIndexToDate.clear()
        GraphPopulatorUtils.clearYTextLabels(renderer)
        repaintChartView()
    }

    override fun currentDate(): Date {
        return mReportDate!!
    }

    override fun reportDateString(): String {
        mReportDate = Date()
        return FORMATTER.format(mReportDate!!)
    }

    //TODO implement this for handle report changes
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
        return FORMATTER.format(mReportDate!!)
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

    override fun deliverCommand(command: String) {
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
            val timeElapsed = Utils.elapsedTimeForSendRequest(nowTime, mLastTimePressed)
            if (timeElapsed) {
                mLastTimePressed = nowTime
                startService(
                    PullStateManagingService.intentForService(
                        this@MainActivity,
                        false
                    )
                )
            }
            mAutoPullResolverCallback.onPostPullStopped()
            if (timeElapsed) {
                mHandler.postDelayed({
                    if (powerCommandsFactory.currentPowerState() == PowerState.ON) {
                        startService(
                            PullStateManagingService.intentForService(
                                this@MainActivity,
                                true
                            )
                        )
                    }
                    mAutoPullResolverCallback.onPostPullStarted()
                }, 1000)
            }
        }
    }

    companion object {
        private const val IS_SERVICE_RUNNING = "is_service_reunning"
        private val FORMATTER = SimpleDateFormat("MM.dd.yyyy HH:mm:ss")
        fun sendBroadCastWithData(context: Context, data: String?) {
            val intent = Intent(EToCMainHandler.USB_DATA_READY)
            intent.putExtra(EToCMainHandler.DATA_EXTRA, data)
            context.sendBroadcast(intent)
        }

        fun sendBroadCastWithData(context: Context, data: Int) {
            val intent = Intent(EToCMainHandler.USB_DATA_READY)
            intent.putExtra(EToCMainHandler.DATA_EXTRA, data.toString() + "")
            intent.putExtra(EToCMainHandler.IS_TOAST, true)
            context.sendBroadcast(intent)
        }
    }
}