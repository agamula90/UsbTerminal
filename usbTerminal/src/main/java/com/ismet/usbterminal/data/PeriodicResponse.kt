package com.ismet.usbterminal.data

sealed class PeriodicResponse {
    class Temperature(val response: String, val value: Int): PeriodicResponse() {
        override fun toString() = response
    }
    class Co2(val response: String, val value: Int): PeriodicResponse() {
        override fun toString() = "${response}\nCO2: $value ppm"
    }
}