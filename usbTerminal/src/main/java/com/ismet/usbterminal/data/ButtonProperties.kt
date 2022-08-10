package com.ismet.usbterminal.data

import android.os.Environment
import android.os.Parcelable
import androidx.annotation.DrawableRes
import com.ismet.usbterminal.APPLICATION_SETTINGS
import com.ismet.usbterminal.BUTTON_FILE_FORMAT_DELIMITER
import com.ismet.usbterminalnew.R
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class ButtonProperties(
    val savable: FileSavable,
    val alpha: Float,
    @DrawableRes val background: Int,
    val isActivated: Boolean,
    val isEnabled: Boolean
): Parcelable {
    companion object {
        fun getButtonChangeable(savable: FileSavable): ButtonProperties {
            return ButtonProperties(
                alpha = 1f,
                savable = savable,
                background = R.drawable.button_drawable,
                isActivated = false,
                isEnabled = false
            )
        }

        fun getButtonStatic(text: String, command: String, fileName: String): ButtonProperties {
            return ButtonProperties(
                alpha = 1f,
                savable = FileSavable(
                    text = text,
                    command = command,
                    activatedText = text,
                    activatedCommand = command,
                    fileName = fileName
                ),
                background = R.drawable.button_drawable,
                isActivated = false,
                isEnabled = false
            )
        }

        fun forPower() = ButtonProperties(
            alpha = 1f,
            savable = FileSavable(
                text = "power on",
                command = "",
                activatedText = "power on",
                activatedCommand = ""
            ),
            background = R.drawable.button_drawable,
            isActivated = false,
            isEnabled = false
        )

        fun forMeasure() = ButtonProperties(
            alpha = 1f,
            savable = FileSavable(
                text = "Measure",
                command = "",
                activatedText = "Measure",
                activatedCommand = ""
            ),
            background = R.drawable.button_drawable,
            isActivated = false,
            isEnabled = false
        )

        fun forSend() = ButtonProperties(
            alpha = 1f,
            savable = FileSavable(
                text = "Send",
                command = "",
                activatedText = "Send",
                activatedCommand = ""
            ),
            background = R.drawable.button_drawable,
            isActivated = false,
            isEnabled = false
        )
    }
}

@Parcelize
class FileSavable(
    val text: String,
    val command: String,
    val activatedText: String,
    val activatedCommand: String,
    val fileName: String = ""
): Parcelable {
    fun isValid() = activatedText.isNotEmpty() && text.isNotEmpty()

    fun withName(fileName: String): FileSavable {
        return FileSavable(text, command, activatedText, activatedCommand, fileName)
    }

    fun save(ignoreActivated: Boolean) {
        val listToBeingSave = when(ignoreActivated) {
            true -> listOf(text, command)
            false -> listOf(text, activatedText, command, activatedCommand)
        }
        val content = listToBeingSave.joinToString(separator = BUTTON_FILE_FORMAT_DELIMITER)
        File(File(Environment.getExternalStorageDirectory(), APPLICATION_SETTINGS), fileName).writeText(content)
    }
}
