package com.ismet.usbterminal.data

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
class AccessorySettings(
    val borderTemperature: Int,
    val ping: Ping,
    val on: On,
    val temperature: String,
    val co2: String,
    val syncPeriod: Int,
    val off: Off,
    val temperatureUiOffset: Int
): Parcelable {
    companion object {
        fun getDefault() = AccessorySettings(
            borderTemperature = 200,
            ping = Ping("/5J1R", 300),
            on = On("/5J5R", listOf("@5J101 ")),
            temperature = "/5H750R",
            co2 = "(FE-44-00-08-02-9F-25)",
            temperatureUiOffset = 0,
            syncPeriod = 2000,
            off = Off("/5J1R", coolingPeriod = 1000)
        )
    }
}

@JsonClass(generateAdapter = true)
@Parcelize
class Ping(val command: String, val delay: Int): Parcelable

@Parcelize
@JsonClass(generateAdapter = true)
class On(
    val command: String,
    val acceptedResponses: List<String>
): Parcelable

@Parcelize
@JsonClass(generateAdapter = true)
class Off(
    val command: String,
    val coolingPeriod: Int? = null
): Parcelable