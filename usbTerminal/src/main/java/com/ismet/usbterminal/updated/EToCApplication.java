package com.ismet.usbterminal.updated;

import android.util.Log;

import com.ismet.usbterminal.updated.data.AppData;
import com.ismet.usbterminal.updated.data.PowerCommand;
import com.ismet.usbterminal.updated.data.PullState;
import com.ismet.usbterminal.utils.Utils;
import com.proggroup.areasquarecalculator.InterpolationCalculatorApp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class EToCApplication extends InterpolationCalculatorApp {

	private static EToCApplication instance;

	public static EToCApplication getInstance() {
		return instance;
	}

	private volatile @PullState int mPullState;

    private volatile boolean mStopPulling;

	private ScheduledExecutorService mPullDataService;

	private ScheduledExecutorService mWaitPullService;

	private ScheduledFuture mWaitPullFuture;

	private List<ScheduledFuture> mScheduledFutures;

    private String mCurrentTemperatureRequest;

	private long mTimeOfRecreating;

	private long mRenewTime;

	private List<PowerCommand> commands = new ArrayList<>();

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

    public void setPullState(@PullState int pullState) {
        this.mPullState = pullState;
    }

    public @PullState int getPullState() {
        return mPullState;
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
        if(mScheduledFutures != null) {
            for (ScheduledFuture future : mScheduledFutures) {
                future.cancel(true);
            }
        }
    }

    public void setScheduledFutures(List<ScheduledFuture> scheduledFutures) {
        this.mScheduledFutures = scheduledFutures;
    }

    public void setCurrentTemperatureRequest(String mCurrentTemperatureRequest) {
        this.mCurrentTemperatureRequest = mCurrentTemperatureRequest;
    }

    public String getCurrentTemperatureRequest() {
        return mCurrentTemperatureRequest;
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

		if(Utils.elapsedTimeForCacheFill(mRenewTime, mTimeOfRecreating)) {
			refreshTimeOfRecreating();
			isRefreshed = true;
		}

		return isRefreshed;
	}

	public long getRenewTime() {
		return mRenewTime;
	}

    public void loadCommands(String text) {
        commands.clear();
        text = text.replace("\n", "");
        text = text.replace("\r", "");
        String values[] = text.split(AppData.SPLIT_STRING);
        if(values.length == 16) {
            for (int i = 0; i < 8; i++) {
                try {
                    commands.add(new PowerCommand(values[2 * i], Long.parseLong(values[2 * i + 1])));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    commands.clear();
                    return;
                }
            }
        }
    }

    public List<PowerCommand> getCommands() {
        return commands;
    }
}
