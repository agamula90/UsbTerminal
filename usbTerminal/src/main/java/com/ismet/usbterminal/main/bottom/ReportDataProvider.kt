package com.ismet.usbterminal.main.bottom

import java.text.DateFormat
import java.util.Date

interface ReportDataProvider {
    fun initReport()
    fun countMinutes(): Int
    fun volume(): Int
    fun reportDate(): Date
    fun dateFormat(): DateFormat
}