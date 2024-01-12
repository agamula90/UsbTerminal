package com.ismet.usbterminal

import com.ismet.usbterminal.main.DATE_TIME_FORMATTER
import com.ismet.usbterminal.main.MainEvent
import com.ismet.usbterminal.main.MainViewModel

import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.graphics.PointF
import android.hardware.usb.UsbManager
import android.os.*
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ismet.usbterminal.main.SharedUiViewModel
import com.ismet.storage.BaseDirectory
import com.ismet.usb.UsbAccessory
import com.ismet.usbterminal.data.*
import com.ismet.usbterminalnew.R
import com.ismet.usbterminal.main.bottom.ReportDataProvider
import com.ismet.usbterminalnew.BuildConfig
import com.ismet.usbterminalnew.databinding.NewLayoutMainBinding
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
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.text.DateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ReportDataProvider {

    @Inject
    @BaseDirectory
    lateinit var baseDirectory: String

    private val usbReceiver: BroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    findDevice()
                    showToast("USB Device Attached")
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.extras!!.getParcelable<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)!!
                    showToast("USB Device Detached")
                    val deviceId = UsbDeviceId(vendorId = device.vendorId, productId = device.productId)
                    if (usbDevice?.deviceId == deviceId) {
                        usbDevice?.close()
                        usbDevice = null
                        showToast("USB disconnected")
                        setUsbConnected(false)
                    }
                }
                else -> {
                    Log.e(TAG, "unhandled broadcast: ${intent.action}")
                }
            }
        }
    }

    private var isWarnedShouldQuit = false

    /**
     * are we in a post activity result ?
     */
    private var isReadIntent = false

    private var isUsbConnected = false

    @Inject
    lateinit var prefs: SharedPreferences
    private lateinit var usbDeviceConnection: UsbDeviceConnection

    @Inject
    lateinit var usbEmitter: UsbEmitter

    @Inject
    lateinit var moshi: Moshi

    private var usbDevice: UsbDevice? = null
    private val viewModel: MainViewModel by viewModels()
    private val sharedUiViewModel: SharedUiViewModel by viewModels()
    private var reportDate: Date? = null
    private var coolingDialog: Dialog? = null
    private var corruptionDialog: Dialog? = null

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

    lateinit var binding: NewLayoutMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = NewLayoutMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupDrawer()
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
        observeEvents()
        observeUsbEvents()
        isReadIntent = true
        isWarnedShouldQuit = false

        setFilters()
        usbDeviceConnection = UsbDeviceConnection(this) { _, device ->
            usbDevice = device
            when(val notNullDevice = usbDevice) {
                null -> {
                    showToast("USB Permission not granted")
                    finish()
                }
                else -> notNullDevice.refreshUi()
            }
        }
        findDevice()
        establishMockedConnections()
    }

    private fun setupDrawer() {
        setSupportActionBar(binding.toolbar)
        with(supportActionBar!!) {
            setHomeButtonEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(true)
            setDisplayUseLogoEnabled(false)
            setDisplayShowCustomEnabled(true)
            setCustomView(R.layout.toolbar)
            (customView.findViewById<View>(R.id.title) as TextView).text = getString(R.string.app_name_with_version, BuildConfig.VERSION_NAME)
            setDisplayHomeAsUpEnabled(false)
        }
    }

    private fun establishMockedConnections() {
        val intent = Intent("com.ismet.usb.accessory").apply { `package` = "com.ismet.usbaccessory" }
        bindService(intent, sendToAccessoryConnection, Context.BIND_AUTO_CREATE)
    }

    private fun findDevice() {
        try {
            usbDeviceConnection.findDevice()
        } catch (_: FindDeviceException) {
            showToast("No USB connected")
            setUsbConnected(false)
        }
    }

    private fun UsbDevice.refreshUi() {
        showToast("USB Ready")
        setUsbConnected(true)
        if (!isConnectionEstablished()) {
            showToast("USB device not supported")
            setUsbConnected(false)
            close()
            usbDevice = null
        } else {
            readCallback = OnDataReceivedCallback {
                runOnUiThread { onDataReceived(it) }
            }
        }
    }

    private fun observeUsbEvents() {
        lifecycleScope.launch {
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
        lifecycleScope.launch {
            viewModel
                .eventsFlow
                .flowWithLifecycle(lifecycle)
                .collect { event ->
                    when (event) {
                        is MainEvent.ShowToast -> showToast(event.message)
                        is MainEvent.WriteToUsb -> sendCommand(event.command)
                        is MainEvent.DismissCoolingDialog -> coolingDialog?.dismiss()
                        is MainEvent.ShowWaitForCoolingDialog -> {
                            sharedUiViewModel.hideProgress()
                            coolingDialog = Dialog(this@MainActivity).apply {
                                requestWindowFeature(Window.FEATURE_NO_TITLE)
                                setContentView(R.layout.layout_cooling)
                                window!!.setBackgroundDrawableResource(android.R.color.transparent)
                                (findViewById<View>(R.id.text) as TextView).text = event.message
                                setCancelable(false)
                                show()
                            }
                        }

                        is MainEvent.ShowCorruptionDialog -> {
                            showCorruptionDialog(event.message)
                        }

                        is MainEvent.DismissCorruptionDialog -> {
                            corruptionDialog?.dismiss()
                            corruptionDialog = null
                        }

                        else -> {}
                    }
                }
        }
    }

    private fun showCorruptionDialog(message: String) {
        corruptionDialog?.dismiss()
        corruptionDialog = AlertDialog.Builder(this)
            .setTitle("Few files are corrupted")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") {_, _ -> finish()}
            .show()
    }

    private var lastCommand: String? = null

    private fun sendCommand(command: String) {
        lastCommand = command
        if (usbDevice != null) {
            usbDevice?.write(command.encodeToByteArrayEnhanced())
        } else {
            try {
                usbAccessory?.setToUsb(command.encodeToByteArrayEnhanced())
            } catch (_: RemoteException) {
                //ignore
            }
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
    }

    override fun onPause() {
        super.onPause()
        if (Settings.FORCE_AUTO_SAVE && sharedUiViewModel.isDirty && !sharedUiViewModel.isReadOnly) {
            if (sharedUiViewModel.currentFilePath == null || sharedUiViewModel.currentFilePath!!.isEmpty()) {
                doAutoSaveFile()
            } else if (Settings.AUTO_SAVE_OVERWRITE) {
                doSaveFile(sharedUiViewModel.currentFilePath)
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
                    sharedUiViewModel.openFile(extras.getString("path")!!, false)
                } else if (extras.getString("path")!!.endsWith(".csv")) {
                    val filePath = extras.getString("path")!!
                    viewModel.readChart(filePath)
                } else {
                    showToast("Invalid File")
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
        if (!sharedUiViewModel.isReadOnly) wrapMenuItem(
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
        return entriesCount == 0 && sharedUiViewModel.isExportedChartLayoutEmpty
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
                    sharedUiViewModel.openFileFromURI(URI(intent.data.toString()), false)
                } catch (e: URISyntaxException) {
                    Crouton.showText(this, R.string.toast_intent_invalid_uri, Style.ALERT)
                } catch (e: IllegalArgumentException) {
                    Crouton.showText(this, R.string.toast_intent_illegal, Style.ALERT)
                }
            }
            Constants.ACTION_WIDGET_OPEN -> {
                try {
                    sharedUiViewModel.openFileFromURI(URI(intent.data.toString()), intent.getBooleanExtra(Constants.EXTRA_FORCE_READ_ONLY, false))
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
                loaded = try {
                    sharedUiViewModel.openFile(Settings.HOME_PAGE_PATH, false)
                    TextFileUtils.readTextFile(file) != null
                } catch (e: OutOfMemoryError) {
                    false
                }
            }
        }
        if (!loaded) doClearContents()
    }

    /**
     * Clears the content of the editor. Assumes that user was prompted and
     * previous data was saved
     */
    private fun doClearContents() {
        sharedUiViewModel.clearContent()
        sharedUiViewModel.currentFilePath = null
        sharedUiViewModel.currentFileName = null
        Settings.END_OF_LINE = Settings.DEFAULT_END_OF_LINE
        sharedUiViewModel.isReadOnly = false
        isWarnedShouldQuit = false
        TextFileUtils.clearInternal(applicationContext)
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
                sharedUiViewModel.openBackup(text)
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
        val content: String = sharedUiViewModel.editorText.toString()
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
        sharedUiViewModel.currentFilePath = FileUtils.getCanonizePath(File(path))
        sharedUiViewModel.currentFileName = File(path).name
        RecentFiles.updateRecentList(path)
        RecentFiles.saveRecentList(getSharedPreferences(Constants.PREFERENCES_NAME, MODE_PRIVATE))
        sharedUiViewModel.isReadOnly = false
        sharedUiViewModel.isDirty = false
        Crouton.showText(this, R.string.toast_save_success, Style.CONFIRM)
    }

    private fun doAutoSaveFile() {
        val text = sharedUiViewModel.editorText.toString()
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
        sharedUiViewModel.isInUndo = true
        val caret = sharedUiViewModel.undoWatcher!!.undo(sharedUiViewModel.editorText!!)
        if (caret >= 0) {
            sharedUiViewModel.setEditorSelection(caret, caret)
            didUndo = true
        }
        sharedUiViewModel.isInUndo = false
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
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(e.message!!)
        }
        super.finish()
    }

    /**
     * General save command : check if a path exist for the current content,
     * then save it , else invoke the [MainActivity.saveContentAs] method
     */
    private fun saveContent() {
        if (sharedUiViewModel.currentFilePath.isNullOrEmpty()) {
            saveContentAs()
        } else {
            doSaveFile(sharedUiViewModel.currentFilePath)
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
        val request = lastCommand ?: "None"
        return ResponseLogEvent(request, response.decodeToStringEnhanced())
    }

    private fun onDataReceived(bytes: ByteArray) {
        val logEvent = createLogEvent(bytes)
        viewModel.cacheResponseLog(logEvent)
        val periodicResponse = bytes.decodeToPeriodicResponse()
        val data = when(periodicResponse) {
            is PeriodicResponse.Temperature -> {
                periodicResponse.toString()
            }
            is PeriodicResponse.Co2 -> {
                // auto
                val delay = prefs.getDelayInSeconds()
                val duration = prefs.getInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)
                val isAuto = prefs.getBoolean(PrefConstants.IS_AUTO, false)
                if (isAuto) {
                    if (sharedUiViewModel.readingCount == (duration * 60 / delay).toInt()) {
                        sharedUiViewModel.incCountMeasure()
                        viewModel.setCurrentChartIndex(1)
                    } else if (sharedUiViewModel.readingCount == (duration * 60)) {
                        sharedUiViewModel.incCountMeasure()
                        viewModel.setCurrentChartIndex(2)
                    }
                }
                val shouldInitDate = sharedUiViewModel.countMeasure != sharedUiViewModel.oldCountMeasure
                if (sharedUiViewModel.countMeasure != sharedUiViewModel.oldCountMeasure) {
                    sharedUiViewModel.refreshOldCountMeasure()
                }
                viewModel.onCurrentChartWasModified(wasModified = shouldInitDate)
                if (viewModel.isCo2Measuring) {
                    viewModel.addPointToCurrentChart(PointF(sharedUiViewModel.readingCount.toFloat(), periodicResponse.value.toFloat()))
                }
                if (periodicResponse.value == 10000) {
                    showToast("Dilute sample")
                }
                periodicResponse.toString()
            }
            else -> bytes.decodeToString()
        }
        if (periodicResponse != null) {
            sharedUiViewModel.setPeriodicResponse(periodicResponse)
        }

        sharedUiViewModel.setData(data)
        viewModel.onDataReceived(bytes)
    }

    fun setUsbConnected(isUsbConnected: Boolean) {
        this.isUsbConnected = isUsbConnected
        invalidateOptionsMenu()
    }

    override fun reportDate(): Date = reportDate!!

    override fun initReport() {
        reportDate = Date()
    }

    override fun countMinutes(): Int {
        return prefs.getInt(PrefConstants.DURATION, 0)
    }

    override fun volume(): Int {
        return prefs.getInt(PrefConstants.VOLUME, 0)
    }

    override fun dateFormat(): DateFormat = DATE_TIME_FORMATTER

    companion object {
        private const val TAG = "MainActivity"
    }
}