package com.ismet.usbterminal.updated.mainscreen.tasks;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v4.util.Pair;
import android.widget.Toast;

import com.ismet.usbterminal.updated.EToCApplication;
import com.ismet.usbterminal.updated.data.PrefConstants;
import com.ismet.usbterminal.updated.mainscreen.EToCMainActivity;
import com.ismet.usbterminal.updated.services.PullStateManagingService;

import java.lang.ref.WeakReference;
import java.util.List;

public class SendDataToUsbTask extends AsyncTask<Long, Pair<Integer, String>, String> {

    private final List<String> simpleCommands;

    private final List<String> loopCommands;

    private final boolean autoPpm;

    private final WeakReference<EToCMainActivity> weakActivity;

    public SendDataToUsbTask(List<String> simpleCommands, List<String> loopCommands,
                             boolean autoPpm, EToCMainActivity activity) {
        this.simpleCommands = simpleCommands;
        this.loopCommands = loopCommands;
        this.autoPpm = autoPpm;
        this.weakActivity = new WeakReference<>(activity);
    }

    @Override
    protected String doInBackground(Long... params) {
        Long future = params[0];
        Long delay = params[1];

        if (weakActivity.get() != null) {

            SharedPreferences preferences = weakActivity.get().getPrefs();

            boolean isAuto = preferences.getBoolean(PrefConstants.IS_AUTO, false);
            if (isAuto) {
                for (int l = 0; l < 3; l++) {
                    processChart(future, delay);
                }
            } else {
                processChart(future, delay);
            }

            if (isAuto && autoPpm && !preferences.getBoolean(PrefConstants.SAVE_AS_CALIBRATION,
                    false)) {
                publishProgress(new Pair<Integer, String>(2, null));
            }

            weakActivity.get().startService(PullStateManagingService.intentForService
                    (weakActivity.get(), true));
        }

        return null;
    }

    @Override
    protected void onProgressUpdate(Pair<Integer, String>... values) {
        super.onProgressUpdate(values);
        if (weakActivity.get() != null) {
            if (values[0].first < 2) {
                weakActivity.get().sendMessageWithUsbDataReady(values[0].second);
            } else {
                weakActivity.get().invokeAutoCalculations();
            }
        }
    }

    public void processChart(long future, long delay) {
        if (weakActivity.get() != null) {
            for (int i = 0; i < simpleCommands.size(); i++) {
                if (simpleCommands.get(i).contains("delay")) {
                    int delayC = Integer.parseInt(simpleCommands.get(i).replace("delay", "").trim
                            ());
                    try {
                        Thread.sleep(delayC);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    publishProgress(new Pair<>(0, simpleCommands.get(i)));
                }
            }
        }

        if (weakActivity.get() != null) {
            weakActivity.get().setTimerRunning(true);
        } else {
            return;
        }

        //int i = 0;
        long len = future / delay;
        long count = 0;

//        boolean isauto = weakActivity.get().getPrefs().getBoolean("isauto", false);

        //			if(isauto){
        //				len = 3 * len;
        //			}

        if (weakActivity.get() != null) {
            if (loopCommands.size() > 0) {
                while (count < len) {
                    if (weakActivity.get() != null) {
                        weakActivity.get().incReadingCount();
                    } else {
                        return;
                    }

                    publishProgress(new Pair<>(1, loopCommands.get(0)));
                    try {
                        long half_delay = delay / 2;
                        Thread.sleep(half_delay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (loopCommands.size() > 1) {
                        publishProgress(new Pair<>(1, loopCommands.get(1)));
                        //
                        try {
                            long half_delay = delay / 2;
                            Thread.sleep(half_delay);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    //				byte [] arr = new byte[]{(byte) 0xFE,0x44,0x11,0x22,0x33,
                    // 0x44,

                    // 0x55};
                    //				Message msg = new Message();
                    //				msg.what = 0;
                    //				msg.obj = arr;
                    //				EToCMainActivity.mHandler.sendMessage(msg);

                    //future = future - delay;
                    //				if(i == 0){
                    //					i = 1;
                    //				}else{
                    //					i = 0;
                    //				}

                    //				try {
                    //					Thread.sleep(delay);
                    //				} catch (InterruptedException e) {
                    //					e.printStackTrace();
                    //				}

                    count++;
                }
            }
        }
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
        if (weakActivity.get() != null) {
            EToCMainActivity activity = weakActivity.get();

            activity.setTimerRunning(false);

            Toast.makeText(activity, "Timer Stopped", Toast.LENGTH_LONG).show();
        }
    }
}
