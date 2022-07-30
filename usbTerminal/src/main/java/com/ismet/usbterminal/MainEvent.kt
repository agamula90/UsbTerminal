package com.ismet.usbterminal

import com.ismet.usbterminal.data.Command

sealed class MainEvent {
    class ShowToast(val message: String): MainEvent()
    class WriteToUsb(val data: Command): MainEvent()
    object InvokeAutoCalculations: MainEvent()
    class UpdateTimerRunning(val isRunning: Boolean): MainEvent()
    object IncReadingCount: MainEvent()
    object SendMessage: MainEvent()
    object ClearEditor: MainEvent()
    object ClearOutput: MainEvent()
    object ClearData: MainEvent()
    object SendRequest: MainEvent()
    class SendResponseToPowerCommandsFactory(val response: String): MainEvent()
    object DismissCoolingDialog: MainEvent()
    //class UpdateStartMeasureClickability(val isClickable: Boolean): MainEvent()
}
