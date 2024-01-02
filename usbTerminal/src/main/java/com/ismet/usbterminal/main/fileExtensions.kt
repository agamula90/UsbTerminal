package com.ismet.usbterminal.main

import android.content.Intent
import android.os.Environment
import com.ismet.storage.DirectoryType
import com.ismet.usbterminal.format
import com.ismet.usbterminalnew.R
import com.ismet.usbterminal.main.data.AvgPoint
import fr.xgouchet.FileDialog
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

val BASE_DIRECTORY = File(
    Environment.getExternalStorageDirectory(),
    "AEToC_CAL_FILES"
).also { it.mkdirs() }

const val CSV_COL_DELiM = ","

fun File.findNewestDirectory(directoryName: String): File {
    val filesInside = listFiles()
    if (filesInside.isNullOrEmpty()) {
        return getSubdirectoryOfTypeDirectoryTypeByName(directoryName)
    }

    var newestFile: File? = null

    for (i in filesInside.indices) {
        if (!filesInside[i].isDirectory || !filesInside[i].name.contains(directoryName)) continue
        if (newestFile == null) {
            newestFile = filesInside[i]
        } else if (filesInside[i].lastModified() > newestFile.lastModified()) {
            newestFile = filesInside[i]
        }
    }

    return newestFile ?: getSubdirectoryOfTypeDirectoryTypeByName(directoryName)
}

private fun File.getSubdirectoryOfTypeDirectoryTypeByName(directoryName: String): File {
    val directoryTypeByName = DirectoryType.values().first { it.encodedName == directoryName }
    return File(this, directoryTypeByName.getDirectoryName()).also { it.mkdirs() }
}

fun File.findFirstDirectoryByName(directoryName: String): File? = listFiles { dir, filename ->
    val fileName = File(dir, filename)
    fileName.isDirectory && filename.contains(directoryName)
}?.firstOrNull()

fun File.findCalDirectory() = findFirstDirectoryByName("CAL")

fun File.findMesDirectory() = findFirstDirectoryByName("MES")

fun Intent.setCustomDirectoryDrawables() {
    putExtra(FileDialog.FOLDER_DRAWABLE_RESOURCE, R.drawable.folder)
    putExtra(FileDialog.FILE_DRAWABLE_RESOURCE, R.drawable.file)
}

fun saveAvgValuesToFile(ppmValues: List<Float>, squareValues: List<List<Float>>, path: String, save0Ppm: Boolean): Boolean {
    return try {
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(path)))
        if (save0Ppm) {
            writer.write("0")
            writer.write(CSV_COL_DELiM)
            for (i in 0..3) {
                writer.write(CSV_COL_DELiM)
            }
            writer.write("0")
            writer.newLine()
        }
        for (i in ppmValues.indices) {
            writer.write(ppmValues[i].toInt().toString() + "")
            writer.write(CSV_COL_DELiM)
            var squareVas = squareValues[i]
            if (squareVas.size > 4) {
                squareVas = squareVas.subList(0, 4)
            }
            for (squareVal in squareVas) {
                if (squareVal != 0f) {
                    writer.write(squareVal.format())
                }
                writer.write(CSV_COL_DELiM)
            }
            val avgValue = AvgPoint(squareValues[i])
                .avg()
            if (squareVas.size < 4) {
                val countSquares = squareVas.size
                for (j in 0 until 4 - countSquares) {
                    if (avgValue != 0f) {
                        writer.write(avgValue.format())
                    }
                    writer.write(CSV_COL_DELiM)
                }
            }
            writer.write(avgValue.format())
            writer.newLine()
        }
        writer.flush()
        writer.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun String.parseGraphData(): Pair<List<Float>, List<Float>>? {
    val ppmValues: MutableList<Float> = ArrayList()
    val avgSquareValues: MutableList<Float> = ArrayList()
    try {
        val reader = BufferedReader(InputStreamReader(FileInputStream(File(this))))
        var s = reader.readLine()
        while (s != null) {
            val splitValues = s.split(CSV_COL_DELiM.toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            if (splitValues.size != 6) {
                reader.close()
                return null
            }
            ppmValues.add(splitValues[0].toFloat())
            avgSquareValues.add(splitValues[splitValues.size - 1].toFloat())
            s = reader.readLine()
        }
        reader.close()
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        return null
    }
    return ppmValues to avgSquareValues
}