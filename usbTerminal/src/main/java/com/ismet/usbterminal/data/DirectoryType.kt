package com.ismet.usbterminal.data

import android.os.Environment
import java.io.File

enum class DirectoryType(val encodedName: String) {
    MEASUREMENT("MES"),
    CALCULATIONS("CAL"),
    REPORT("Report"),
    APPLICATION_SETTINGS("SYS");

    fun getDirectory() = File(Environment.getExternalStorageDirectory(), "AEToC_${encodedName}_Files")
}