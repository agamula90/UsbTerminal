package com.ismet.usbterminal

sealed class MainEvent {
    class ShowToast(val message: String): MainEvent()
    class WriteToUsb(val command: String): MainEvent()
    object InvokeAutoCalculations: MainEvent()
    object IncReadingCount: MainEvent()
    object SendCommandsFromEditor: MainEvent()
    object ClearEditor: MainEvent()
    object ClearOutput: MainEvent()
    object ClearData: MainEvent()
    object DismissCoolingDialog: MainEvent()
    object IncCountMeasure: MainEvent()
    class SetReadingCount(val value: Int): MainEvent()
    class ShowWaitForCoolingDialog(val message: String): MainEvent()
    class ShowCorruptionDialog(val message: String): MainEvent()
    object DismissCorruptionDialog: MainEvent()
}
