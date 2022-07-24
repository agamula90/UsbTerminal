package com.ismet.usbterminal.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import com.felhr.usbserial.UsbSerialDevice
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable

class UsbDeviceConnection(val context: Context): Closeable {
    private val continuations = mutableMapOf<UsbDeviceId, List<CancellableContinuation<UsbDevice>>>()

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val usbDeviceReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras!!
            val granted = extras.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
            val device = extras.getParcelable<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)!!

            when(granted) {
                true -> {
                    continuations[UsbDeviceId(device.vendorId, device.productId)]!!.forEach {
                        val connection = usbManager.openDevice(device)
                        val serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection)
                        it.resumeWith(Result.success(UsbDevice(connection, serialPort)))
                    }
                }
                false -> {
                    continuations[UsbDeviceId(device.vendorId, device.productId)]!!.forEach {
                        it.resumeWith(Result.failure(CreateDeviceException(ConnectionFailureReason.PERMISSION_NOT_GRANTED)))
                    }
                }
            }
        }
    }

    init {
        context.registerReceiver(usbDeviceReceiver, IntentFilter(USB_PERMISSION))
    }

    private suspend fun createDevice(deviceId: UsbDeviceId): UsbDevice = suspendCancellableCoroutine { continuation ->
        val usbDevices = usbManager.deviceList
        val usbDevice = usbDevices.values.firstOrNull { it.vendorId == deviceId.vendorId && it.productId == deviceId.productId }
        when (usbDevice) {
            null -> continuation.resumeWith(Result.failure(CreateDeviceException(ConnectionFailureReason.DEVICE_NOT_FOUND)))
            else -> {
                continuations[deviceId] = (continuations[deviceId] ?: emptyList()) + continuation
                val mPendingIntent = PendingIntent.getBroadcast(context, 0, Intent(USB_PERMISSION), 0)
                usbManager.requestPermission(usbDevice, mPendingIntent)
            }
        }
    }

    suspend fun findUsbDevice(): UsbDevice {
        for (deviceId in supportedDeviceIds) {
            try {
                return createDevice(deviceId)
            } catch (e: CreateDeviceException) {
                if (e.reason != ConnectionFailureReason.DEVICE_NOT_FOUND) throw e
            }
        }
        throw CreateDeviceException(ConnectionFailureReason.DEVICE_NOT_FOUND)
    }

    companion object {
        private const val USB_PERMISSION = "com.ismet.usb.permission"

        val supportedDeviceIds = listOf(
            UsbDeviceId(vendorId = 0x1a86, productId = 0x7523),
            //UsbDeviceId(vendorId = 0x1d6b, productId = 0x0001),
            //UsbDeviceId(vendorId = 0x1d6b, productId = 0x0002),
            //UsbDeviceId(vendorId = 0x1d6b, productId = 0x0003),
        )
    }

    override fun close() {
        context.unregisterReceiver(usbDeviceReceiver)
    }
}

enum class ConnectionFailureReason {
    DEVICE_NOT_FOUND, PERMISSION_NOT_GRANTED
}
class CreateDeviceException(val reason: ConnectionFailureReason): Exception()