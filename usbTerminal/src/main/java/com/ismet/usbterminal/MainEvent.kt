package com.ismet.usbterminal

import org.achartengine.chart.CombinedXYChart

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
    class SetCombinedChart(val combinedXYChart: CombinedXYChart): MainEvent()
    class ShowWaitForCoolingDialog(val message: String): MainEvent()
    class ShowCorruptionDialog(val message: String): MainEvent()
    object DismissCorruptionDialog: MainEvent()
}
