package com.ismet.usb;

interface UsbHost {
    void getFromUsb(inout byte[] values);
}