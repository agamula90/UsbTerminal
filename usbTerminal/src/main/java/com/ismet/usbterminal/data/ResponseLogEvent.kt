package com.ismet.usbterminal.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ResponseLogEvent(val request: String, val response: String)
