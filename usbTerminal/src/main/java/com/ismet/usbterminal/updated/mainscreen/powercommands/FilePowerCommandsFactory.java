package com.ismet.usbterminal.updated.mainscreen.powercommands;

import android.util.Log;
import android.util.SparseArray;

import com.ismet.usbterminal.updated.data.PowerCommand;
import com.ismet.usbterminal.updated.data.PowerState;

public class FilePowerCommandsFactory extends PowerCommandsFactory {

	public static final String START_COOLING = "Cooling";

	public static final String INTERRUPT_SOFTWARE_ACTIONS = "InterruptActions";

	private final SparseArray<PowerCommand> mOnCommands, mOffCommands;

	private
	@PowerState
	int mPowerState;

	private int mIndexInRunning;

	public int getIndexInRunning() {
		return mIndexInRunning;
	}

	public FilePowerCommandsFactory(@PowerState int powerState, SparseArray<PowerCommand>
			onCommands, SparseArray<PowerCommand> offCommands) {
		this.mPowerState = powerState;
		this.mOnCommands = onCommands;
		this.mOffCommands = offCommands;
	}

	@Override
	public boolean moveStateToNext() {
		final boolean isFinalState;

		switch (mPowerState) {
			case PowerState.ON:
				mIndexInRunning = 0;
				PowerCommand powerCommand = mOffCommands.valueAt(0);
				if (powerCommand.getCommand().equals(START_COOLING)) {
					mPowerState = PowerState.OFF_WAIT_FOR_COOLING;
				} else if (powerCommand.getCommand().equals(INTERRUPT_SOFTWARE_ACTIONS)) {
					mPowerState = PowerState.OFF_INTERRUPTING;
				} else {
					mPowerState = PowerState.OFF_RUNNING;
				}
				if (mIndexInRunning == mOffCommands.size()) {
					isFinalState = true;
					mPowerState = PowerState.OFF;
				} else {
					isFinalState = false;
				}
				break;
			case PowerState.OFF_RUNNING:
			case PowerState.OFF_INTERRUPTING:
			case PowerState.OFF_WAIT_FOR_COOLING:
				mIndexInRunning++;

				if (mIndexInRunning == mOffCommands.size()) {
					isFinalState = true;
					mIndexInRunning = 0;
					mPowerState = PowerState.OFF;
					return isFinalState;
				} else {
					isFinalState = false;
				}

				powerCommand = currentCommand();
				if (powerCommand.getCommand().equals(START_COOLING)) {
					mPowerState = PowerState.OFF_WAIT_FOR_COOLING;
				} else if (powerCommand.getCommand().equals(INTERRUPT_SOFTWARE_ACTIONS)) {
					mPowerState = PowerState.OFF_INTERRUPTING;
				} else {
					mPowerState = PowerState.OFF_RUNNING;
				}
				break;
			case PowerState.OFF:
				mIndexInRunning = 0;

				if (mIndexInRunning == mOnCommands.size()) {
					isFinalState = true;
					mPowerState = PowerState.ON;
				} else {
					mPowerState = PowerState.ON_RUNNING;
					isFinalState = false;
				}
				break;
			case PowerState.ON_RUNNING:
				mIndexInRunning++;

				if (mIndexInRunning == mOnCommands.size()) {
					isFinalState = true;
					mIndexInRunning = 0;
					mPowerState = PowerState.ON;
				} else {
					isFinalState = false;
				}
				break;
			case PowerState.PRE_LOOPING:
				mPowerState = PowerState.OFF;
				isFinalState = false;
				break;
			default:
				isFinalState = false;
		}
		return isFinalState;
	}

	@Override
	public int nextPowerState() {
		if(currentPowerState() == PowerState.PRE_LOOPING) {
			return PowerState.OFF;
		}
		@PowerState int curState = currentPowerState();
		moveStateToNext();
		@PowerState int newState = currentPowerState();
		if (newState == PowerState.OFF) {
			mIndexInRunning = mOffCommands.size() - 1;
		} else if (newState == PowerState.ON) {
			mIndexInRunning = mOnCommands.size() - 1;
		} else {
			mIndexInRunning--;
			if(mIndexInRunning == -1) {
				mIndexInRunning = 0;
			}
		}
		mPowerState = curState;
		return newState;
	}

	@Override
	public PowerCommand currentCommand() {
		switch (mPowerState) {
			case PowerState.ON_RUNNING:
			case PowerState.ON:
				return mOnCommands.valueAt(mIndexInRunning);
			case PowerState.OFF_INTERRUPTING:
			case PowerState.OFF_RUNNING:
			case PowerState.OFF_WAIT_FOR_COOLING:
				return mOffCommands.valueAt(mIndexInRunning);
			default:
				return null;
		}
	}

	@Override
	public int currentPowerState() {
		return mPowerState;
	}
}
