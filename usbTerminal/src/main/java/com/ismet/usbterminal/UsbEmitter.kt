package com.ismet.usbterminal

import kotlinx.coroutines.channels.Channel

class UsbEmitter {
    val readEvents = Channel<ByteArray?>(Channel.UNLIMITED)
}