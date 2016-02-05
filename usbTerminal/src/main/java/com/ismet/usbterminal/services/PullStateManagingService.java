package com.ismet.usbterminal.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.ismet.usbterminal.EToCApplication;
import com.ismet.usbterminal.data.PowerCommand;
import com.ismet.usbterminal.data.PowerState;
import com.ismet.usbterminal.data.PullState;
import com.ismet.usbterminal.mainscreen.EToCMainActivity;
import com.ismet.usbterminal.mainscreen.powercommands.PowerCommandsFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PullStateManagingService extends Service {

    private static final String TAG = PullStateManagingService.class.getSimpleName();

    public static final String WAIT_FOR_COOLING_ACTION = "wait_for_cooling";

    public static final String IS_AUTO_PULL_ON = "is_auto_pull_on";
    public static final String IS_RECREATING = "is_recreating";

    public static final String CO2_REQUEST = "(FE-44-00-08-02-9F-25)";
    private static final int MAX_WAIT_TIME_FOR_CANCEL_EXECUTOR = 100;
    public static final int DELAY_ON_CHANGE_REQUEST = 1000;

    private ScheduledExecutorService mPullDataService;
    private ScheduledExecutorService mWaitPullService;

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
        mWaitPullService = eToCApplication.getWaitPullService();
    }

    public static Intent intentForService(Context context, boolean isAutoPull) {
        EToCApplication application = EToCApplication.getInstance();
        Intent intent = new Intent(application, PullStateManagingService.class);
        intent.putExtra(IS_AUTO_PULL_ON, isAutoPull);
        if (isAutoPull) {
            EToCApplication.getInstance().setStopPulling(false);
        }
        return intent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        Bundle extras = intent.getExtras();
        if (extras != null && !eToCApplication.isPullingStopped()) {
            String action = intent.getAction();

            boolean isPull = extras.getBoolean(IS_AUTO_PULL_ON, true);

            if (action != null && action.equals(WAIT_FOR_COOLING_ACTION)) {
                if (isPull) {
                    mWaitPullService = Executors.newSingleThreadScheduledExecutor();

                    final PowerCommandsFactory commandsFactory = eToCApplication
                            .getPowerCommandsFactory();

                    final PowerCommand command;

                    if (eToCApplication.isPreLooping()) {
                        command = new PowerCommand("/5J1R", 1000);
                    } else {
                        command = commandsFactory.currentCommand();
                    }

                    Log.e(TAG, "Service 1 started");

                    eToCApplication.initWaitPullService(mWaitPullService, mWaitPullService
                            .scheduleWithFixedDelay(new Runnable() {

                        @Override
                        public void run() {
                            final String message;

                            if (eToCApplication.isPreLooping()) {
                                message = command.getCommand();
                            } else {
                                message = "/5H0000R";
                            }

                            int state = commandsFactory.currentPowerState();

                            if (state != PowerState.OFF) {
                                EToCMainActivity.sendBroadCastWithData(PullStateManagingService
                                        .this, message);
                                EToCMainActivity.sendBroadCastWithData(PullStateManagingService
                                        .this, state);
                            }

                            try {
                                Thread.sleep((int) (0.3 * command.getDelay()));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }, 0, command.getDelay(), TimeUnit.MILLISECONDS));
                } else {
                    Log.e(TAG, "Service 1 stopped");
                    eToCApplication.getWaitPullFuture().cancel(true);

                    eToCApplication.initWaitPullService(null, null);
                }
                intent.setAction(null);
            } else {
                if (isPull) {
                    Log.e(TAG, "Service 0 started");

                    mIsAutoHandling.set(true);
                    if (eToCApplication.getPullState() == PullState.NONE) {
                        eToCApplication.setPullState(PullState.TEMPERATURE);
                    }

                    if (mPullDataService == null) {
                        mPullDataService = Executors.newSingleThreadScheduledExecutor();
                        eToCApplication.initPullDataService(mPullDataService);
                        eToCApplication.refreshTimeOfRecreating();
                        eToCApplication.reNewTimeOfRecreating();
                    } else {
                        eToCApplication.unScheduleTasks();
                        boolean isRenew = eToCApplication.reNewTimeOfRecreating();
                        if (isRenew) {
                            mPullDataService = Executors.newSingleThreadScheduledExecutor();
                            eToCApplication.clearPullDataService();
                            eToCApplication.initPullDataService(mPullDataService);
                        }
                    }

                    List<ScheduledFuture> scheduledFutures = new ArrayList<>(1);

                    Runnable autoChangeRunnable = new Runnable() {

                        @Override
                        public void run() {
                            int pullState = eToCApplication.getPullState();

                            if (pullState == PullState.NONE || !mIsAutoHandling.get()) {
                                return;
                            }

                            switch (pullState) {
                                case PullState.TEMPERATURE:
                                    pullState = PullState.CO2;
                                    break;
                                case PullState.CO2:
                                    pullState = PullState.TEMPERATURE;
                                    break;
                            }

                            eToCApplication.setPullState(pullState);

                            boolean isTemperature = pullState == PullState.TEMPERATURE;
                            if (isTemperature) {
                                initAutoPullTemperatureRunnable().run();
                            } else {
                                initAutoPullCo2Runnable().run();
                            }
                        }
                    };

                    scheduledFutures.add(mPullDataService.scheduleWithFixedDelay
                            (autoChangeRunnable, 0, 1, TimeUnit.SECONDS));

                    eToCApplication.setScheduledFutures(scheduledFutures);
                } else {
                    Log.e(TAG, "Service 0 stopped");

                    mIsAutoHandling.set(false);
                    eToCApplication.unScheduleTasks();
                    eToCApplication.setStopPulling(true);
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
                /*EToCMainActivity.sendBroadCastWithData(PullStateManagingService.this,
                        "/5J5R");

                try {
                    Thread.sleep(350);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/

                EToCMainActivity.sendBroadCastWithData(PullStateManagingService.this,
                        eToCApplication.getCurrentTemperatureRequest());
            }
        };
    }
}
