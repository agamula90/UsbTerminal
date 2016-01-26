package com.ismet.usbterminal.updated;

import android.util.Log;

import com.ismet.usbterminal.updated.data.PullData;
import com.ismet.usbterminal.updated.data.PullState;
import com.proggroup.areasquarecalculator.InterpolationCalculatorApp;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class EToCApplication extends InterpolationCalculatorApp {

	private static EToCApplication instance;

	public static EToCApplication getInstance() {
		return instance;
	}

	private volatile @PullState int mPullState;

    private boolean mUnScheduling;

    private boolean isMeasureStarted;

    private final PullData pullData = new PullData();

	private ScheduledExecutorService mPullDataService;

	private List<ScheduledFuture> mScheduledFutures;

    private String mCurrentTemperatureRequest;

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

    public PullData getPullData() {
        return pullData;
    }

	public void initPullDataService(ScheduledExecutorService mPullTemperatureService) {
		this.mPullDataService = mPullTemperatureService;
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

    public void setUnScheduling(boolean mUnScheduling) {
        this.mUnScheduling = mUnScheduling;
    }

    public boolean isUnScheduling() {
        return mUnScheduling;
    }

    public void setMeasureStarted(boolean measureStarted) {
        isMeasureStarted = measureStarted;
    }

    public boolean isMeasureStarted() {
        return isMeasureStarted;
    }
}
