package com.ismet.usbterminal.main.bottom

import android.content.DialogInterface
import android.util.SparseArray
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ismet.usbterminal.main.parseGraphData
import com.ismet.usbterminal.main.saveAvgValuesToFile
import com.ismet.usbterminal.main.data.AvgPoint
import com.ismet.usbterminal.main.data.Constants
import com.ismet.usbterminal.calculateSquare
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import javax.inject.Inject

private const val PROGRESS_DELAY = 500L
private const val MIN_PROGRESS_TIME = 500L

@HiltViewModel
class BottomViewModel @Inject constructor(

): ViewModel() {
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss");

    val events = Channel<BottomEvent>()
    var ppmPoints: List<Float> = mutableListOf()
    var avgSquarePoints: List<Float> = mutableListOf()
    var mAutoAvgPoint = AvgPoint(listOf())

    var mCurveFile: File? = null
    var mAvgFiles: List<File> = mutableListOf()

    var mAutoSelected = false
    var mDoPostLoadingCalculations = false

    fun createCurveFromDirectory(selectedDirectory: File, dialog: DialogInterface) = viewModelScope.launch(Dispatchers.IO) {
        events.send(BottomEvent.ShowProgress)

        val mCurveFiles = SparseArray<MutableList<File>>()

        val filesInside = selectedDirectory.listFiles { file -> !file.isDirectory }
        val ppmFiles: MutableList<File> = ArrayList(
            filesInside!!.size
        )
        for (file in filesInside) {
            val index: Int = detectPpmFromName(file)
            if (index != -1) {
                if (mCurveFiles.get(index) == null) {
                    mCurveFiles.put(index, ArrayList())
                }
                mCurveFiles.get(index).add(file)
            }
            ppmFiles.add(file)
        }

        val folderWithCurve =
            File(selectedDirectory, Constants.CALIBRATION_CURVE_NAME).also { it.mkdir() }

        var countFilesToProcess = 0

        for (i in 0 until mCurveFiles.size()) {
            countFilesToProcess += mCurveFiles.valueAt(i).size
        }

        val ppmValues = mutableListOf<Float>()
        val averageValues = mutableListOf<List<Float>>()

        var countOperationsProcessed = 0

        for (i in 0 until mCurveFiles.size()) {
            val ppmValue = mCurveFiles.keyAt(i)
            ppmValues.add(ppmValue.toFloat())
            val curveSquares = mutableListOf<Float>()
            averageValues.add(curveSquares)
            val curveFiles = mCurveFiles.valueAt(i)
            for (file in curveFiles) {
                val square = file.calculateSquare()
                if (square < 0f) {
                    events.send(
                        BottomEvent.ShowToast(
                            message = "Creating calibration curve... Chart #$ppmValue can not be calculated. Please rerecord it."
                        )
                    )
                    delay(MIN_PROGRESS_TIME)
                    events.send(BottomEvent.HideProgress)
                    events.send(
                        BottomEvent.ShowToast("There is no curve values")
                    )
                    return@launch
                }
                curveSquares.add(square)
                countOperationsProcessed++
                delay(PROGRESS_DELAY)
                val progress =
                    (countOperationsProcessed / countFilesToProcess.toFloat() * 100).toInt()
                events.send(BottomEvent.ChangeProgress(progress))
            }
        }

        val tableFile = File(
            folderWithCurve, "${Constants.CALIBRATION_CURVE_NAME}_${generateDate()}.csv"
        )

        val isSaveSucceeded = try {
            tableFile.createNewFile()
            saveAvgValuesToFile(ppmValues, averageValues, tableFile.absolutePath,true)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }

        delay(PROGRESS_DELAY)
        events.send(BottomEvent.HideProgress)
        if (!isSaveSucceeded) {
            events.send(BottomEvent.ShowToast("There is no curve values"))
        } else {
            events.send(BottomEvent.LoadPpmAverageValues(dialog, tableFile.absolutePath))
        }
    }

    private fun generateDate(): String {
        val calendar = Calendar.getInstance()
        val builder = StringBuilder()
        builder.append(calendar[Calendar.YEAR])
        builder.append(normalizeValue(calendar[Calendar.MONTH] + 1))
        builder.append(normalizeValue(calendar[Calendar.DAY_OF_MONTH]))
        builder.append("_")
        builder.append(normalizeValue(calendar[Calendar.HOUR_OF_DAY]))
        builder.append(normalizeValue(calendar[Calendar.MINUTE]))
        builder.append(normalizeValue(calendar[Calendar.SECOND]))
        return builder.toString()
    }

    private fun normalizeValue(value: Int): String {
        require(!(value > 99 || value < 0))
        val builder = StringBuilder()
        if (value < 10) {
            builder.append(0)
        }
        builder.append(value)
        return builder.toString()
    }

    private fun detectPpmFromName(file: File): Int {
        var fileName = file.name
        val revisionPrefix = "_R"
        var index = fileName.lastIndexOf(revisionPrefix)
        if (index == -1) {
            return -1
        }
        val endString = fileName.substring(index + revisionPrefix.length)
        val csvPostfix = ".csv"
        if (endString.indexOf(csvPostfix) == -1) {
            return -1
        }
        try {
            endString.substring(0, endString.indexOf(csvPostfix)).toInt()
        } catch (e: NumberFormatException) {
            return -1
        }
        fileName = fileName.substring(0, index)
        index = fileName.lastIndexOf('_')
        return if (index == -1) {
            -1
        } else try {
            fileName.substring(index + 1).toInt()
        } catch (e: NumberFormatException) {
            -1
        }
    }

    private fun deleteAllInside(root: File) {
        if (!root.isDirectory) {
            root.delete()
        } else for (file in root.listFiles()) {
            deleteAllInside(file)
        }
    }

    fun calculatePpmAuto(selectedDirectory: File) = viewModelScope.launch(Dispatchers.IO) {
        mDoPostLoadingCalculations = true
        events.send(BottomEvent.ShowProgress)
        val mCurveFiles = SparseArray<MutableList<File>>()

        val folderWithCalibrationFiles: File = selectedDirectory
        var filesInside = folderWithCalibrationFiles.listFiles()
        var folderWithCurve: File? = null
        val ppmFiles: MutableList<File> = java.util.ArrayList(
            filesInside!!.size
        )
        for (file in filesInside) {
            if (file.isDirectory) {
                if (file.name.contains(Constants.CALIBRATION_CURVE_NAME)) {
                    folderWithCurve = file
                    break
                }
            } else {
                val index = detectPpmFromName(file)
                if (index != -1) {
                    if (mCurveFiles.get(index) == null) {
                        mCurveFiles.put(index, java.util.ArrayList<File>())
                    }
                    mCurveFiles.get(index).add(file)
                }
                ppmFiles.add(file)
            }
        }

        if (folderWithCurve != null) {
            filesInside = folderWithCurve.listFiles()
            if (filesInside != null && filesInside.size != 0) {
                var newestFile: File? = null
                for (f in filesInside) {
                    if (!f.isDirectory) {
                        if (newestFile == null) {
                            newestFile = f
                        } else if (f.lastModified() > newestFile.lastModified()) {
                            newestFile = f
                        }
                    }
                }
                if (newestFile != null) {
                    events.send(BottomEvent.ChangeProgress(100))
                    loadGraphData(newestFile)
                    return@launch
                } else {
                    deleteAllInside(folderWithCurve)
                }
            }
        } else {
            folderWithCurve = File(folderWithCalibrationFiles, Constants.CALIBRATION_CURVE_NAME)
        }

        folderWithCurve.mkdir()

        var countFilesForProcess = 0

        for (i in 0 until mCurveFiles.size()) {
            countFilesForProcess += mCurveFiles.valueAt(i).size
        }

        val ppmValues: MutableList<Float> = java.util.ArrayList()
        val averageValues: MutableList<List<Float>> = java.util.ArrayList()

        var countOperationsProcessed = 0

        for (i in 0 until mCurveFiles.size()) {
            val ppmValue: Int = mCurveFiles.keyAt(i)
            ppmValues.add(ppmValue.toFloat())
            val curveSquares: MutableList<Float> = java.util.ArrayList()
            averageValues.add(curveSquares)
            val curveFiles: List<File> = mCurveFiles.valueAt(i)
            for (file in curveFiles) {
                val square = file.calculateSquare()
                if (square < 0f) {
                    events.send(
                        BottomEvent.ShowToast(
                            message = "Creating calibration curve... Chart #$ppmValue can not be calculated. Please rerecord it."
                        )
                    )
                    delay(MIN_PROGRESS_TIME)
                    events.send(BottomEvent.HideProgress)
                    events.send(
                        BottomEvent.ShowToast("There is no curve values")
                    )
                    return@launch
                }
                curveSquares.add(square)
                countOperationsProcessed++
                delay(PROGRESS_DELAY)
                events.send(
                    BottomEvent.ChangeProgress(
                        progress = (countOperationsProcessed / countFilesForProcess.toFloat() * 100).toInt()
                    )
                )
            }
        }

        val tableFile = File(
            folderWithCurve, "${Constants.CALIBRATION_CURVE_NAME}_${generateDate()}.csv"
        )

        val isSaveSucceeded = try {
            tableFile.createNewFile()
            saveAvgValuesToFile(ppmValues, averageValues, tableFile.absolutePath, true)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }

        if (!isSaveSucceeded) {
            delay(PROGRESS_DELAY)
            events.send(BottomEvent.HideProgress)
            events.send(BottomEvent.ShowToast("There is no curve values"))
            return@launch
        } else {
            events.send(BottomEvent.LoadPpmAverageValues(dialog = null, tableFile.absolutePath))
        }
    }

    fun loadGraphData(file: File) = viewModelScope.launch(Dispatchers.IO) {
        val res = file.absolutePath.parseGraphData()
        delay(MIN_PROGRESS_TIME)
        events.send(BottomEvent.HideProgress)
        if (res == null) {
            events.send(BottomEvent.ShowToast("You select wrong file"))
        } else {
            events.send(BottomEvent.GraphDataLoaded(res.first, res.second, file.absolutePath))
        }
    }
}