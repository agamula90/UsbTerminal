package com.ismet.usbterminal.powercommands

import com.ismet.usbterminal.CO2_REQUEST
import com.ismet.usbterminal.data.Command
import com.ismet.usbterminal.data.PowerCommand
import com.ismet.usbterminal.data.PowerState

class LocalPowerCommandsFactory(
    private var powerState: PowerState
) : PowerCommandsFactory() {
    private val onCommands: Map<PowerState, PowerCommand> = mutableMapOf<PowerState, PowerCommand>().apply {
        this[PowerState.ON_STAGE1] = PowerCommand(Command("/5J1R"), 1000)
        this[PowerState.ON_STAGE1_REPEAT] = PowerCommand(Command("/5J1R"), 1000)
        this[PowerState.ON_STAGE2B] = PowerCommand(Command(
            "/5J1R"), 1000, arrayOf("@5J101 ",
            "@5J001 ")
        )
        this[PowerState.ON_STAGE2] = PowerCommand(Command("/5J5R"), 1000, arrayOf("@5J101 "))
        this[PowerState.ON_STAGE3A] = PowerCommand(Command("/5H0000R"), 500)
        this[PowerState.ON_STAGE3B] = PowerCommand(Command("/5H0000R"), 500)
        this[PowerState.ON_STAGE3] = PowerCommand(Command(CO2_REQUEST), 2000)
        this[PowerState.ON_STAGE4] = PowerCommand(Command("/1ZR"), 0)
    }
    private val offCommands: Map<PowerState, PowerCommand> = mutableMapOf<PowerState, PowerCommand>().apply {
        this[PowerState.OFF_STAGE1] = PowerCommand(Command("/5H0000R"), 1000)
        this[PowerState.OFF_FINISHING] = PowerCommand(Command("/5J1R"), 1000)
        this[PowerState.OFF_WAIT_FOR_COOLING] = PowerCommand(Command("/5H0000R"), 1000)
    }
    
    override fun moveStateToNext() {
        powerState = getNextPowerState()
    }

    private fun getNextPowerState() = when (powerState) {
        PowerState.ON_STAGE1 -> PowerState.ON_STAGE1_REPEAT
        PowerState.ON_STAGE1_REPEAT -> PowerState.ON_STAGE2B
        PowerState.ON_STAGE2B -> PowerState.ON_STAGE2
        PowerState.ON_STAGE2 -> PowerState.ON_STAGE3A
        PowerState.ON_STAGE3A -> PowerState.ON_STAGE3B
        PowerState.ON_STAGE3B -> PowerState.ON_STAGE3
        PowerState.ON_STAGE3 -> PowerState.ON_STAGE4
        PowerState.ON_STAGE4 -> PowerState.ON
        PowerState.ON -> PowerState.OFF_INTERRUPTING
        PowerState.OFF_INTERRUPTING -> PowerState.OFF_STAGE1
        PowerState.OFF_STAGE1 -> PowerState.OFF_WAIT_FOR_COOLING
        PowerState.OFF_WAIT_FOR_COOLING -> PowerState.OFF_FINISHING
        PowerState.OFF_FINISHING -> PowerState.OFF
        PowerState.OFF -> PowerState.ON_STAGE1
        PowerState.INITIAL -> PowerState.OFF
        else -> powerState
    }

    override fun nextPowerState(): PowerState = getNextPowerState()

    override fun currentCommand(): PowerCommand? = onCommands[powerState] ?: offCommands[powerState]

    override fun currentPowerState(): PowerState {
        return powerState
    }

    override fun toString() = "DefaultPowerCommand"
}