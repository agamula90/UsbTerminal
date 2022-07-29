package com.ismet.usbterminal.data

import android.content.SharedPreferences
import android.os.Parcelable
import androidx.annotation.DrawableRes
import com.ismet.usbterminalnew.R
import kotlinx.parcelize.Parcelize

@Parcelize
data class ButtonProperties(
    val alpha: Float,
    val text: String,
    val command: String,
    val activatedText: String,
    val activatedCommand: String,
    @DrawableRes val background: Int,
    val isActivated: Boolean,
    val isEnabled: Boolean
): Parcelable {
    companion object {
        fun byButtonIndex(prefs: SharedPreferences, index: Int, background: Int = R.drawable.button_drawable): ButtonProperties {
            val text = when(index) {
                0, 3 -> prefs.getString(PrefConstants.ON_NAME1, PrefConstants.ON_NAME_DEFAULT)!!
                1, 4 -> prefs.getString(PrefConstants.ON_NAME2, PrefConstants.ON_NAME_DEFAULT)!!
                2, 5 -> prefs.getString(PrefConstants.ON_NAME3, PrefConstants.ON_NAME_DEFAULT)!!
                else -> PrefConstants.ON_NAME_DEFAULT
            }
            val command = when(index) {
                0, 3 -> prefs.getString(PrefConstants.ON1, "")!!
                1, 4 -> prefs.getString(PrefConstants.ON2,"")!!
                2, 5 -> prefs.getString(PrefConstants.ON3,"")!!
                else -> ""
            }
            val activatedText = when(index) {
                0, 3 -> prefs.getString(PrefConstants.OFF_NAME1, PrefConstants.OFF_NAME_DEFAULT)!!
                1, 4 -> prefs.getString(PrefConstants.OFF_NAME2, PrefConstants.OFF_NAME_DEFAULT)!!
                2, 5 -> prefs.getString(PrefConstants.OFF_NAME3, PrefConstants.OFF_NAME_DEFAULT)!!
                else -> PrefConstants.OFF_NAME_DEFAULT
            }
            val activatedCommand = when(index) {
                0, 3 -> prefs.getString(PrefConstants.OFF1, "")!!
                1, 4 -> prefs.getString(PrefConstants.OFF2,"")!!
                2, 5 -> prefs.getString(PrefConstants.OFF3,"")!!
                else -> ""
            }
            return ButtonProperties(
                alpha = 1f,
                text = text,
                command = command,
                activatedText = activatedText,
                activatedCommand = activatedCommand,
                background = background,
                isActivated = false,
                isEnabled = true
            )
        }

        fun forPower() = ButtonProperties(
            alpha = 1f,
            text = "power on",
            command = "",
            activatedText = "power on",
            activatedCommand = "",
            background = R.drawable.button_drawable,
            isActivated = false,
            isEnabled = true
        )
    }
}

class PersistedInfo(
    val text: String,
    val command: String,
    val activatedText: String,
    val activatedCommand: String
) {
    fun isValid() = activatedText.isNotEmpty() && text.isNotEmpty() && activatedCommand.isNotEmpty() && command.isNotEmpty()
}
