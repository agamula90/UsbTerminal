package com.ismet.usbterminal;

import android.hardware.usb.UsbDevice;

public interface UsbServiceWritable {
	void writeToUsb(byte bytes[]);
	UsbDevice searchForUsbDevice();
	void disconnect();
}
