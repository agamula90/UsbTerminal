package com.ismet.usbterminal.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class PowerCommand(val command: Command, val delay: Long, val possibleResponses: Array<String> = emptyArray()): Parcelable {
    fun hasSelectableResponses(): Boolean {
        return possibleResponses.isNotEmpty()
    }

    fun isResponseCorrect(response: String): Boolean {
        return possibleResponses.isEmpty() || listOf(*possibleResponses).contains(response)
    }
}