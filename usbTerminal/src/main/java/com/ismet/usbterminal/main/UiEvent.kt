package com.ismet.usbterminal.main

import com.ismet.usbterminal.data.PeriodicResponse
import java.net.URI

sealed class UiEvent {
    object ViewModelInitialized: UiEvent()
    object ClearContent: UiEvent()

    //one of path/uriPath should be specified
    class OpenFile(
        val path: String = "",
        val forceReadOnly: Boolean,
        val uriPath: URI ?= null
    ): UiEvent()

    class OpenBackup(val text: String): UiEvent()
    class SetPeriodicResponse(val response: PeriodicResponse): UiEvent()
    class SetDataReceived(val data: String): UiEvent()
    class SetEditorSelection(val from: Int, val to: Int): UiEvent()

    object HideProgress: UiEvent()
}