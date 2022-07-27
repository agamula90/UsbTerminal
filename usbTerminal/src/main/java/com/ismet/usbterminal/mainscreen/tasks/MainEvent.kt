package com.ismet.usbterminal.mainscreen.tasks

sealed class MainEvent {
    class ShowToast(val message: String): MainEvent()
}
