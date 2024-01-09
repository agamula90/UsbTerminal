package com.ismet.usbterminal.main

import android.text.Editable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ismet.usbterminal.data.PeriodicResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.xgouchet.texteditor.undo.TextChangeWatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import java.net.URI
import javax.inject.Inject

@HiltViewModel
class SharedUiViewModel @Inject constructor(

): ViewModel() {
    private val events = Channel<UiEvent>()
    val eventsFlow = events.receiveAsFlow().shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        replay = 1
    )

    var isDirty = false

    var undoWatcher: TextChangeWatcher? = null
    var isInUndo = false

    var readingCount = 0

    var oldCountMeasure = 0
    var countMeasure = 0
        private set

    /**
     * the path of the file currently opened
     */
    var currentFilePath: String? = null

    /**
     * the name of the file currently opened
     */
    var currentFileName: String? = null
    var isReadOnly = false

    var isExportedChartLayoutEmpty = true

    var editorText: Editable? = null

    fun clearContent() {
        events.trySend(UiEvent.ClearContent)
    }

    fun incCountMeasure() {
        countMeasure++
    }

    fun refreshOldCountMeasure() {
        oldCountMeasure = countMeasure
    }

    fun resetCountMeasure() {
        oldCountMeasure = 0
        countMeasure = oldCountMeasure
    }

    fun openFile(path: String, forceReadOnly: Boolean) {
        events.trySend(UiEvent.OpenFile(path, forceReadOnly))
    }

    fun openFileFromURI(uri: URI, forceReadOnly: Boolean) {
        events.trySend(UiEvent.OpenFile(uriPath = uri, forceReadOnly = forceReadOnly))
    }

    fun openBackup(text: String) {
        events.trySend(UiEvent.OpenBackup(text))
    }

    fun setPeriodicResponse(periodicResponse: PeriodicResponse) {
        events.trySend(UiEvent.SetPeriodicResponse(periodicResponse))
    }

    fun setData(data: String) {
        events.trySend(UiEvent.SetDataReceived(data))
    }

    fun setEditorSelection(from: Int, to: Int) {
        events.trySend(UiEvent.SetEditorSelection(from, to))
    }

    fun hideProgress() {
        events.trySend(UiEvent.HideProgress)
    }
}