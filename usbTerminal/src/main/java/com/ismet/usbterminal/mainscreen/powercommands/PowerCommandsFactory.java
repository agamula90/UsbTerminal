package com.ismet.usbterminal.mainscreen.powercommands;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.view.Window;
import android.widget.TextView;

import com.ismet.usbterminal.MainActivity;
import com.ismet.usbterminalnew.R;
import com.ismet.usbterminal.data.PowerCommand;
import com.ismet.usbterminal.data.PowerState;
import com.proggroup.areasquarecalculator.api.LibraryContentAttachable;

public abstract class PowerCommandsFactory {

	private Dialog mAlertDialog;

	public abstract boolean moveStateToNext();

	public abstract
	@PowerState
	int nextPowerState();

	public abstract PowerCommand currentCommand();

	public abstract
	@PowerState
	int currentPowerState();

	public void sendRequest(final Context context, final Handler mHandler, final CommandsDeliverer
			commandsDeliverer) {
		final boolean handled;
        MainActivity activity = (MainActivity) context;

		@PowerState int mPowerState = currentPowerState();

		switch (mPowerState) {
			case PowerState.OFF_INTERRUPTING:
				Message message = mHandler.obtainMessage();
				message.what = MainActivity.MESSAGE_INTERRUPT_ACTIONS;
				message.sendToTarget();
				handled = true;
				break;
			case PowerState.OFF_WAIT_FOR_COOLING:
				activity.waitForCooling();

				if(context instanceof LibraryContentAttachable) {
					((LibraryContentAttachable) context).dismissProgress();
				}

				mAlertDialog = new Dialog(context);
				mAlertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
				mAlertDialog.setContentView(R.layout.layout_cooling);
				mAlertDialog.getWindow().setBackgroundDrawableResource(android.R.color
						.transparent);

				((TextView) mAlertDialog.findViewById(R.id.text)).setText("  Cooling down.  Do not" +
						" " +
						"switch power off.  Please wait . . . ! ! !    \nSystem will turn off " +
						"automaticaly.");

				mAlertDialog.setCancelable(false);

				mAlertDialog.show();

				handled = true;
				break;
			default:
				final PowerCommand currentCommand = currentCommand();
				if (currentCommand != null) {
					if (commandsDeliverer != null) {
						commandsDeliverer.deliverCommand(currentCommand.getCommand());
					}

					if (!currentCommand.hasSelectableResponses()) {
						int powerState = nextPowerState();

						if (powerState != PowerState.OFF && powerState != PowerState.ON) {
							mHandler.postDelayed(new Runnable() {

								@Override
								public void run() {
									PowerCommand currentCommandNew = currentCommand();
									int currentState = currentPowerState();
									if(currentState != PowerState.OFF && currentState !=
											PowerState.ON) {
										if (currentCommand.equals(currentCommandNew)) {
											moveStateToNext();
											currentState = currentPowerState();
											if (currentState != PowerState.OFF && currentState != PowerState.ON) {
												sendRequest(context, mHandler, commandsDeliverer);
											}
										}
									}
								}
							}, currentCommand.getDelay());
						}
					}
					handled = true;
				} else {
					handled = true;
				}
		}

		if (!handled) {
			throw new IllegalArgumentException();
		}
	}

	public Dialog getCoolingDialog() {
		return mAlertDialog;
	}
}
