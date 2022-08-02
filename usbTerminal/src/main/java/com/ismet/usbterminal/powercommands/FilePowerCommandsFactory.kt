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

    override fun moveStateToNext() {
        val newState = getNextPowerState()
        changeIndexInRunning()
        powerState = newState
    }

    override fun nextPowerState(): PowerState = getNextPowerState()

    private fun getNextPowerState() = when {
        powerState == PowerState.ON -> {
            val powerCommand = offCommands.valueAt(0)
            when (powerCommand.command.toString()) {
                START_COOLING -> PowerState.OFF_WAIT_FOR_COOLING
                INTERRUPT_SOFTWARE_ACTIONS -> PowerState.OFF_INTERRUPTING
                else -> PowerState.OFF_RUNNING
            }
        }
        powerState in arrayOf(PowerState.OFF_RUNNING, PowerState.OFF_INTERRUPTING, PowerState.OFF_WAIT_FOR_COOLING) && indexInRunning == offCommands.size() - 1 -> {
            PowerState.OFF
        }
        powerState in arrayOf(PowerState.OFF_RUNNING, PowerState.OFF_INTERRUPTING, PowerState.OFF_WAIT_FOR_COOLING) -> {
            val powerCommand = currentCommand()
            when(powerCommand?.command?.toString()) {
                null -> PowerState.OFF_RUNNING
                START_COOLING -> PowerState.OFF_WAIT_FOR_COOLING
                INTERRUPT_SOFTWARE_ACTIONS -> PowerState.OFF_INTERRUPTING
                else -> PowerState.OFF_RUNNING
            }
        }
        powerState == PowerState.OFF -> PowerState.ON_RUNNING
        powerState == PowerState.ON_RUNNING && indexInRunning == onCommands.size() - 1 -> {
            PowerState.ON
        }
        powerState == PowerState.INITIAL -> PowerState.OFF
        else -> powerState
    }

    private fun changeIndexInRunning() {
        indexInRunning = when(powerState) {
            PowerState.ON, PowerState.OFF -> 0
            PowerState.OFF_RUNNING, PowerState.OFF_INTERRUPTING, PowerState.OFF_WAIT_FOR_COOLING -> {
                if (indexInRunning == offCommands.size() - 1) 0 else indexInRunning + 1
            }
            PowerState.ON_RUNNING -> {
                if (indexInRunning == onCommands.size() - 1) 0 else indexInRunning + 1
            }
            else -> indexInRunning
        }
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