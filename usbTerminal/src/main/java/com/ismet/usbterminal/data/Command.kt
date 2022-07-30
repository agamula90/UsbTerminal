package com.ismet.usbterminal.data

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class Command(val text: String): Parcelable {

    @IgnoredOnParcel
    val byteArray: ByteArray = when(text) {
        "", "\n" -> ByteArray(0)
        "\r" -> "\r".toByteArray()
        else -> {
            val tempText = text
                .replace("\r", "")
                .replace("\n", "")
                .trim { it <= ' ' }
            if (tempText.contains("(") && tempText.contains(")")) {
                val arr = tempText
                    .replace("(", "")
                    .replace(")", "")
                    .trim { it <= ' ' }
                    .split("-")
                    .toTypedArray()
                val bytes = ByteArray(arr.size)
                for (j in bytes.indices) {
                    bytes[j] = arr[j].toInt(16).toByte()
                }
                bytes
            } else {
                text.toByteArray() + "\r".toByteArray()
            }
        }
    }

    override fun toString(): String = when(text) {
        "", "\n", "\r" -> ""
        else -> {
            "Tx: " + text
                .replace("\r", "")
                .replace("\n", "")
                .trim { it <= ' ' }
                .replace("(", "")
                .replace(")", "")
                .trim { it <= ' ' } + "\n"
        }
    }
}