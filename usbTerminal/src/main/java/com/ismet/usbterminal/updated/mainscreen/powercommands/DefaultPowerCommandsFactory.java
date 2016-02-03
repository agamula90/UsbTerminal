package com.ismet.usbterminal.updated.mainscreen.powercommands;

import com.ismet.usbterminal.updated.data.PowerCommand;
import com.ismet.usbterminal.updated.data.PowerState;
import com.ismet.usbterminal.updated.services.PullStateManagingService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultPowerCommandsFactory extends PowerCommandsFactory {

	private volatile @PowerState int mPowerState;

	private final Map<Integer, PowerCommand> mOnCommands, mOffCommands;

	public DefaultPowerCommandsFactory(final @PowerState int mPowerState) {
		this.mPowerState = mPowerState;
		mOnCommands = new HashMap<>();
		mOffCommands = new HashMap<>();

		mOnCommands.put(PowerState.ON_STAGE1, new PowerCommand("/5H0000R", 500));
		mOnCommands.put(PowerState.ON_STAGE1_REPEAT, new PowerCommand("/5H0000R", 500));
		mOnCommands.put(PowerState.ON_STAGE2A, new PowerCommand("/5J1R", 1000));
		mOnCommands.put(PowerState.ON_STAGE2B, new PowerCommand("/5J1R", 1000, "@5J101 ",
				"@5J001 "));
		mOnCommands.put(PowerState.ON_STAGE2, new PowerCommand("/5J5R", 1000, "@5J101 "));
		mOnCommands.put(PowerState.ON_STAGE3, new PowerCommand(PullStateManagingService
				 .CO2_REQUEST, 2000));
		mOnCommands.put(PowerState.ON_STAGE4, new PowerCommand("/1ZR", 0));

		mOffCommands.put(PowerState.OFF_STAGE1, new PowerCommand("/5H0000R", 1000));
		mOnCommands.put(PowerState.OFF_WAIT_FOR_COOLING, new PowerCommand("/5H0000R", 1000));
		mOffCommands.put(PowerState.OFF_FINISHING, new PowerCommand("/5J1R", 1000));
	}

	@Override
	public boolean moveStateToNext() {
		final boolean isFinalState;
		switch (mPowerState) {
			case PowerState.ON_STAGE1:
				isFinalState = false;
				mPowerState = PowerState.ON_STAGE1_REPEAT;
				break;
			case PowerState.ON_STAGE1_REPEAT:
				isFinalState = false;
				mPowerState = PowerState.ON_STAGE2A;
				break;
			case PowerState.ON_STAGE2A:
				isFinalState = false;
				mPowerState = PowerState.ON_STAGE2B;
				break;
			case PowerState.ON_STAGE2B:
				isFinalState = false;
				mPowerState = PowerState.ON_STAGE2;
				break;
			case PowerState.ON_STAGE2:
				isFinalState = false;
				mPowerState = PowerState.ON_STAGE3;
				break;
			case PowerState.ON_STAGE3:
				isFinalState = false;
				mPowerState = PowerState.ON_STAGE4;
				break;
			case PowerState.ON_STAGE4:
				isFinalState = true;
				mPowerState = PowerState.ON;
				break;
			case PowerState.ON:
				isFinalState = false;
				mPowerState = PowerState.OFF_INTERRUPTING;
				break;
			case PowerState.OFF_INTERRUPTING:
				isFinalState = false;
				mPowerState = PowerState.OFF_STAGE1;
				break;
			case PowerState.OFF_STAGE1:
				isFinalState = false;
				mPowerState = PowerState.OFF_WAIT_FOR_COOLING;
				break;
			case PowerState.OFF_WAIT_FOR_COOLING:
				isFinalState = false;
				mPowerState = PowerState.OFF_FINISHING;
				break;
			case PowerState.OFF_FINISHING:
				isFinalState = true;
				mPowerState = PowerState.OFF;
				break;
			case PowerState.OFF:
				isFinalState = false;
				mPowerState = PowerState.ON_STAGE2A;
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
		mPowerState = curState;
		return newState;
	}

	@Override
	public PowerCommand currentCommand() {
		if(mOnCommands.containsKey(mPowerState)) {
			return mOnCommands.get(mPowerState);
		} else if(mOffCommands.containsKey(mPowerState)) {
			return mOffCommands.get(mPowerState);
		} else {
			return null;
		}
	}

	@Override
	public int currentPowerState() {
		return mPowerState;
	}
}
