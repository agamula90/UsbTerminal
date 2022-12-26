package com.ismet.usbterminal.data

import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import java.io.Closeable

class UsbDevice(
    val deviceId: UsbDeviceId,
    private val connection: UsbDeviceConnection,
    private var serialPort: UsbSerialDevice?
): Closeable {
    var readCallback = OnDataReceivedCallback { data -> Log.d(TAG, data.decodeToString()) }

    private val usbReadCallback = UsbSerialInterface.UsbReadCallback {
        readCallback.onDataReceived(it)
    }

    init {
        val tempSerialPort = serialPort
        if (tempSerialPort != null) {
            if (tempSerialPort.open()) {
                tempSerialPort.setBaudRate(BAUD_RATE)
                tempSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8)
                tempSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1)
                tempSerialPort.setParity(UsbSerialInterface.PARITY_NONE)
                tempSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                tempSerialPort.read(usbReadCallback)
            } else {
                tempSerialPort.close()
                serialPort = null
            }
        }
    }

    fun isConnectionEstablished() = serialPort != null

    fun write(bytes: ByteArray) {
        serialPort?.write(bytes)
    }

    override fun close() {
        try {
            serialPort?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        connection.close()
    }

    companion object {
        private const val BAUD_RATE = 9600 // BaudRate. Change this value if you need
        private const val TAG = "OpenedUsbDevice"
    }
}

fun interface OnDataReceivedCallback {
    fun onDataReceived(data: ByteArray)
}