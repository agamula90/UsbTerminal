package com.ismet.usb;

interface UsbHost {
    void getFromUsb(out byte[] values);
}