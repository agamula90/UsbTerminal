package com.ismet.usbterminal

import com.ismet.usbterminal.data.Command
import com.ismet.usbterminal.utils.GraphData

sealed class MainEvent {
    class ShowToast(val message: String): MainEvent()
    class WriteToUsb(val data: Command): MainEvent()
    object InvokeAutoCalculations: MainEvent()
    object IncReadingCount: MainEvent()
    object SendCommandsFromEditor: MainEvent()
    object ClearEditor: MainEvent()
    object ClearOutput: MainEvent()
    object ClearData: MainEvent()
    object DismissCoolingDialog: MainEvent()
    object IncCountMeasure: MainEvent()
    class SetReadingCount(val value: Int): MainEvent()
    class UpdateGraphData(val graphData: GraphData): MainEvent()
    class ShowWaitForCoolingDialog(val message: String): MainEvent()
    class ShowCorruptionDialog(val message: String): MainEvent()
}
