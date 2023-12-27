package com.ismet.usbterminal

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.ismet.usb.UsbEmitter
import com.ismet.usb.UsbHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class UsbHostService : Service() {
    @Inject
    lateinit var usbEmitter: UsbEmitter

    private val binder = object: UsbHost.Stub() {
        override fun getFromUsb(values: ByteArray?) {
            usbEmitter.readEvents.trySend(values)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
}