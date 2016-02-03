package com.ismet.usbterminal.updated.mainscreen.powercommands;

import android.util.SparseArray;

import com.ismet.usbterminal.updated.data.PowerCommand;
import com.ismet.usbterminal.updated.data.PowerState;

public class FilePowerCommandsFactory extends PowerCommandsFactory {

	public static final String START_COOLING = "Cooling";

	public static final String INTERRUPT_SOFTWARE_ACTIONS = "InterruptActions";

	private
	@PowerState
	int mPowerState;

	private int mIndexInRunning;

	private final SparseArray<PowerCommand> mOnCommands, mOffCommands;

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
				PowerCommand powerCommand = currentCommand();
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
				powerCommand = currentCommand();
				if (powerCommand.getCommand().equals(START_COOLING)) {
					mPowerState = PowerState.OFF_WAIT_FOR_COOLING;
				} else if (powerCommand.getCommand().equals(INTERRUPT_SOFTWARE_ACTIONS)) {
					mPowerState = PowerState.OFF_INTERRUPTING;
				}
				if (mIndexInRunning == mOffCommands.size()) {
					isFinalState = true;
					mIndexInRunning = 0;
					mPowerState = PowerState.OFF;
				} else {
					isFinalState = false;
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
			default:
				isFinalState = false;
		}
		return isFinalState;
	}

	@Override
	public int nextPowerState() {
		@PowerState int curState = currentPowerState();
		moveStateToNext();
		@PowerState int newState = currentPowerState();
		if(newState == PowerState.OFF || newState == PowerState.ON) {
			mIndexInRunning = 0;
		} else {
			mIndexInRunning--;
		}
		mPowerState = curState;
		return newState;
	}

	@Override
	public PowerCommand currentCommand() {
		switch (mPowerState) {
			case PowerState.ON_RUNNING:
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
