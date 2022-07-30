package com.ismet.usbterminal;

import androidx.core.util.Pair;
import android.util.SparseArray;

import com.ismet.usbterminal.data.Command;
import com.ismet.usbterminal.data.PowerCommand;
import com.ismet.usbterminal.data.PowerState;
import com.ismet.usbterminal.mainscreen.powercommands.LocalPowerCommandsFactory;
import com.ismet.usbterminal.mainscreen.powercommands.FilePowerCommandsFactory;
import com.ismet.usbterminal.mainscreen.powercommands.PowerCommandsFactory;
import com.proggroup.areasquarecalculator.InterpolationCalculatorApp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class EToCApplication extends InterpolationCalculatorApp {

	private static EToCApplication instance;

	private String mCurrentTemperatureRequest;

    private int mBorderCoolingTemperature = 80;

	private PowerCommandsFactory powerCommandsFactory;

	private boolean isPreLooping;

	public static EToCApplication getInstance() {
		return instance;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
	}

    public boolean isPreLooping() {
		return isPreLooping;
	}

	public void setPreLooping(boolean isPreLooping) {
		this.isPreLooping = isPreLooping;
	}

    public String getCurrentTemperatureRequest() {
		return mCurrentTemperatureRequest;
	}

	public void setCurrentTemperatureRequest(String mCurrentTemperatureRequest) {
		this.mCurrentTemperatureRequest = mCurrentTemperatureRequest;
	}

    public PowerCommandsFactory parseCommands(String text) {
		text = text.replace("\r", "");
		String[] rows = text.split("\n");
		final String borderTemperatureString = "borderTemperature:";
		final String onString = "on:";
		final String offString = "off:";

		List<String> borderTemperatures = new ArrayList<>();
		List<String> onCommands = new ArrayList<>();
		List<String> offCommands = new ArrayList<>();

		List<String> delimitedValues = new ArrayList<String>() {
			{
				add(borderTemperatureString);
				add(onString);
				add(offString);
			}
		};

		List<String> currentList = null;

		for (String row : rows) {
			int index = delimitedValues.indexOf(row);
			if(index >= 0) {
				switch (index) {
					case 0:
						currentList = borderTemperatures;
						break;
					case 1:
						currentList = onCommands;
						break;
					case 2:
						currentList = offCommands;
						break;
					default:
						currentList = null;
				}
			} else {
				if(currentList != null) {
					currentList.add(row);
				}
			}
		}

		powerCommandsFactory = new LocalPowerCommandsFactory(PowerState.PRE_LOOPING);

		if(borderTemperatures.size() != 1) {
			return powerCommandsFactory;
		} else {
			try {
				mBorderCoolingTemperature = Integer.parseInt(borderTemperatures.get(0));
			} catch (NumberFormatException e) {
				e.printStackTrace();
				return powerCommandsFactory;
			}

			SparseArray<PowerCommand> onCommandsArr = new SparseArray<>();
			for (String onCommand : onCommands) {
				Pair<Integer, PowerCommand> parsedRow = parseCommand(onCommand);
				if(parsedRow != null) {
					onCommandsArr.put(parsedRow.first, parsedRow.second);
				} else {
					return powerCommandsFactory;
				}
			}

			SparseArray<PowerCommand> offCommandsArr = new SparseArray<>();
			for (String offCommand : offCommands) {
				Pair<Integer, PowerCommand> parsedRow = parseCommand(offCommand);

				if(parsedRow != null) {
					offCommandsArr.put(parsedRow.first, parsedRow.second);
				} else {
					return powerCommandsFactory;
				}
			}

			powerCommandsFactory = new FilePowerCommandsFactory(PowerState.PRE_LOOPING, onCommandsArr,
					offCommandsArr);
		}
		return powerCommandsFactory;
	}

	public PowerCommandsFactory getPowerCommandsFactory() {
		return powerCommandsFactory;
	}

	private static Pair<Integer, PowerCommand> parseCommand(String text) {
		String[] splitArr = text.split(";");

		int indexOfCommand;

		try {
			indexOfCommand = Integer.parseInt(splitArr[0]);
			long delay = Long.parseLong(splitArr[1]);
			String command = splitArr[2];
			if(splitArr.length > 3) {
				List<String> possibleResponses = Arrays.asList(splitArr);
				possibleResponses = possibleResponses.subList(3, possibleResponses.size());
				String[] responses = new String[possibleResponses.size()];
				possibleResponses.toArray(responses);
				return new Pair<>(indexOfCommand, new PowerCommand(new Command(command), delay, responses));
			} else {
				return new Pair<>(indexOfCommand, new PowerCommand(new Command(command), delay, new String[0]));
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
			return null;
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
			return null;
		}
	}

	public int getBorderCoolingTemperature() {
		return mBorderCoolingTemperature;
	}
}
