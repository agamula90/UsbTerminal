package com.ismet.usbterminal.main.bottom

import android.content.DialogInterface

sealed class BottomEvent {
    object ShowProgress : BottomEvent()
    object HideProgress : BottomEvent()
    class LoadPpmAverageValues(val dialog: DialogInterface?, val filePath: String) : BottomEvent()
    class ShowToast(val message: String) : BottomEvent()
    class ChangeProgress(val progress: Int) : BottomEvent()
    class GraphDataLoaded(
        val ppmValues: List<Float>,
        val avgSquareValues: List<Float>,
        val filePath: String
    ) : BottomEvent()
}