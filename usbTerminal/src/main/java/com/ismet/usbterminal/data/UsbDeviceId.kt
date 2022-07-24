package com.ismet.usbterminal.data

// Maybe (0x1d6b, 0x0001) or (0x1d6b, 0x0002) or (0x1d6b, 0x0003)
data class UsbDeviceId(
    val vendorId: Int,
    val productId: Int
)
