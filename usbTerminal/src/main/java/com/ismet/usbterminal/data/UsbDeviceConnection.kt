package com.ismet.usbterminal.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import java.io.Closeable

class UsbDeviceConnection(val context: Context): Closeable {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var device: UsbDevice? = null
    var permissionCallback = PermissionCallback { deviceId, device -> Log.d(TAG, "permission for $deviceId ${if (device != null) "granted" else "not granted"}") }

    private val usbDeviceReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras!!
            val granted = extras.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
            val platformDevice = extras.getParcelable<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)!!
            val deviceId = UsbDeviceId(platformDevice.vendorId, platformDevice.productId)

            when(granted) {
                true -> {
                    val connection = usbManager.openDevice(platformDevice)
                    val serialPort = UsbSerialDevice.createUsbSerialDevice(platformDevice, connection)
                    val newDevice = UsbDevice(deviceId, connection, serialPort)
                    device?.close()
                    device = newDevice
                    permissionCallback.onPermissionResult(deviceId, newDevice)
                }
                false -> {
                    if (device?.deviceId == deviceId) {
                        device?.close()
                        device = null
                    }
                    permissionCallback.onPermissionResult(deviceId, null)
                }
            }
        }
    }

    init {
        context.registerReceiver(usbDeviceReceiver, IntentFilter(USB_PERMISSION))
    }

    private fun findDevice(deviceId: UsbDeviceId) {
        val usbDevices = usbManager.deviceList
        val usbDevice = usbDevices.values.firstOrNull { it.vendorId == deviceId.vendorId && it.productId == deviceId.productId }
        when (usbDevice) {
            null -> throw FindDeviceException()
            else -> {
                val mPendingIntent = PendingIntent.getBroadcast(context, 0, Intent(USB_PERMISSION), 0)
                usbManager.requestPermission(usbDevice, mPendingIntent)
            }
        }
    }

    fun findDevice() {
        for (deviceId in supportedDeviceIds) {
            try {
                findDevice(deviceId)
            } catch (e: FindDeviceException) {
                // ignore
            }
        }
        throw FindDeviceException()
    }

    companion object {
        private const val USB_PERMISSION = "com.ismet.usb.permission"
        private const val TAG = "UsbDeviceConnection"

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

fun interface PermissionCallback {
    fun onPermissionResult(deviceId: UsbDeviceId, device: UsbDevice?)
}

class FindDeviceException : Exception()