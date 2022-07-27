package com.ismet.usbterminal.mainscreen.tasks

sealed class MainEvent {
    class ShowToast(val message: String): MainEvent()
    class WriteToUsb(val data: String): MainEvent()
}
