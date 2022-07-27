package com.ismet.usbterminal.mainscreen.tasks

sealed class MainEvent {
    class ShowToast(val message: String): MainEvent()
    class WriteToUsb(val data: String): MainEvent()
    class ChangeSubDirDate(val subDirDate: String): MainEvent()
    object InvokeAutoCalculations: MainEvent()
    class UpdateTimerRunning(val isRunning: Boolean): MainEvent()
    object IncReadingCount: MainEvent()
}
