package com.ismet.usbterminal.mainscreen.powercommands

import android.app.Dialog
import android.os.Handler
import android.view.View
import android.view.Window
import android.widget.TextView
import com.ismet.usbterminal.MainActivity
import com.ismet.usbterminal.data.PowerCommand
import com.ismet.usbterminal.data.PowerState
import com.ismet.usbterminalnew.R

abstract class PowerCommandsFactory {
    var coolingDialog: Dialog? = null
        private set

    abstract fun moveStateToNext(): Boolean
    abstract fun nextPowerState(): PowerState
    abstract fun currentCommand(): PowerCommand?
    abstract fun currentPowerState(): PowerState

    fun sendRequest(activity: MainActivity, mHandler: Handler) {
        var powerState = currentPowerState()
        when (powerState) {
            PowerState.OFF_INTERRUPTING -> {
                val message = mHandler.obtainMessage()
                message.what = MainActivity.MESSAGE_INTERRUPT_ACTIONS
                message.sendToTarget()
            }
            PowerState.OFF_WAIT_FOR_COOLING -> {
                activity.waitForCooling()
                activity.dismissProgress()
                coolingDialog = Dialog(activity).apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    setContentView(R.layout.layout_cooling)
                    window!!.setBackgroundDrawableResource(android.R.color.transparent)
                    (findViewById<View>(R.id.text) as TextView).text =
                        """  Cooling down.  Do not switch power off.  Please wait . . . ! ! !    
System will turn off automaticaly."""
                    setCancelable(false)
                    show()
                }
            }
            else -> {
                val currentCommand = currentCommand()
                if (currentCommand != null) {
                    activity.deliverCommand(currentCommand.command)
                    if (!currentCommand.hasSelectableResponses()) {
                        powerState = nextPowerState()
                        if (powerState !== PowerState.OFF && powerState !== PowerState.ON) {
                            mHandler.postDelayed({
                                val currentCommandNew = currentCommand()
                                var currentState = currentPowerState()
                                if (currentState !== PowerState.OFF && currentState !==
                                    PowerState.ON
                                ) {
                                    if (currentCommand == currentCommandNew) {
                                        moveStateToNext()
                                        currentState = currentPowerState()
                                        if (currentState !== PowerState.OFF && currentState !== PowerState.ON) {
                                            sendRequest(activity, mHandler)
                                        }
                                    }
                                }
                            }, currentCommand.delay)
                        }
                    }
                }
            }
        }
    }
}