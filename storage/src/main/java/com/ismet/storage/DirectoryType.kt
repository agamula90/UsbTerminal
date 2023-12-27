package com.ismet.storage

enum class DirectoryType(val encodedName: String) {
    MEASUREMENT("MES"),
    CALCULATIONS("CAL"),
    REPORT("Report"),
    APPLICATION_SETTINGS("SYS"),
    TEMPORARY("Temp");

    fun getDirectoryName(): String {
        return "AEToC_${encodedName}_Files"
    }
}