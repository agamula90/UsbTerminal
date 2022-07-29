package com.ismet.usbterminal

sealed class MainEvent {
    class ShowToast(val message: String): MainEvent()
    class WriteToUsb(val data: String): MainEvent()
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
