package com.ismet.usbterminal.mainscreen.tasks;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import androidx.core.util.Pair;
import android.widget.Toast;

import com.ismet.usbterminal.MainActivity;
import com.ismet.usbterminal.data.AppData;
import com.ismet.usbterminal.data.PrefConstants;
import com.ismet.usbterminal.mainscreen.EToCMainActivity;
import com.ismet.usbterminal.services.PullStateManagingService;
import com.proggroup.areasquarecalculator.utils.ToastUtils;

import java.io.File;
import java.io.FileFilter;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.StringTokenizer;

import kotlin.Deprecated;

@Deprecated(message = "Use MainViewModel instead")
public class SendDataToUsbTask extends AsyncTask<Long, Pair<Integer, String>, String> {

    private final List<String> simpleCommands;

    private final List<String> loopCommands;

    private final boolean autoPpm;

    private final WeakReference<MainActivity> weakActivity;
    private boolean useRecentDirectory;

    public SendDataToUsbTask(List<String> simpleCommands, List<String> loopCommands,
                             boolean autoPpm, MainActivity activity, boolean useRecentDirectory) {
        this.simpleCommands = simpleCommands;
        this.loopCommands = loopCommands;
        this.autoPpm = autoPpm;
        this.weakActivity = new WeakReference<>(activity);
        this.useRecentDirectory = useRecentDirectory;
    }

    public SendDataToUsbTask(List<String> simpleCommands, List<String> loopCommands,
                             boolean autoPpm, EToCMainActivity activity, boolean useRecentDirectory) {
        this.simpleCommands = simpleCommands;
        this.loopCommands = loopCommands;
        this.autoPpm = autoPpm;
        this.weakActivity = new WeakReference<>(null);
        this.useRecentDirectory = useRecentDirectory;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        MainActivity activity = weakActivity.get();
        if (activity != null && useRecentDirectory) {

            int ppm = activity.getPrefs().getInt(PrefConstants.KPPM, -1);
            //cal direcory
            if (ppm != -1) {
                File directory = new File(Environment.getExternalStorageDirectory(), AppData.CAL_FOLDER_NAME);
                File[] directoriesInside = directory.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.isDirectory();
                    }
                });
                if (directoriesInside != null && directoriesInside.length > 0) {
                    File recentDir = null;
                    for (File dir : directoriesInside) {
                        if (recentDir == null || dir.lastModified() > recentDir.lastModified()) {
                            recentDir = dir;
                        }
                    }
                    String name = recentDir.getName();
                    StringTokenizer tokenizer = new StringTokenizer(name, "_");
                    tokenizer.nextToken();
                    activity.setSubDirDate(tokenizer.nextToken() + "_" + tokenizer.nextToken());
                }
            }
        }
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
                publishProgress(new Pair<>(2, null));
            }

            weakActivity.get().waitForCooling();
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
            MainActivity activity = weakActivity.get();

            activity.setTimerRunning(false);

            Toast toast = Toast.makeText(activity, "Timer Stopped", Toast.LENGTH_LONG);
            ToastUtils.wrap(toast);
            toast.show();
        }
    }
}
