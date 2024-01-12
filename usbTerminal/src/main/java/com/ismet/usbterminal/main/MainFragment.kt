package com.ismet.usbterminal.main

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.Log
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.children
import androidx.core.view.forEachIndexed
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.charts.LineChart
import com.ismet.storage.BaseDirectory
import com.ismet.storage.DirectoryType
import com.ismet.usbterminal.append
import com.ismet.usbterminal.calculateSquare
import com.ismet.usbterminal.composePpmCurveText
import com.ismet.usbterminal.data.FileSavable
import com.ismet.usbterminal.data.PeriodicResponse
import com.ismet.usbterminal.data.PrefConstants
import com.ismet.usbterminal.findPpmBySquare
import com.ismet.usbterminal.format
import com.ismet.usbterminal.getDelayInSeconds
import com.ismet.usbterminal.init
import com.ismet.usbterminal.main.bottom.BottomEvent
import com.ismet.usbterminal.main.bottom.BottomViewModel
import com.ismet.usbterminal.main.bottom.REPORT_START_NAME
import com.ismet.usbterminal.main.bottom.ReportDataProvider
import com.ismet.usbterminal.main.bottom.countReports
import com.ismet.usbterminal.main.bottom.createReport
import com.ismet.usbterminal.main.bottom.defaultReport
import com.ismet.usbterminal.main.chart.configure
import com.ismet.usbterminal.main.data.AvgPoint
import com.ismet.usbterminal.main.data.ReportData
import com.ismet.usbterminal.set
import com.ismet.usbterminal.showCommandDialog
import com.ismet.usbterminal.showMeasureDialog
import com.ismet.usbterminal.showOnOffDialog
import com.ismet.usbterminal.showToast
import com.ismet.usbterminal.viewBinding
import com.ismet.usbterminalnew.R
import com.ismet.usbterminalnew.databinding.LayoutCreateCurveProgressBinding
import com.ismet.usbterminalnew.databinding.LayoutDialogOnOffBinding
import com.ismet.usbterminalnew.databinding.LayoutDialogOneCommandBinding
import com.ismet.usbterminalnew.databinding.NewFragmentMainBinding
import com.itextpdf.text.DocumentException
import dagger.hilt.android.AndroidEntryPoint
import de.keyboardsurfer.android.widget.crouton.Crouton
import de.keyboardsurfer.android.widget.crouton.Style
import fr.xgouchet.FileDialog
import fr.xgouchet.SelectionMode
import fr.xgouchet.androidlib.data.FileUtils
import fr.xgouchet.texteditor.common.Constants
import fr.xgouchet.texteditor.common.RecentFiles
import fr.xgouchet.texteditor.common.Settings
import fr.xgouchet.texteditor.common.TextFileUtils
import fr.xgouchet.texteditor.undo.TextChangeWatcher
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject


@AndroidEntryPoint
class MainFragment : Fragment(R.layout.new_fragment_main), TextWatcher {

    private val binding by viewBinding(NewFragmentMainBinding::bind)
    private val viewModel by activityViewModels<MainViewModel>()
    private val sharedUiViewModel by activityViewModels<SharedUiViewModel>()
    private val bottomViewModel by viewModels<BottomViewModel>()

    private lateinit var loadPpmCurveLauncher: ActivityResultLauncher<Intent>
    private val loadPpmCurveCallback = ActivityResultCallback<ActivityResult> {
        if (it.resultCode != Activity.RESULT_OK) return@ActivityResultCallback

        binding.resultPpmLoaded.text = ""
        val path = it.data!!.getStringExtra(FileDialog.RESULT_PATH)!!
        val selectedFile = File(path)
        if (selectedFile.isDirectory) {
            val context: Context? = activity
            val dialog = AlertDialog.Builder(context)
                .setNeutralButton(resources.getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(resources.getString(R.string.yes)) { dialog, _ ->
                    bottomViewModel.createCurveFromDirectory(selectedFile, dialog)
                }.setMessage(R.string.do_you_want_to_create_curve).create()
            dialog.show()
            val decorView = dialog.window!!.decorView
            (decorView.findViewById<View>(android.R.id.message) as TextView).gravity =
                Gravity.CENTER
            val button3 = decorView.findViewById<View>(android.R.id.button3) as Button
            button3.setTextColor(Color.BLACK)
            button3.setBackgroundResource(R.drawable.button_drawable)
            val button1 = decorView.findViewById<View>(android.R.id.button1) as Button
            button1.setTextColor(Color.BLACK)
            button1.setBackgroundResource(R.drawable.button_drawable)
        } else {
            bottomViewModel.mDoPostLoadingCalculations = false
            bottomViewModel.loadGraphData(File(path))
        }
    }

    private lateinit var mesSelectLauncher: ActivityResultLauncher<Intent>
    private val mesSelectCallback = ActivityResultCallback<ActivityResult> {
        if (it.resultCode != Activity.RESULT_OK) return@ActivityResultCallback

        bottomViewModel.mDoPostLoadingCalculations = true
        val mMesFolderFile = File(it.data!!.getStringExtra(FileDialog.RESULT_PATH)!!)
        val isCorrectFilesSelected: Boolean
        if (mMesFolderFile.isDirectory) {
            isCorrectFilesSelected =
                handleDirectoryMesSelected(searchCsvFilesInside(mMesFolderFile))
        } else {
            isCorrectFilesSelected = handleCsvFileMesSelected(mMesFolderFile)
        }
        if (!isCorrectFilesSelected) {
            showToast("Wrong files for calculating")
        }
        fillAvgPointsLayout()
    }

    private var storagePermissionLauncher : ActivityResultLauncher<Intent>? = null
    private val storagePermissionResult = ActivityResultCallback<ActivityResult> {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            Log.e("Oops", "is storage manager = ${Environment.isExternalStorageManager()}, result code: ${it.resultCode}")
            if(!Environment.isExternalStorageManager()) {
                showToast("Please grant storage permission in order to use app")
                return@ActivityResultCallback
            }
        }
        viewModel.initRequiredDirectories()
        viewModel.observeAppSettingsDirectoryUpdates()
    }

    private var storagePermissionsLauncher: ActivityResultLauncher<Array<String>> ?= null
    private val storagePermissionsResult = ActivityResultCallback<Map<String, Boolean>> {
        if (it[Manifest.permission.READ_EXTERNAL_STORAGE] != true || it[Manifest.permission.WRITE_EXTERNAL_STORAGE] != true) {
            showToast("Please grant storage permission in order to use app")
            requireActivity().finish()
            return@ActivityResultCallback
        }
        viewModel.initRequiredDirectories()
        viewModel.observeAppSettingsDirectoryUpdates()
    }

    private val realCalculationsCalculateAutoListener = {
        val autoAvgPoint = bottomViewModel.mAutoAvgPoint
        binding.avgValueLoaded.setText(autoAvgPoint.avg().format())
        val avgValueY: Float = binding.avgValueLoaded.text.toString().toFloat()
        var value: Float
        var isExpanded = false
        try {
            val ppmPoints = mutableListOf<Float>()
            val avgSquarePoints = mutableListOf<Float>()
            ppmPoints.addAll(bottomViewModel.ppmPoints)
            avgSquarePoints.addAll(bottomViewModel.avgSquarePoints)
            if (avgSquarePoints[avgSquarePoints.size - 1] < avgValueY) {
                value = calculatePpmExpanded(ppmPoints, avgSquarePoints, avgValueY)
                isExpanded = true
            } else {
                isExpanded = false
                value = findPpmBySquare(
                    avgValueY,
                    bottomViewModel.ppmPoints,
                    bottomViewModel.avgSquarePoints
                )
            }
        } catch (e: Exception) {
            value = -1f
        }
        if (value == -1f) {
            showToast(getString(R.string.wrong_data))
        } else {
            if (isExpanded) {
                showToast(createRatioString(bottomViewModel.avgSquarePoints.get(bottomViewModel.avgSquarePoints.size - 1), value))
            } else {
                binding.resultPpmLoaded.text = value.format()
            }
        }
    }

    private fun createRatioString(firstValue: Float, lastValue: Float): String {
        var ratio = lastValue / firstValue
        var intRatio = 0
        if (ratio >= 2) {
            intRatio = ratio.toInt()
        } else {
            ratio = if (ratio <= 1.25f) {
                1.25f
            } else if (ratio <= 1.5f) {
                1.5f
            } else {
                1.75f
            }
        }
        return "Dilute Sample 1 : ${if (intRatio > 0) intRatio else ratio.format(2)} or use different cal curve"
    }

    private fun calculatePpmExpanded(
        ppmPoints: List<Float>,
        avgSquarePoints: List<Float>,
        avgValueY: Float
    ): Float {
        val lastIndex = ppmPoints.size - 1
        val prevIndex = ppmPoints.size - 2
        return (ppmPoints[lastIndex] - ppmPoints[prevIndex]) /
                (avgSquarePoints[lastIndex] - avgSquarePoints[prevIndex]) * avgValueY - avgSquarePoints[prevIndex] + ppmPoints[prevIndex]
    }

    private fun onGraphDataLoaded(ppmValues: List<Float>, avgSquareValues: List<Float>, curveFileUrl: String) {
        bottomViewModel.mCurveFile = File(curveFileUrl)
        bottomViewModel.ppmPoints = ppmValues
        bottomViewModel.avgSquarePoints = avgSquareValues
        if (bottomViewModel.mDoPostLoadingCalculations) {
            fillAvgPointsLayout()
            return
        }
        var mesFile = BASE_DIRECTORY.findMesDirectory()

        if (mesFile == null) {
            showToast("Please make MES directory to find ppm")
            bottomViewModel.mDoPostLoadingCalculations = false
            return
        }

        val newestMesFile = mesFile.findNewestDirectory("MES")
        mesFile = newestMesFile
        var mesFiles = mesFile.listFiles()
        val parentOfMesFile = mesFile.parentFile
        if (mesFiles == null && parentOfMesFile != null) {
            mesFiles = parentOfMesFile.listFiles()!!
        } else if (mesFiles == null) {
            showToast("Wrong files for calculating")
            return
        }
        var newestCalFile1: File? = null
        var newestCalFile2: File? = null
        var newestCalFile3: File? = null
        for (f in mesFiles) {
            if (!f.isDirectory) {
                if (newestCalFile1 == null) {
                    newestCalFile1 = f
                } else if (newestCalFile2 == null) {
                    if (newestCalFile1.lastModified() > f.lastModified()) {
                        newestCalFile2 = newestCalFile1
                        newestCalFile1 = f
                    } else {
                        newestCalFile2 = f
                    }
                } else if (newestCalFile3 == null) {
                    if (newestCalFile2.lastModified() < f.lastModified()) {
                        newestCalFile3 = f
                    } else if (newestCalFile1.lastModified() > f.lastModified()) {
                        newestCalFile3 = newestCalFile2
                        newestCalFile2 = newestCalFile1
                        newestCalFile1 = f
                    } else {
                        newestCalFile3 = newestCalFile2
                        newestCalFile2 = f
                    }
                } else if (newestCalFile3.lastModified() > f.lastModified()) {
                    if (newestCalFile2.lastModified() > f.lastModified()) {
                        newestCalFile3 = f
                    } else if (newestCalFile1.lastModified() > f.lastModified()) {
                        newestCalFile3 = newestCalFile2
                        newestCalFile2 = f
                    } else {
                        newestCalFile3 = newestCalFile2
                        newestCalFile2 = newestCalFile1
                        newestCalFile1 = f
                    }
                }
            }
        }
        if (newestCalFile1 == null) {
            bottomViewModel.mDoPostLoadingCalculations = false
            return
        }
        val square1 = newestCalFile1.calculateSquare()
        if (square1 == -1f) {
            showToast("Chart #1 can not be calculated. Please rerecord it.")
            return
        }

        if (newestCalFile2 == null) {
            bottomViewModel.mAvgFiles = mutableListOf(newestCalFile1)
            bottomViewModel.mAutoSelected = true
            bottomViewModel.mAutoAvgPoint =
                AvgPoint(listOf(square1))
            fillAvgPointsLayout()
            realCalculationsCalculateAutoListener.invoke()
            //mClearRow2.performClick();
            return
        }
        val square2 = newestCalFile2.calculateSquare()
        if (square2 == -1f) {
            showToast("Chart #2 can not be calculated. Please rerecord it.")
            return
        }

        if (newestCalFile3 == null) {
            bottomViewModel.mAvgFiles = mutableListOf(newestCalFile1, newestCalFile2)
            bottomViewModel.mAutoSelected = true
            bottomViewModel.mAutoAvgPoint =
                AvgPoint(
                    listOf(
                        square1,
                        square2
                    )
                )
            fillAvgPointsLayout()
            realCalculationsCalculateAutoListener.invoke()
            //mClearRow2.performClick();
            return
        }
        val square3 = newestCalFile3.calculateSquare()
        if (square3 == -1f) {
            showToast("Chart #3 can not be calculated. Please rerecord it.")
            return
        }

        bottomViewModel.mAvgFiles = mutableListOf(newestCalFile1, newestCalFile2, newestCalFile3)
        bottomViewModel.mAutoSelected = true
        bottomViewModel.mAutoAvgPoint = AvgPoint(
            listOf(
                square1,
                square2,
                square3
            )
        )
        fillAvgPointsLayout()
        realCalculationsCalculateAutoListener.invoke()
        //mClearRow2.performClick();
    }

    private fun handleDirectoryMesSelected(files: List<File>): Boolean {
        val correctSquares: MutableList<Float> = java.util.ArrayList(files.size)
        val correctFiles: MutableList<File> = java.util.ArrayList()
        for (file in files) {
            val square1 = file.calculateSquare()
            if (square1 > 0) {
                correctSquares.add(square1)
                correctFiles.add(file)
            }
        }
        if (correctSquares.isEmpty()) {
            bottomViewModel.mAvgFiles = mutableListOf()
            return false
        }
        bottomViewModel.mAutoAvgPoint =
            AvgPoint(correctSquares)
        binding.avgValueLoaded.setText(bottomViewModel.mAutoAvgPoint.avg().format())
        bottomViewModel.mAvgFiles = correctFiles
        return true
    }

    private fun handleCsvFileMesSelected(csvFile: File): Boolean {
        val square1 = csvFile.calculateSquare()
        if (square1 > 0) {
            bottomViewModel.mAutoAvgPoint =
                AvgPoint(listOf(square1))
            binding.avgValueLoaded.setText(bottomViewModel.mAutoAvgPoint.avg().format())
        }
        bottomViewModel.mAvgFiles = mutableListOf(csvFile)
        return square1 > 0
    }

    private fun searchCsvFilesInside(file: File): List<File> {
        return if (!file.isDirectory) {
            if (!file.absolutePath.endsWith(".csv")) {
                listOf()
            } else {
                listOf(file)
            }
        } else {
            val result = mutableListOf<File>()
            val filesInside = file.listFiles()
            if (filesInside != null) {
                for (localFile in filesInside) {
                    result.addAll(searchCsvFilesInside(localFile))
                }
            }
            result
        }
    }

    private fun fillAvgPointsLayout() {
        binding.avgPoints.removeAllViews()
        var tv: TextView
        val curveFile = bottomViewModel.mCurveFile
        if (curveFile != null) {
            tv = TextView(activity)
            tv.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.edit_text_size_default)
            )
            tv.text = "Curve file: \"" + curveFile.getName() + "\"" + "   "
            tv.setTextColor(Color.WHITE)
            binding.avgPoints.addView(tv)
        }
        if (bottomViewModel.mAvgFiles.isNotEmpty() && bottomViewModel.mAvgFiles.size != 0) {
            tv = TextView(activity)
            tv.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.edit_text_size_default)
            )
            tv.text = "Avg files: "
            tv.setTextColor(Color.WHITE)
            binding.avgPoints.addView(tv)
            if (bottomViewModel.mAvgFiles.size == 1 || bottomViewModel.mAvgFiles.size <= 3) {

                //TODO it's remove to work only on auto calculations
                bottomViewModel.mAutoSelected = true
                for (i in 0 until bottomViewModel.mAvgFiles.size - 1) {
                    if (bottomViewModel.mAutoSelected) {
                        val file = bottomViewModel.mAvgFiles[i].parentFile!!
                        tv = TextView(activity)
                        tv.setTextSize(
                            TypedValue.COMPLEX_UNIT_PX,
                            resources.getDimension(R.dimen.edit_text_size_default)
                        )
                        tv.text = "\"" + file.name + "\":  "
                        tv.setTextColor(Color.WHITE)
                        binding.avgPoints.addView(tv)
                    }
                    bottomViewModel.mAutoSelected = false
                    val file: File = bottomViewModel.mAvgFiles[i]
                    tv = TextView(activity)
                    tv.setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        resources.getDimension(R.dimen.edit_text_size_default)
                    )
                    tv.text = "\"" + file.name + "\",  "
                    tv.setTextColor(Color.WHITE)
                    binding.avgPoints.addView(tv)
                }
                val file: File = bottomViewModel.mAvgFiles[bottomViewModel.mAvgFiles.size - 1]
                tv = TextView(activity)
                tv.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.edit_text_size_default)
                )
                tv.text = "\"" + file.name + "\"  "
                tv.setTextColor(Color.WHITE)
                binding.avgPoints.addView(tv)
            } else {
                val parentFile = bottomViewModel.mAvgFiles[0].parentFile!!
                tv = TextView(activity)
                tv.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.edit_text_size_default)
                )
                tv.text = "Folder: \"" + parentFile.name + "\"" + "   "
                tv.setTextColor(Color.WHITE)
                binding.avgPoints.addView(tv)
            }
        }
        if (bottomViewModel.mAvgFiles.isNotEmpty() || bottomViewModel.mCurveFile != null) {
            tv = TextView(activity)
            tv.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.edit_text_size_default)
            )
            tv.text = "||  "
            tv.setTextColor(Color.WHITE)
            binding.avgPoints.addView(tv)
        }
        if (!bottomViewModel.ppmPoints.isEmpty()) {
            for (i in bottomViewModel.ppmPoints.indices) {
                tv = TextView(activity)
                tv.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.edit_text_size_default)
                )
                tv.text = composePpmCurveText(
                    listOf(bottomViewModel.ppmPoints[i]), listOf(bottomViewModel.avgSquarePoints[i])
                )
                tv.setTextColor(Color.WHITE)
                binding.avgPoints.addView(tv)
            }
        }
        if (binding.avgPoints.getChildCount() == 0) {
            tv = TextView(activity)
            tv.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.edit_text_size_default)
            )
            tv.text = ""
            tv.setTextColor(Color.WHITE)
            binding.avgPoints.addView(tv)
        }
        binding.calculatePpmLayoutLoaded.visibility = View.VISIBLE
    }

    @Inject
    lateinit var prefs: SharedPreferences

    @Inject
    @BaseDirectory
    lateinit var baseDirectory: String

    private val chartHelperPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(binding.allChartsLayout.childCount > 0) {
                override fun handleOnBackPressed() {
                    binding.exportedChartLayout.removeAllViews()
                    sharedUiViewModel.isExportedChartLayoutEmpty = true
                    binding.marginLayout.setBackgroundColor(Color.TRANSPARENT)
                    binding.exportedChartLayout.setBackgroundColor(Color.TRANSPARENT)
                }
            }
        )
        binding.chart.init(prefs)
        binding.observeEvents()

        binding.editor.addTextChangedListener(this)
        binding.editor.updateFromSettings()
        sharedUiViewModel.undoWatcher = TextChangeWatcher()

        binding.power.setOnClickListener { viewModel.onPowerClick() }
        binding.buttonSend.setOnClickListener { viewModel.onSendClick() }
        binding.setPowerOnButtonListeners()
        binding.setEditorActionListener()
        binding.setClearListener()
        binding.setMeasureListener()
        binding.setReportListener()
        binding.setLoadPpmCurveListener()
        binding.setGraphListener()
        binding.setMesSelectFolderListener()
        binding.setCalculatePpmSimpleLoadedListener()
        binding.setCalculatePpmAutoListener()
        binding.setClearRowListener()

        loadPpmCurveLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            loadPpmCurveCallback
        )
        mesSelectLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            mesSelectCallback
        )

        fillAvgPointsLayout()
        createPermissionLaunchers()
        if (!checkStoragePermissions()) {
            requestStoragePermissions()
        } else {
            viewModel.initRequiredDirectories()
            viewModel.observeAppSettingsDirectoryUpdates()
        }
    }

    private fun checkStoragePermissions(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            val context = requireContext()
            val readPermissionGrant = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            val writePermissionGrant = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            readPermissionGrant == PackageManager.PERMISSION_GRANTED && writePermissionGrant == PackageManager.PERMISSION_GRANTED
        }
        else -> true
    }

    private fun createPermissionLaunchers() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), storagePermissionResult)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                storagePermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), storagePermissionsResult)
            }
        }
    }

    private fun requestStoragePermissions() {
        storagePermissionLauncher?.also { launcher ->
            try {
                val intent = Intent().apply {
                    action = android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    //data = Uri.fromParts("package", requireContext().packageName, null)
                }
                launcher.launch(intent)
            } catch (e: java.lang.Exception) {
                val intent = Intent().apply {
                    action = android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                }
                launcher.launch(intent)
            }
        }
        storagePermissionsLauncher?.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    private fun NewFragmentMainBinding.observeEvents() {
        observeMainEvents()
        observeUiEvents()
        observeBottomEvents()

        viewModel.charts.observe(viewLifecycleOwner) { chart.set(chartHelperPaint, it) }

        viewModel.buttonOn1Properties.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            buttonOn1.apply {
                text = if (it.isActivated) it.savable.activatedText else it.savable.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.buttonOn2Properties.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            buttonOn2.apply {
                text = if (it.isActivated) it.savable.activatedText else it.savable.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.buttonOn3Properties.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            buttonPpm.apply {
                text = if (it.isActivated) it.savable.activatedText else it.savable.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.buttonOn4Properties.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            buttonOn3.apply {
                text = if (it.isActivated) it.savable.activatedText else it.savable.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.buttonOn5Properties.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            buttonOn4.apply {
                text = if (it.isActivated) it.savable.activatedText else it.savable.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.buttonOn6Properties.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            buttonOn5.apply {
                text = if (it.isActivated) it.savable.activatedText else it.savable.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.powerProperties.observe(viewLifecycleOwner) {
            power.apply {
                text = if (it.isActivated) it.savable.activatedText else it.savable.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.measureProperties.observe(viewLifecycleOwner) {
            buttonMeasure.apply {
                text = if (it.isActivated) it.savable.activatedText else it.savable.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
        viewModel.sendProperties.observe(viewLifecycleOwner) {
            buttonSend.apply {
                text = if (it.isActivated) it.savable.activatedText else it.savable.text
                alpha = it.alpha
                isActivated = it.isActivated
                isEnabled = it.isEnabled
                setBackgroundResource(it.background)
            }
        }
    }

    private fun NewFragmentMainBinding.observeMainEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.eventsFlow
                .flowWithLifecycle(lifecycle)
                .collect { event ->
                    when (event) {
                        is MainEvent.ClearEditor -> editor.setText("")
                        is MainEvent.ClearOutput -> output.text = ""
                        is MainEvent.WriteToUsb -> sendCommand(event.command)
                        is MainEvent.SendCommandsFromEditor -> sendCommandsFromEditor()
                        is MainEvent.ClearData -> clearData()
                        is MainEvent.IncReadingCount -> sharedUiViewModel.readingCount++
                        is MainEvent.SetReadingCount -> sharedUiViewModel.readingCount = event.value
                        is MainEvent.IncCountMeasure -> sharedUiViewModel.incCountMeasure()
                        is MainEvent.InvokeAutoCalculations -> calculatePpmAuto.performClick()
                        else -> {}
                    }
                }
        }
    }

    private fun sendCommand(command: String) {
        binding.output.append(isRead = false, command = command)
        binding.scrollView.smoothScrollTo(0, 0)
    }

    private fun sendCommandsFromEditor() {
        val editorText = binding.editor.text.toString()
        if (editorText.isEmpty()) {
            viewModel.startSendingTemperatureOrCo2Requests()
        } else {
            editorText.split("\n").forEach(this::sendCommand)
        }
    }

    private fun clearData() {
        sharedUiViewModel.resetCountMeasure()
        viewModel.resetCharts()
    }

    private fun NewFragmentMainBinding.observeUiEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedUiViewModel
                .eventsFlow
                .flowWithLifecycle(lifecycle)
                .collect { event ->
                    when (event) {
                        is UiEvent.ClearContent -> {
                            sharedUiViewModel.undoWatcher = null
                            sharedUiViewModel.isInUndo = true
                            editor.setText("")
                            sharedUiViewModel.isDirty = false
                            sharedUiViewModel.undoWatcher = TextChangeWatcher()
                            sharedUiViewModel.isInUndo = false
                        }

                        is UiEvent.OpenFile -> {
                            if (event.uriPath != null) {
                                doOpenFile(File(event.uriPath), event.forceReadOnly)
                            } else {
                                doOpenFile(File(event.path), event.forceReadOnly)
                            }
                        }

                        is UiEvent.OpenBackup -> {
                            sharedUiViewModel.isInUndo = true
                            binding.editor.setText(event.text)
                            sharedUiViewModel.undoWatcher = TextChangeWatcher()
                            sharedUiViewModel.currentFilePath = null
                            sharedUiViewModel.currentFileName = null
                            sharedUiViewModel.isDirty = false
                            sharedUiViewModel.isInUndo = false
                            sharedUiViewModel.isReadOnly = false
                            binding.editor.isEnabled = true
                        }

                        is UiEvent.SetPeriodicResponse -> {
                            refreshTextAccordToSensor(event.response)
                        }

                        is UiEvent.SetDataReceived -> {
                            output.append(isRead = true, event.data)
                            scrollView.smoothScrollTo(0, 0)
                        }

                        is UiEvent.SetEditorSelection -> {
                            binding.editor.setSelection(event.from, event.to)
                        }

                        is UiEvent.HideProgress -> hideBottomProgress()
                    }
                }
        }
    }

    private fun onGraphAttached() {
        sharedUiViewModel.isExportedChartLayoutEmpty = false
        binding.marginLayout.setBackgroundColor(Color.BLACK)
        binding.exportedChartLayout.setBackgroundColor(Color.WHITE)
    }

    private fun NewFragmentMainBinding.hideBottomProgress() {
        val progressIndex =
            root.children.indexOfFirst { it.id == R.id.creation_curve_container }
        if (progressIndex != -1) {
            root.removeViewAt(progressIndex)
        }
    }

    private fun NewFragmentMainBinding.observeBottomEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (event in bottomViewModel.events) {
                    when (event) {
                        is BottomEvent.ShowProgress -> {
                            if (root.children.all { it.id != R.id.creation_curve_container }) {
                                root.addView(
                                    LayoutCreateCurveProgressBinding.inflate(layoutInflater).root,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).also { it.gravity = Gravity.CENTER }
                                )
                            }
                        }

                        is BottomEvent.HideProgress -> hideBottomProgress()
                        is BottomEvent.ShowToast -> showToast(event.message)
                        is BottomEvent.LoadPpmAverageValues -> {
                            event.dialog?.dismiss()
                            if (event.dialog != null) {
                                bottomViewModel.mDoPostLoadingCalculations = false
                            }
                            bottomViewModel.loadGraphData(File(event.filePath))
                        }

                        is BottomEvent.ChangeProgress -> {
                            val binding =
                                LayoutCreateCurveProgressBinding.bind(root.children.first { it.id == R.id.creation_curve_container })
                            binding.creationProgress.progress = event.progress
                        }

                        is BottomEvent.GraphDataLoaded -> {
                            onGraphDataLoaded(
                                event.ppmValues,
                                event.avgSquareValues,
                                event.filePath
                            )
                        }
                    }
                }
            }
        }
    }

    private fun refreshTextAccordToSensor(periodicResponse: PeriodicResponse) =
        when (periodicResponse) {
            is PeriodicResponse.Temperature -> {
                binding.temperature.text =
                    (periodicResponse.value + viewModel.accessorySettings.value!!.temperatureUiOffset).toString()
            }

            is PeriodicResponse.Co2 -> {
                binding.co2.text = periodicResponse.value.toString()
            }
        }

    /**
     * Opens the given file and replace the editors content with the file.
     * Assumes that user was prompted and previous data was saved
     *
     * @param file          the file to load
     * @param forceReadOnly force the file to be used as read only
     * @return if the file was loaded successfully
     */
    private fun doOpenFile(file: File, forceReadOnly: Boolean): Boolean {
        val text: String?
        try {
            text = TextFileUtils.readTextFile(file)
            if (text != null) {
                sharedUiViewModel.isInUndo = true
                if (binding.editor.text.toString() == "") {
                    binding.editor.append(text)
                } else {
                    binding.editor.append(
                        """
                            
                            $text
                            """.trimIndent()
                    )
                }
                sharedUiViewModel.undoWatcher = TextChangeWatcher()
                sharedUiViewModel.currentFilePath = FileUtils.getCanonizePath(file)
                sharedUiViewModel.currentFileName = file.name
                RecentFiles.updateRecentList(sharedUiViewModel.currentFilePath)
                RecentFiles.saveRecentList(
                    requireContext().getSharedPreferences(
                        Constants.PREFERENCES_NAME,
                        AppCompatActivity.MODE_PRIVATE
                    )
                )
                sharedUiViewModel.isDirty = false
                sharedUiViewModel.isInUndo = false
                if (file.canWrite() && !forceReadOnly) {
                    sharedUiViewModel.isReadOnly = false
                    binding.editor.isEnabled = true
                } else {
                    sharedUiViewModel.isReadOnly = true
                    binding.editor.isEnabled = false
                }
                return true
            } else {
                Crouton.showText(requireActivity(), R.string.toast_open_error, Style.ALERT)
            }
        } catch (e: OutOfMemoryError) {
            Crouton.showText(requireActivity(), R.string.toast_memory_open, Style.ALERT)
        }
        return false
    }

    private fun NewFragmentMainBinding.setPowerOnButtonListeners() {
        buttonOn1.setOnClickListener { viewModel.onButton1Click() }
        buttonOn1.setOnLongClickListener {
            requireContext().showOnOffDialog(
                init = {
                    changeTextsForButtons(it)
                    val button1Savable = viewModel.buttonOn1Properties.value!!.savable
                    it.editOn.setText(button1Savable.command)
                    it.editOff.setText(button1Savable.activatedCommand)
                    it.editOn1.setText(button1Savable.text)
                    it.editOff1.setText(button1Savable.activatedText)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val activatedCommand = localBinding.editOff.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val activatedText = localBinding.editOff1.text.toString()
                    val isSuccess = viewModel.changeButton1PersistedInfo(
                        FileSavable(text, command, activatedText, activatedCommand)
                    )
                    if (!isSuccess) {
                        showToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
        buttonOn2.setOnClickListener { viewModel.onButton2Click() }
        buttonOn2.setOnLongClickListener {
            requireContext().showOnOffDialog(
                init = {
                    changeTextsForButtons(it)
                    val button2Savable = viewModel.buttonOn2Properties.value!!.savable
                    it.editOn.setText(button2Savable.command)
                    it.editOff.setText(button2Savable.activatedCommand)
                    it.editOn1.setText(button2Savable.text)
                    it.editOff1.setText(button2Savable.activatedText)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val activatedCommand = localBinding.editOff.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val activatedText = localBinding.editOff1.text.toString()
                    val isSuccess = viewModel.changeButton2PersistedInfo(
                        FileSavable(text, command, activatedText, activatedCommand)
                    )
                    if (!isSuccess) {
                        showToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
        buttonPpm.setOnClickListener { viewModel.onButton3Click() }
        buttonPpm.setOnLongClickListener {
            requireContext().showOnOffDialog(
                init = {
                    changeTextsForButtons(it)
                    val button3Savable = viewModel.buttonOn3Properties.value!!.savable
                    it.editOn.setText(button3Savable.command)
                    it.editOff.setText(button3Savable.activatedCommand)
                    it.editOn1.setText(button3Savable.text)
                    it.editOff1.setText(button3Savable.activatedText)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val activatedCommand = localBinding.editOff.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val activatedText = localBinding.editOff1.text.toString()
                    val isSuccess = viewModel.changeButton3PersistedInfo(
                        FileSavable(text, command, activatedText, activatedCommand)
                    )
                    if (!isSuccess) {
                        showToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
        buttonOn3.setOnClickListener { viewModel.onButton4Click() }
        buttonOn3.setOnLongClickListener {
            requireContext().showCommandDialog(
                init = {
                    changeTextsForButtons(it)
                    val button4Savable = viewModel.buttonOn4Properties.value!!.savable
                    it.editOn.setText(button4Savable.command)
                    it.editOn1.setText(button4Savable.text)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val isSuccess = viewModel.changeButton4PersistedInfo(
                        FileSavable(text, command, text, command)
                    )
                    if (!isSuccess) {
                        showToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
        buttonOn4.setOnClickListener { viewModel.onButton5Click() }
        buttonOn4.setOnLongClickListener {
            requireContext().showCommandDialog(
                init = {
                    changeTextsForButtons(it)
                    val button5Savable = viewModel.buttonOn3Properties.value!!.savable
                    it.editOn.setText(button5Savable.command)
                    it.editOn1.setText(button5Savable.text)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val isSuccess = viewModel.changeButton5PersistedInfo(
                        FileSavable(text, command, text, command)
                    )
                    if (!isSuccess) {
                        showToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
        buttonOn5.setOnClickListener { viewModel.onButton6Click() }
        buttonOn5.setOnLongClickListener {
            requireContext().showCommandDialog(
                init = {
                    changeTextsForButtons(it)
                    val button6Savable = viewModel.buttonOn6Properties.value!!.savable
                    it.editOn.setText(button6Savable.command)
                    it.editOn1.setText(button6Savable.text)
                },
                okClick = { localBinding, dialog ->
                    val command = localBinding.editOn.text.toString()
                    val text = localBinding.editOn1.text.toString()
                    val isSuccess = viewModel.changeButton6PersistedInfo(
                        FileSavable(text, command, text, command)
                    )
                    if (!isSuccess) {
                        showToast("Please enter all values")
                    } else {
                        dialog.cancel()
                    }
                }
            )
            true
        }
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

    private fun changeTextsForButtons(binding: LayoutDialogOneCommandBinding) {
        val addTextBuilder = StringBuilder()
        for (i in 0..8) {
            addTextBuilder.append(' ')
        }
        binding.txtOn.text = "${addTextBuilder}Command: "
        binding.txtOn1.text = "Button State Name: "
    }

    private fun NewFragmentMainBinding.setEditorActionListener() {
        editor.setOnEditorActionListener { _, actionId, _ ->
            var handled = false
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                // sendMessage();
                handled = true
            }
            handled
        }
    }

    private fun NewFragmentMainBinding.setClearListener() {
        buttonClear.setOnClickListener {
            if (viewModel.isCo2Measuring) {
                showToast("Timer is running. Please wait")
                return@setOnClickListener
            }
            val items = viewModel.allClearOptions.toTypedArray()
            val checkedItems = viewModel.checkedClearOptions.toBooleanArray()

            val alert = AlertDialog.Builder(requireContext()).apply {
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
    }

    private fun NewFragmentMainBinding.setMeasureListener() {
        buttonMeasure.setOnClickListener { measure ->
            if (!viewModel.powerProperties.value!!.isActivated) {
                return@setOnClickListener
            }
            if (viewModel.isCo2Measuring) {
                showToast("Timer is running. Please wait")
                return@setOnClickListener
            }
            var isChart1Clear = true
            var isChart2Clear = true
            var isChart3Clear = true
            for ((i, series) in viewModel.charts.value!!.charts.withIndex()) {
                if (series.points.isNotEmpty()) {
                    when (i) {
                        0 -> isChart1Clear = false
                        1 -> isChart2Clear = false
                        2 -> isChart3Clear = false
                        else -> {}
                    }
                }
            }
            if (!isChart1Clear && !isChart2Clear && !isChart3Clear) {
                showToast("No chart available. Please clear one of the charts")
                return@setOnClickListener
            }
            measure.showMeasureDialog(
                init = {
                    val delay = (prefs.getDelayInSeconds() * 1000).toInt()
                    val duration =
                        prefs.getInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT)
                    val volume =
                        prefs.getInt(PrefConstants.VOLUME, PrefConstants.VOLUME_DEFAULT)
                    val kppm = prefs.getInt(PrefConstants.KPPM, -1)
                    val user_comment = prefs.getString(PrefConstants.USER_COMMENT, "")
                    it.editDelay.setText(delay.toString())
                    it.editDuration.setText(duration.toString())
                    it.editVolume.setText(volume.toString())
                    it.editUserComment.setText(user_comment)
                    it.commandsEditText1.setText(viewModel.measureFileNames[0])
                    it.commandsEditText2.setText(viewModel.measureFileNames[1])
                    it.commandsEditText3.setText(viewModel.measureFileNames[2])
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
                    if (viewModel.measureCo2Values(
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
                            countMeasure = sharedUiViewModel.countMeasure
                        )
                    ) {
                        dialog.cancel()
                    }
                }
            )
        }
    }

    private fun NewFragmentMainBinding.setReportListener() {
        report.setOnClickListener {
            if (resultPpmLoaded.text.isEmpty() || bottomViewModel.mAvgFiles.isEmpty()) {
                showToast("Do auto calculations!")
                return@setOnClickListener
            }
            val context = requireContext()

            exportedChartLayout.removeAllViews()
            val frameLayout = FrameLayout(context)
            exportedChartLayout.addView(
                frameLayout,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            val webView = WebView(context)
            frameLayout.addView(
                webView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            onGraphAttached()
            val reportData = ReportData()
            reportData.ppm = resultPpmLoaded.text.toString().toFloat()
            val parentFile = bottomViewModel.mAvgFiles[0].parentFile!!
            reportData.measurementFolder = parentFile.name
            val measurementFiles = mutableListOf<String>()
            for (avgFile in bottomViewModel.mAvgFiles) {
                measurementFiles.add(avgFile.name)
            }
            reportData.measurementFiles = measurementFiles
            val measurementValues: List<Float> =
                ArrayList<Float>(bottomViewModel.mAutoAvgPoint.values)
            reportData.measurementAverages = measurementValues
            reportData.calibrationCurveFolder = bottomViewModel.mCurveFile!!.getName()
            reportData.ppmData = bottomViewModel.ppmPoints
            reportData.avgData = bottomViewModel.avgSquarePoints
            reportData.countMeasurements = bottomViewModel.mAvgFiles.size
            val reportDataProvider = requireActivity() as ReportDataProvider
            val reportDataItems = defaultReport(reportData, reportDataProvider)
            val htmlText = Html.toHtml(createReport(reportDataItems))
            val reportFolder = getReportBaseDirectory()
            val reportNumber = countReports(reportFolder)
            val fileName = REPORT_START_NAME +
                    bottomViewModel.formatter.format(reportDataProvider.reportDate()) +
                    "_" + reportNumber
            saveHtmlReport(fileName, htmlText)
            webView.loadDataWithBaseURL(null, htmlText, null, "UTF-8", null)
            val button = Button(context)
            button.background =
                ResourcesCompat.getDrawable(resources, R.drawable.button_drawable, null)
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.edit_text_size_default))
            button.text = "SAVE PDF"
            val padding = resources.getDimension(R.dimen.text_margin_default).toInt()
            button.setPadding(padding, padding, padding, padding)
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                resources.getDimension(R.dimen.button_height_default).toInt()
            )
            params.gravity = GravityCompat.END
            params.rightMargin = 10
            params.topMargin = 10
            frameLayout.addView(button, params)
            button.setOnClickListener {
                val jobName = "$fileName Report"
                val printAdapter = if (Build.VERSION.SDK_INT >= 21) {
                    webView.createPrintDocumentAdapter(jobName)
                } else if (Build.VERSION.SDK_INT >= 19) {
                    webView.createPrintDocumentAdapter()
                } else {
                    null
                }
                if (printAdapter != null && Build.VERSION.SDK_INT >= 19) {
                    // Create a print job with name and adapter instance
                    val printManager =
                        it.context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    printManager.print(
                        jobName, printAdapter,
                        PrintAttributes.Builder().build()
                    )
                } else {
                    /*
                        Toast.makeText(getActivity(), "Impossible to print because of " +
                            "old api! Current api: " + Build.VERSION.SDK_INT + ". " +
                            "Required api: 19", Toast.LENGTH_LONG).show();
                    */
                }
                val newPdf = File(reportFolder, "$fileName.pdf")
                try {
                    createReport(reportDataItems, newPdf.absolutePath)
                } catch (e: DocumentException) {
                    e.printStackTrace()
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun saveHtmlReport(fileName: String, html: String) {
        val file = File(getReportBaseDirectory(), "$fileName.html")
        file.parentFile!!.mkdirs()
        try {
            file.createNewFile()
            TextFileUtils.writeTextFile(file.absolutePath, html)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getReportBaseDirectory(): File {
        return File(baseDirectory, DirectoryType.REPORT.getDirectoryName())
    }

    private fun NewFragmentMainBinding.setLoadPpmCurveListener() {
        loadPpmCurve.setOnClickListener {
            val activity = requireActivity()
            val intent = Intent(activity, FileDialog::class.java)
            intent.putExtra(
                FileDialog.START_PATH,
                BASE_DIRECTORY.absolutePath
            )
            intent.putExtra(
                FileDialog.ROOT_PATH,
                BASE_DIRECTORY.absolutePath
            )
            intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN)
            intent.setCustomDirectoryDrawables()
            intent.putExtra(FileDialog.FORMAT_FILTER, arrayOf("csv"))
            intent.putExtra(FileDialog.CAN_SELECT_DIR, true)
            loadPpmCurveLauncher.launch(intent)
        }
    }

    private fun NewFragmentMainBinding.setGraphListener() {
        graph.setOnClickListener {
            val activity: Activity? = activity
            if (bottomViewModel.ppmPoints.isEmpty()) {
                showToast("Please load CAL_Curve")
                return@setOnClickListener
            }
            val ppmPoints = mutableListOf<Float>()
            val avgSquarePoints = mutableListOf<Float>()
            ppmPoints.addAll(bottomViewModel.ppmPoints)
            avgSquarePoints.addAll(bottomViewModel.avgSquarePoints)
            val ppmStrings = java.util.ArrayList<String>(ppmPoints.size)
            val squareStrings = java.util.ArrayList<String>(avgSquarePoints.size)
            for (ppm in ppmPoints) {
                ppmStrings.add(ppm.toInt().toString())
            }
            for (square in avgSquarePoints) {
                squareStrings.add(square.format())
            }
            val viewGroup = exportedChartLayout
            val lineChart = LineChart(activity)
            val countPoints = squareStrings.size
            val squares = SparseArray<Float>(countPoints)
            for (i in 0 until countPoints) {
                squares.put(ppmStrings[i].toInt(), squareStrings[i].toFloat())
            }
            if (squares.size() < 2) {
                showToast("Please load Correct Calibration Curve!")
                return@setOnClickListener
            }
            lineChart.configure(squares)
            viewGroup.removeAllViews()
            val frameLayout = FrameLayout(activity!!)
            var params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60)
            params.gravity = GravityCompat.END or Gravity.BOTTOM

            //View v1 = new View(activity);
            //v1.setBackgroundColor(Color.BLACK);
            //frameLayout.addView(v1, params);
            val containerParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            viewGroup.addView(frameLayout, containerParams)
            frameLayout.addView(
                lineChart,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            var textView = TextView(activity, null, R.style.TextViewDefaultStyle)
            textView.setTextColor(Color.BLACK)
            textView.text = "ppm"
            params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.gravity = GravityCompat.END or Gravity.BOTTOM
            params.rightMargin = 10
            params.bottomMargin = 20
            frameLayout.addView(textView, params)
            textView = TextView(activity, null, R.style.TextViewDefaultStyle)
            textView.setTextColor(Color.BLACK)
            textView.text = "SAV"
            params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.leftMargin = 20
            params.topMargin = 20
            frameLayout.addView(textView, params)
            onGraphAttached()
            lineChart.invalidate()
        }
    }

    private fun NewFragmentMainBinding.setMesSelectFolderListener() {
        mesSelectFolder.setOnClickListener {
            val activity = requireActivity()
            val intent = Intent(activity, FileDialog::class.java)
            val extFile = Environment.getExternalStorageDirectory()
            intent.putExtra(FileDialog.START_PATH, extFile.absolutePath)
            intent.putExtra(FileDialog.ROOT_PATH, extFile.absolutePath)
            intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN)

            intent.putExtra(FileDialog.MES_SELECTION_NAMES, arrayOf("CAL_FILES", "MES_Files"))
            intent.putExtra(FileDialog.CAN_SELECT_DIR, true)

            intent.setCustomDirectoryDrawables()

            mesSelectLauncher.launch(intent)
        }
    }

    private fun NewFragmentMainBinding.setCalculatePpmSimpleLoadedListener() {
        calculatePpmLoaded.setOnClickListener {
            if (avgValueLoaded.text.toString().isEmpty()) {
                showToast(getString(R.string.input_avg_value))
                return@setOnClickListener
            }
            val avgValueY = avgValueLoaded.text.toString().toFloat()
            var value: Float

            var isExpanded = false

            try {
                val ppmPoints = mutableListOf<Float>()
                val avgSquarePoints = mutableListOf<Float>()
                ppmPoints.addAll(bottomViewModel.ppmPoints)
                avgSquarePoints.addAll(bottomViewModel.avgSquarePoints)
                if (avgSquarePoints[avgSquarePoints.size - 1] < avgValueY) {
                    value = calculatePpmExpanded(ppmPoints, avgSquarePoints, avgValueY)
                    isExpanded = true
                } else {
                    value = findPpmBySquare(
                        avgValueY,
                        ppmPoints,
                        avgSquarePoints
                    )
                    isExpanded = false
                }
            } catch (e: java.lang.Exception) {
                value = -1f
            }

            if (value == -1f) {
                showToast(getString(R.string.wrong_data))
            } else {
                if (isExpanded) {
                    showToast(createRatioString(bottomViewModel.avgSquarePoints[bottomViewModel.avgSquarePoints.size - 1], value))
                } else {
                    resultPpmLoaded.text = value.format()
                }
            }
        }
    }

    private fun NewFragmentMainBinding.setCalculatePpmAutoListener() {
        calculatePpmAuto.setOnClickListener {
            bottomViewModel.calculatePpmAuto(BASE_DIRECTORY.findNewestDirectory("CAL"))
        }
    }

    private fun NewFragmentMainBinding.setClearRowListener() {
        clearRow.setOnClickListener {
            resultPpmLoaded.text = ""
            avgValueLoaded.setText("")
            bottomViewModel.mAvgFiles = mutableListOf()
            bottomViewModel.mCurveFile = null
            bottomViewModel.ppmPoints = mutableListOf()
            bottomViewModel.avgSquarePoints = mutableListOf()
            fillAvgPointsLayout()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.editor.updateFromSettings()
    }

    override fun beforeTextChanged(
        oldText: CharSequence,
        start: Int,
        length: Int,
        newLength: Int
    ) {
        if (Settings.UNDO && !sharedUiViewModel.isInUndo) {
            sharedUiViewModel.undoWatcher?.beforeChange(oldText, start, length, newLength)
        }
    }

    override fun onTextChanged(
        newText: CharSequence?,
        start: Int,
        oldLength: Int,
        newLength: Int
    ) {
        if (Settings.UNDO && !sharedUiViewModel.isInUndo) {
            sharedUiViewModel.undoWatcher?.afterChange(newText, start, oldLength, newLength)
        }
        sharedUiViewModel.editorText = binding.editor.text
    }

    override fun afterTextChanged(s: Editable?) {
        if (!sharedUiViewModel.isDirty) sharedUiViewModel.isDirty = true
    }
}