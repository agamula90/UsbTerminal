package com.ismet.usbterminal.powercommands

import com.ismet.usbterminal.data.PowerCommand
import com.ismet.usbterminal.data.PowerState

abstract class PowerCommandsFactory {
    abstract fun moveStateToNext(): Boolean
    abstract fun nextPowerState(): PowerState
    abstract fun currentCommand(): PowerCommand?
    abstract fun currentPowerState(): PowerState
}