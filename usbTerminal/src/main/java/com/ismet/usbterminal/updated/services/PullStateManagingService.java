package com.ismet.usbterminal.updated.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.ismet.usbterminal.updated.EToCApplication;
import com.ismet.usbterminal.updated.data.PullState;
import com.ismet.usbterminal.updated.mainscreen.EToCMainActivity;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PullStateManagingService extends Service {

    public static final String IS_AUTO_PULL_ON = "is_auto_pull_on";
    public static final String EXTRA_IS_TEMPERATURE_CO2_HANDLING = "is_handling";

    private static final String TEMPERATURE_REQUEST = "FE-44-00-08-02-9F-25";
    private static final String CO2_REQUEST = "/5H750R";
	private static final int MAX_WAIT_TIME_FOR_CANCEL_EXECUTOR = 100;

    private Runnable mAutoPullTemperatureCo2Runnable;
    private Runnable mAutoPullCo2Runnable;

    private ScheduledExecutorService mPullDataService;
    private EToCApplication eToCApplication;

    private AtomicBoolean mIsAutoHandling = new AtomicBoolean(true);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        eToCApplication = EToCApplication.getInstance();

        mPullDataService = eToCApplication.getPullTemperatureService();
    }

    public static Intent intentForData(Context context) {
        EToCApplication application = EToCApplication.getInstance();
        Intent intent = new Intent(application, PullStateManagingService.class);

        intent.putExtra(IS_AUTO_PULL_ON, true);
        intent.putExtra(EXTRA_IS_TEMPERATURE_CO2_HANDLING, true);

        return intent;
    }

    public static Intent intentForService(Context context, boolean isAutoPull) {
        EToCApplication application = EToCApplication.getInstance();
        Intent intent = new Intent(application, PullStateManagingService.class);
        intent.putExtra(IS_AUTO_PULL_ON, isAutoPull);
        /*application.getPullData().isAutoPullOn = isAutoPull;
        application.getPullData().isValuesHandling = false;
        application.getPullData().isTemperature = false;*/
        return intent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
        boolean isPull = extras.getBoolean(IS_AUTO_PULL_ON, true);
            if (isPull) {
                mIsAutoHandling.set(true);

                boolean isValuesHandling = intent.getBooleanExtra
                        (EXTRA_IS_TEMPERATURE_CO2_HANDLING, false);

                if (isValuesHandling) {
                    boolean isTemperature = eToCApplication.getPullState() == PullState.TEMPERATURE;
                    if (isTemperature) {
                        try {
	                        if(mPullDataService != null) {
		                        mPullDataService.shutdown();
		                        mPullDataService.awaitTermination(MAX_WAIT_TIME_FOR_CANCEL_EXECUTOR, TimeUnit
				                        .MILLISECONDS);
	                        }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        mAutoPullTemperatureCo2Runnable = initAutoPullTemperatureCo2Runnable();
                        mPullDataService = Executors.newSingleThreadScheduledExecutor();
                        mPullDataService
                                .scheduleWithFixedDelay(mAutoPullTemperatureCo2Runnable, 0, 1,
                                          TimeUnit.SECONDS);
	                    eToCApplication.setPullTemperatureService(mPullDataService);
                    } else {
	                    try {
		                    if(mPullDataService != null) {
			                    mPullDataService.shutdown();
			                    mPullDataService.awaitTermination(MAX_WAIT_TIME_FOR_CANCEL_EXECUTOR, TimeUnit.MILLISECONDS);
		                    }
	                    } catch (Exception e) {
		                    e.printStackTrace();
	                    }
                        mAutoPullCo2Runnable = initAutoPullCo2Runnable();
	                    mPullDataService = Executors.newSingleThreadScheduledExecutor();
                        mPullDataService.scheduleWithFixedDelay
                                (mAutoPullCo2Runnable, 0, 1, TimeUnit.SECONDS);
	                    eToCApplication.setPullCo2Service(mPullDataService);
                    }
                } else {
	                eToCApplication.setPullState(PullState.TEMPERATURE);
                    startService(intentForData(PullStateManagingService.this));
                }
            } else {
                mIsAutoHandling.set(false);
                try {
	                if(mPullDataService != null) {
		                mPullDataService.shutdown();
		                eToCApplication.setPullTemperatureService(null);
	                }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
	    return START_STICKY;
    }

    private Runnable initAutoPullTemperatureCo2Runnable() {
        return new Runnable() {
            @Override
            public void run() {
	            if(eToCApplication.getPullState() == PullState.NONE) {
		            return;
	            }
                eToCApplication.setPullState(PullState.TEMPERATURE);
                EToCMainActivity.sendBroadCastWithData(PullStateManagingService.this,
                        TEMPERATURE_REQUEST);

                boolean isRequestCompleted = false;

                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    isRequestCompleted = true;
                }

                if(!mIsAutoHandling.get()) {
                    eToCApplication.setPullState(PullState.NONE);
                    return;
                }

                if (!isRequestCompleted) {
                    eToCApplication.setPullState(PullState.CO2);
	                startService(intentForData(PullStateManagingService.this));
                }
            }
        };
    }

    public Runnable initAutoPullCo2Runnable() {
        return new Runnable() {
            @Override
            public void run() {
	            if(eToCApplication.getPullState() == PullState.NONE) {
		            return;
	            }
                eToCApplication.setPullState(PullState.CO2);
                EToCMainActivity.sendBroadCastWithData(PullStateManagingService.this,
                        CO2_REQUEST);

                boolean isRequestCompleted = false;

                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    isRequestCompleted = true;
                }

                if(!mIsAutoHandling.get()) {
                    eToCApplication.setPullState(PullState.NONE);
                    return;
                }

                if (!isRequestCompleted) {
                    eToCApplication.setPullState(PullState.TEMPERATURE);
	                startService(intentForData(PullStateManagingService.this));
                }
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
