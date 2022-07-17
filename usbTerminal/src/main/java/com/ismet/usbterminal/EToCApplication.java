package com.ismet.usbterminal;

import androidx.core.util.Pair;
import android.util.Log;
import android.util.SparseArray;

import com.ismet.usbterminal.data.PowerCommand;
import com.ismet.usbterminal.data.PowerState;
import com.ismet.usbterminal.data.PullState;
import com.ismet.usbterminal.mainscreen.powercommands.DefaultPowerCommandsFactory;
import com.ismet.usbterminal.mainscreen.powercommands.FilePowerCommandsFactory;
import com.ismet.usbterminal.mainscreen.powercommands.PowerCommandsFactory;
import com.ismet.usbterminal.utils.Utils;
import com.proggroup.areasquarecalculator.InterpolationCalculatorApp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class EToCApplication extends InterpolationCalculatorApp {

	private static EToCApplication instance;

	private volatile
	@PullState
	int mPullState;

	private volatile boolean mStopPulling;

	private ScheduledExecutorService mPullDataService;

	private ScheduledExecutorService mWaitPullService;

	private ScheduledFuture mWaitPullFuture;

	private List<ScheduledFuture> mScheduledFutures;

	private String mCurrentTemperatureRequest;

	private long mTimeOfRecreating;

	private long mRenewTime;

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
		Log.v("EToCApplication", "onCreate triggered");

		/*Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {

			public void uncaughtException(Thread thread, Throwable ex) {

				SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale
						.ENGLISH);
				Date currentTime = new Date();

				try {
					File dir = new File(Environment.getExternalStorageDirectory(), "CLogs");
					dir.mkdirs();
					System.out.println("file != null");

					File file = new File(dir, "CrashLogs.txt");
					FileOutputStream fos = new FileOutputStream(file, true);
					new PrintStream(fos).print
							("=========================================================\r" +
									formatter.format(currentTime) + "\r"
	                                /* + ex.getStackTrace().toString()+ "\r"+ * / +
							"=========================================================\r");
					ex.printStackTrace(new PrintStream(fos));
					fos.close();

					Uri uri = Uri.fromFile(file);
					// Toast.makeText(getActivity(), "exists",
					// Toast.LENGTH_LONG).show();
					Intent sendIntent = new Intent(Intent.ACTION_SEND);
					sendIntent.setType("application/pdf");
					sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"nikhil.talaviya@gmail" +
							".com"});
					sendIntent.putExtra(Intent.EXTRA_SUBJECT, "DCam LOGS");
					sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
					sendIntent.putExtra(Intent.EXTRA_TEXT, "DCam LOGS");
					// startActivity(sendIntent);

					// System.exit(2);

					// restart your app here like this
					// Intent intent = new Intent();
					// intent.setClass(EToCApplication.this,MainActivity.class);
					//
					PendingIntent pendingIntent = PendingIntent.getActivity(getBaseContext(), 0,
							sendIntent, sendIntent.getFlags());
					//
					AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
					//mgr.set(AlarmManager.RTC,
					//		System.currentTimeMillis() + 1000, pendingIntent);
					System.exit(2);
					// //system will restart after 2 secs
					// clearApplicationData();

				} catch (Exception e1) {
					// TODO: handle exception
					e1.printStackTrace();
				}
			}
		});*/
	}

	public
	@PullState
	int getPullState() {
		return mPullState;
	}

	public void setPullState(@PullState int pullState) {
		this.mPullState = pullState;
	}

	public void initPullDataService(ScheduledExecutorService mPullTemperatureService) {
		this.mPullDataService = mPullTemperatureService;
	}

	public ScheduledExecutorService getWaitPullService() {
		return mWaitPullService;
	}

	public void initWaitPullService(ScheduledExecutorService scheduledExecutorService,
			ScheduledFuture scheduledFuture) {
		mWaitPullService = scheduledExecutorService;
		mWaitPullFuture = scheduledFuture;
	}

	public ScheduledFuture getWaitPullFuture() {
		return mWaitPullFuture;
	}

	public void clearPullDataService() {
		this.mPullDataService = null;
	}

	public ScheduledExecutorService getPullDataService() {
		return mPullDataService;
	}

	public void unScheduleTasks() {
		if (mScheduledFutures != null) {
			for (ScheduledFuture future : mScheduledFutures) {
				future.cancel(true);
			}
		}
	}

	public boolean isPreLooping() {
		return isPreLooping;
	}

	public void setPreLooping(boolean isPreLooping) {
		this.isPreLooping = isPreLooping;
	}

	public void setScheduledFutures(List<ScheduledFuture> scheduledFutures) {
		this.mScheduledFutures = scheduledFutures;
	}

	public String getCurrentTemperatureRequest() {
		return mCurrentTemperatureRequest;
	}

	public void setCurrentTemperatureRequest(String mCurrentTemperatureRequest) {
		this.mCurrentTemperatureRequest = mCurrentTemperatureRequest;
	}

	public void setStopPulling(boolean stopPulling) {
		this.mStopPulling = stopPulling;
	}

	public boolean isPullingStopped() {
		return mStopPulling;
	}

	public void refreshTimeOfRecreating() {
		mTimeOfRecreating = System.currentTimeMillis();
	}

	public long getTimeOfRecreating() {
		return mTimeOfRecreating;
	}

	public boolean reNewTimeOfRecreating() {
		mRenewTime = System.currentTimeMillis();

		boolean isRefreshed = false;

		if (Utils.elapsedTimeForCacheFill(mRenewTime, mTimeOfRecreating)) {
			refreshTimeOfRecreating();
			isRefreshed = true;
		}

		return isRefreshed;
	}

	public long getRenewTime() {
		return mRenewTime;
	}

	public PowerCommandsFactory parseCommands(String text) {
		text = text.replace("\r", "");
		String rows[] = text.split("\n");
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

		powerCommandsFactory = new DefaultPowerCommandsFactory(PowerState.PRE_LOOPING);

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
		String splitArr[] = text.split(";");

		int indexOfCommand = -1;

		try {
			indexOfCommand = Integer.parseInt(splitArr[0]);
			long delay = Long.parseLong(splitArr[1]);
			String command = splitArr[2];
			if(splitArr.length > 3) {
				List<String> possibleResponses = Arrays.asList(splitArr);
				possibleResponses = possibleResponses.subList(3, possibleResponses.size());
				String responses[] = new String[possibleResponses.size()];
				possibleResponses.toArray(responses);
				return new Pair<>(indexOfCommand, new PowerCommand(command, delay, responses));
			} else {
				return new Pair<>(indexOfCommand, new PowerCommand(command, delay));
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
