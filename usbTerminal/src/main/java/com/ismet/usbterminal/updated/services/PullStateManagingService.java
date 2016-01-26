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
import com.ismet.usbterminal.updated.mainscreen.EToCMainHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PullStateManagingService extends Service {

    public static final String IS_AUTO_PULL_ON = "is_auto_pull_on";

    private static final String CO2_REQUEST = "(FE-44-00-08-02-9F-25)";
    private static final int MAX_WAIT_TIME_FOR_CANCEL_EXECUTOR = 100;
    public static final int DELAY_ON_CHANGE_REQUEST = 1000;

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

        mPullDataService = eToCApplication.getPullDataService();
    }

    public static Intent intentForData(Context context) {
        Intent intent = intentForService(context, true);
        return intent;
    }

    public static Intent intentForService(Context context, boolean isAutoPull) {
        EToCApplication application = EToCApplication.getInstance();
        Intent intent = new Intent(application, PullStateManagingService.class);
        intent.putExtra(IS_AUTO_PULL_ON, isAutoPull);
        return intent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        if (extras != null && !eToCApplication.isUnScheduling()) {
            boolean isPull = extras.getBoolean(IS_AUTO_PULL_ON, true);
            if (isPull) {
                mIsAutoHandling.set(true);
                if(eToCApplication.getPullState() == PullState.NONE) {
                    eToCApplication.setPullState(PullState.TEMPERATURE);
                }

                if(mPullDataService == null) {
                    mPullDataService = Executors.newSingleThreadScheduledExecutor();
                    eToCApplication.initPullDataService(mPullDataService);
                } else {
                    eToCApplication.unScheduleTasks();
                }

                Runnable autoSendRequest = new Runnable() {
                    @Override
                    public void run() {
                        int pullState = eToCApplication.getPullState();
                        if(pullState == PullState.NONE || !mIsAutoHandling.get()) {
                            return;
                        }

                        boolean isTemperature = eToCApplication.getPullState() == PullState
                                .TEMPERATURE;
                        if (isTemperature) {
                            initAutoPullTemperatureRunnable().run();
                        } else {
                            initAutoPullCo2Runnable().run();
                        }
                    }
                };

                List<ScheduledFuture> scheduledFutures = new ArrayList<>(2);

                scheduledFutures.add(mPullDataService.scheduleWithFixedDelay(autoSendRequest, 0, 1,
                        TimeUnit.SECONDS));

                Runnable autoChangeRunnable = new Runnable() {
                    @Override
                    public void run() {
                        int pullState = eToCApplication.getPullState();

                        if(pullState == PullState.NONE || !mIsAutoHandling.get()) {
                            return;
                        }

                        try {
                            Thread.sleep(DELAY_ON_CHANGE_REQUEST);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        int newPullState = eToCApplication.getPullState();
                        if (newPullState != PullState.NONE) {
                            if (pullState == newPullState) {
                                int changedPullState = PullState.NONE;

                                switch (pullState) {
                                    case PullState.TEMPERATURE:
                                        changedPullState = PullState.CO2;
                                        break;
                                    case PullState.CO2:
                                        changedPullState = PullState.TEMPERATURE;
                                        break;
                                }

                                eToCApplication.setPullState(changedPullState);
                            }
                        }
                    }
                };

                scheduledFutures.add(mPullDataService.scheduleWithFixedDelay(autoChangeRunnable, 0,
                        1, TimeUnit.SECONDS));

                eToCApplication.setScheduledFutures(scheduledFutures);
            } else {
                mIsAutoHandling.set(false);
                eToCApplication.unScheduleTasks();
                if(eToCApplication.isMeasureStarted()) {
                    eToCApplication.setUnScheduling(true);
                }
            }
        }
        return START_STICKY;
    }

    private Runnable initAutoPullCo2Runnable() {
        return new Runnable() {
            @Override
            public void run() {
                if (eToCApplication.getPullState() == PullState.NONE) {
                    return;
                }
                EToCMainActivity.sendBroadCastWithData(PullStateManagingService.this,
                        CO2_REQUEST);
            }
        };
    }

    public Runnable initAutoPullTemperatureRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                if (eToCApplication.getPullState() == PullState.NONE) {
                    return;
                }
                EToCMainActivity.sendBroadCastWithData(PullStateManagingService.this,
                        eToCApplication.getCurrentTemperatureRequest());
            }
        };
    }
}
