package com.ismet.usbterminal.updated.services;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.ismet.usbterminal.updated.EToCApplication;
import com.ismet.usbterminal.updated.data.PullState;
import com.ismet.usbterminal.updated.mainscreen.EToCMainActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PullStateManagingService extends Service {

    public static final String IS_AUTO_PULL_ON = "is_auto_pull_on";
    public static final String EXTRA_IS_TEMPERATURE = "is_temperature";
    public static final String EXTRA_IS_TEMPERATURE_CO2_HANDLING = "is_handling";

    private static final String TEMPERATURE_REQUEST = "FE-44-00-08-02-9F-25";
    private static final String CO2_REQUEST = "/5H750R";

    private Runnable mAutoPullTemperatureCo2Runnable;
    private Runnable mAutoPullCo2Runnable;

    private ScheduledExecutorService mPullTemperatureService, mPullCo2Service;
    private ScheduledFuture mPullTemperatureScheduledFuture, mPullCo2ScheduledFuture;
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

        mPullTemperatureService = eToCApplication.getPullTemperatureService();
        mPullCo2Service = eToCApplication.getPullCo2Service();
    }

    public static Intent intentForCo2(Context context) {
        EToCApplication application = EToCApplication.getInstance();
        Intent intent = new Intent(application, PullStateManagingService.class);

        intent.putExtra(IS_AUTO_PULL_ON, true);
        intent.putExtra(EXTRA_IS_TEMPERATURE_CO2_HANDLING, true);
        intent.putExtra(EXTRA_IS_TEMPERATURE, false);
        /*application.getPullData().isAutoPullOn = true;
        application.getPullData().isTemperature = false;
        application.getPullData().isValuesHandling = true;*/

        return intent;
    }

    public static Intent intentForTemperature(Context context) {
        EToCApplication application = EToCApplication.getInstance();
        Intent intent = new Intent(application, PullStateManagingService.class);
        intent.putExtra(IS_AUTO_PULL_ON, true);
        intent.putExtra(EXTRA_IS_TEMPERATURE_CO2_HANDLING, true);
        intent.putExtra(EXTRA_IS_TEMPERATURE, true);
        /*application.getPullData().isAutoPullOn = true;
        application.getPullData().isValuesHandling = true;
        application.getPullData().isTemperature = true;*/

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
                    boolean isTemperature = intent.getBooleanExtra(EXTRA_IS_TEMPERATURE,
                            false);
                    if (isTemperature) {
                        eToCApplication.setPullState(PullState.TEMPERATURE);
                        try {
                            if(mPullTemperatureScheduledFuture != null) {
                                mPullTemperatureScheduledFuture.cancel(true);
                            }
	                        if(mPullTemperatureService != null) {
		                        mPullTemperatureService.shutdown();
		                       // mPullTemperatureService.awaitTermination(1, TimeUnit.SECONDS);
	                        }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        mAutoPullTemperatureCo2Runnable = initAutoPullTemperatureCo2Runnable();
                        mPullTemperatureService = Executors.newSingleThreadScheduledExecutor();
                        mPullTemperatureScheduledFuture = mPullTemperatureService
                                .scheduleWithFixedDelay(mAutoPullTemperatureCo2Runnable, 0, 1,
                                          TimeUnit.SECONDS);
	                    eToCApplication.setPullTemperatureService(mPullTemperatureService);
                    } else {
                        eToCApplication.setPullState(PullState.CO2);
                        try {
                            if(mPullCo2ScheduledFuture != null) {
                                mPullCo2ScheduledFuture.cancel(true);
                            }
	                        if(mPullCo2Service != null) {
		                        mPullCo2Service.shutdown();
		                       // mPullCo2Service.awaitTermination(1, TimeUnit.SECONDS);
	                        }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        mAutoPullCo2Runnable = initAutoPullCo2Runnable();
                        mPullCo2Service = Executors.newSingleThreadScheduledExecutor();
                        mPullCo2ScheduledFuture = mPullCo2Service.scheduleWithFixedDelay
                                (mAutoPullCo2Runnable, 0, 1, TimeUnit.SECONDS);
	                    eToCApplication.setPullCo2Service(mPullCo2Service);
                    }
                } else {
                    startService(intentForTemperature(PullStateManagingService.this));
	                startService(intentForCo2(PullStateManagingService.this));
                }
            } else {
                mIsAutoHandling.set(false);
                try {
	                if(mPullTemperatureService != null) {
		                mPullTemperatureService.shutdown();
		                eToCApplication.setPullTemperatureService(null);
	                }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
	                if(mPullCo2Service != null) {
		                mPullCo2Service.shutdown();
		                eToCApplication.setPullCo2Service(null);
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

                /*if (!isRequestCompleted) {
                    eToCApplication.setPullState(PullState.NONE);
                    startService(intentForCo2(PullStateManagingService.this));
                }*/
            }
        };
    }

    public Runnable initAutoPullCo2Runnable() {
        return new Runnable() {
            @Override
            public void run() {
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
                /*
                if (!isRequestCompleted) {
                    eToCApplication.setPullState(PullState.NONE);
                    startService(intentForTemperature(PullStateManagingService.this));
                }*/
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
