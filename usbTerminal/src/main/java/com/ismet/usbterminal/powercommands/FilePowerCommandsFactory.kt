package com.ismet.usbterminal.powercommands

import android.util.SparseArray
import com.ismet.usbterminal.data.PowerCommand
import com.ismet.usbterminal.data.PowerState

class FilePowerCommandsFactory(
    private var powerState: PowerState,
    private val onCommands: SparseArray<PowerCommand>,
    private val offCommands: SparseArray<PowerCommand>
) : PowerCommandsFactory() {
    
    var indexInRunning = 0
        private set

    override fun moveStateToNext(): Boolean {
        val isFinalState: Boolean
        when (powerState) {
            PowerState.ON -> {
                indexInRunning = 0
                val powerCommand = offCommands.valueAt(0)
                powerState = when (powerCommand.command.toString()) {
                    START_COOLING -> PowerState.OFF_WAIT_FOR_COOLING
                    INTERRUPT_SOFTWARE_ACTIONS -> PowerState.OFF_INTERRUPTING
                    else -> PowerState.OFF_RUNNING
                }
                if (indexInRunning == offCommands.size()) {
                    isFinalState = true
                    powerState = PowerState.OFF
                } else {
                    isFinalState = false
                }
            }
            PowerState.OFF_RUNNING, PowerState.OFF_INTERRUPTING, PowerState.OFF_WAIT_FOR_COOLING -> {
                indexInRunning++
                if (indexInRunning == offCommands.size()) {
                    isFinalState = true
                    indexInRunning = 0
                    powerState = PowerState.OFF
                    return isFinalState
                } else {
                    isFinalState = false
                }
                val powerCommand = currentCommand()
                powerState = when(powerCommand?.command?.toString()) {
                    null -> PowerState.OFF_RUNNING
                    START_COOLING -> PowerState.OFF_WAIT_FOR_COOLING
                    INTERRUPT_SOFTWARE_ACTIONS -> PowerState.OFF_INTERRUPTING
                    else -> PowerState.OFF_RUNNING
                }
            }
            PowerState.OFF -> {
                indexInRunning = 0
                if (indexInRunning == onCommands.size()) {
                    isFinalState = true
                    powerState = PowerState.ON
                } else {
                    powerState = PowerState.ON_RUNNING
                    isFinalState = false
                }
            }
            PowerState.ON_RUNNING -> {
                indexInRunning++
                if (indexInRunning == onCommands.size()) {
                    isFinalState = true
                    indexInRunning = 0
                    powerState = PowerState.ON
                } else {
                    isFinalState = false
                }
            }
            PowerState.PRE_LOOPING -> {
                powerState = PowerState.OFF
                isFinalState = false
            }
            else -> isFinalState = false
        }
        return isFinalState
    }

    override fun nextPowerState(): PowerState {
        if (currentPowerState() == PowerState.PRE_LOOPING) {
            return PowerState.OFF
        }
        val curState = currentPowerState()
        moveStateToNext()
        val newState = currentPowerState()
        if (newState == PowerState.OFF) {
            indexInRunning = offCommands.size() - 1
        } else if (newState == PowerState.ON) {
            indexInRunning = onCommands.size() - 1
        } else {
            indexInRunning--
            if (indexInRunning == -1) {
                indexInRunning = 0
            }
        }
        powerState = curState
        return newState
    }

    override fun currentCommand(): PowerCommand? = when (powerState) {
        PowerState.ON_RUNNING, PowerState.ON -> onCommands.valueAt(indexInRunning)
        PowerState.OFF_INTERRUPTING, PowerState.OFF_RUNNING, PowerState.OFF_WAIT_FOR_COOLING -> offCommands.valueAt(indexInRunning)
        else -> null
    }

    override fun currentPowerState(): PowerState {
        return powerState
    }

    override fun toString(): String = "FilePowerCommand"

    companion object {
        const val START_COOLING = "Cooling"
        const val INTERRUPT_SOFTWARE_ACTIONS = "InterruptActions"
    }
}