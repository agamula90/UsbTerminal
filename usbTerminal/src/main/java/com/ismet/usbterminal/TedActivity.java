package com.ismet.usbterminal;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.ismet.usbterminal.threads.FileWriterThread;
import com.proggroup.areasquarecalculator.activities.BaseAttachableActivity;
import com.proggroup.areasquarecalculator.utils.AutoExpandKeyboardUtils;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.AbstractChart;
import org.achartengine.chart.CubicLineChart;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.neofonie.mobile.app.android.widget.crouton.Crouton;
import de.neofonie.mobile.app.android.widget.crouton.Style;
import fr.xgouchet.texteditor.common.Constants;
import fr.xgouchet.texteditor.common.RecentFiles;
import fr.xgouchet.texteditor.common.Settings;
import fr.xgouchet.texteditor.common.TextFileUtils;
import fr.xgouchet.texteditor.ui.view.AdvancedEditText;
import fr.xgouchet.texteditor.undo.TextChangeWatcher;

import static fr.xgouchet.androidlib.data.FileUtils.deleteItem;
import static fr.xgouchet.androidlib.data.FileUtils.getCanonizePath;
import static fr.xgouchet.androidlib.data.FileUtils.renameItem;
import static fr.xgouchet.androidlib.ui.Toaster.showToast;
import static fr.xgouchet.androidlib.ui.activity.ActivityDecorator.addMenuItem;
import static fr.xgouchet.androidlib.ui.activity.ActivityDecorator.showMenuItemAsAction;

public class TedActivity extends BaseAttachableActivity implements Constants, TextWatcher {
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action" +
            ".USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action" +
            ".USB_DEVICE_DETACHED";

    // USB activity data
    // private UsbService usbService;
    boolean isUsbConnected = false;

    private TextView txtOutput;
    private ScrollView mScrollView;
    private Button sendButton, buttonClear, buttonMeasure;//,buttonCal
    private Button buttonOn1, buttonOn2, buttonPpm;//, buttonOff,buttonCal

    SharedPreferences prefs;

    // private TextView txtOutput1;
    // private ScrollView mScrollView1;
    // private Button sendButton1;
    // EditText input1;

    public static MyHandler mHandler;

    @Override
    public int getFragmentContainerId() {
        return R.id.fragment_container;
    }

    @Override
    public DrawerLayout getDrawerLayout() {
        return null;
    }

    @Override
    public int getToolbarId() {
        return R.id.toolbar;
    }

    @Override
    public int getLeftDrawerFragmentId() {
        return LEFT_DRAWER_FRAGMENT_ID_UNDEFINED;
    }

    @Override
    public FrameLayout getFrameLayout() {
        return (FrameLayout) findViewById(R.id.frame_container);
    }

    public static class MyHandler extends Handler {
        private final WeakReference<TedActivity> myActivity;

        public MyHandler(WeakReference<TedActivity> tedActivity) {
            super();
            this.myActivity = tedActivity;
        }

        public WeakReference<TedActivity> getActivity() {
            return myActivity;
        }
    }

    WeakReference<TedActivity> weakReference = new WeakReference<TedActivity>(this);

    //CountDownTimer ctimer;
    // String filename = "";
    int count_measure = 0, old_count_measure = 0;
    boolean isTimerRunning = false;

    XYSeries currentSeries;
    GraphicalView mChartView;
    XYMultipleSeriesDataset currentdataset;
    XYMultipleSeriesRenderer renderer;

    int readingCount = 0;
    // int idx_count = 0;
    int chart_idx = 0;
    String chart_date = "", sub_dir_date = "";
    //String chart_time = "";

    ExecutorService executor;

    HashMap<Integer, String> mapChartDate = new HashMap<Integer, String>();

    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    protected void onCreate(Bundle savedInstanceState) {
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        //        WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onCreate");

        executor = Executors.newSingleThreadExecutor();

        prefs = PreferenceManager.getDefaultSharedPreferences(TedActivity.this);

        Settings.updateFromPreferences(getSharedPreferences(PREFERENCES_NAME,
                MODE_PRIVATE));

        mHandler = new MyHandler(weakReference) {
            @Override
            public void handleMessage(Message msg) {
                // TODO Auto-generated method stub
                super.handleMessage(msg);

                WeakReference<TedActivity> callActivity = getActivity();

                switch (msg.what) {
                    case 0: {
                        // String data = (String) msg.obj;
                        // data = data.replace("\r", "");
                        // data = data.replace("\n", "");

                        // byte [] arrT1 = data.getBytes();
                        byte[] arrT1 = (byte[]) msg.obj;
                        // String strH="";
                        String data = "";
                        if (arrT1.length == 7) {
                            if ((String.format("%02X", arrT1[0]).equals("FE"))
                                    && (String.format("%02X", arrT1[1])
                                    .equals("44"))) {
                                // SENSOR Response
                                String strHex = "";
                                for (byte b : arrT1) {
                                    strHex = strHex + String.format("%02X-", b);
                                }
                                int end = strHex.length() - 1;
                                data = strHex.substring(0, end);

                                String strH = String.format("%02X%02X", arrT1[3],
                                        arrT1[4]);
                                int co2 = Integer.parseInt(strH, 16);

                                if (callActivity.get().renderer != null) {
                                    int yMax = (int) callActivity.get().renderer.getYAxisMax();
                                    if (co2 >= yMax) {
                                        // int vmax = (int) (co2 + (co2*15)/100f);
                                        // int vmax_extra = (int) Math.ceil(1.5 *
                                        // (co2/20)) ;
                                        // int vmax = co2 + vmax_extra;
                                        if (callActivity.get().currentSeries.getItemCount() == 0) {
                                            int vmax = 3 * co2;
                                            callActivity.get().renderer.setYAxisMax(vmax);
                                        } else {
                                            int vmax = (int) (co2 + (co2 * 15) / 100f);
                                            callActivity.get().renderer.setYAxisMax(vmax);
                                        }

                                    }

                                    // int yMin = (int) renderer.getYAxisMin();
                                    // if(yMin == 0){
                                    // int vmin = co2 - (co2 * (15/100));
                                    // renderer.setYAxisMin(vmin);
                                    // }else if(co2<yMin){
                                    // int vmin = co2 - (co2 * (15/100));
                                    // renderer.setYAxisMin(vmin);
                                    // }

                                    // int delay_v = prefs.getInt("delay", 2);
                                    // int duration_v = prefs.getInt("duration", 3);
                                    // int limit = (duration_v * 60)/delay_v;
                                    // if(readingCount != 1){
                                    // if((readingCount%limit) == 1){
                                    // //addNewSeries();
                                    // if(idx_count<=1){
                                    // // Toast.makeText(TedActivity.this,
                                    // // "Series Changed",
                                    // // Toast.LENGTH_LONG).show();
                                    // currentSeries =
                                    // currentdataset.getSeriesAt(idx_count+1);
                                    // // if(c == 0){
                                    // //
                                    // renderer.getSeriesRendererAt(0).setColor(Color.rgb(0,
                                    // 171, 234));
                                    // // }else if(c == 1){
                                    // //
                                    // renderer.getSeriesRendererAt(0).setColor(Color.RED);
                                    // // }
                                    //
                                    // //addNewSeries();
                                    // idx_count++;
                                    // }
                                    // }
                                    // }

                                    // XYSeries currentSeries =
                                    // currentdataset.getSeriesAt(0);

                                    // file writing
                                    // Toast.makeText(TedActivity.this, filename,
                                    // Toast.LENGTH_SHORT).show();

                                    // auto
                                    int delay_v = callActivity.get().prefs.getInt("delay", 2);
                                    int duration_v = callActivity.get().prefs.getInt("duration", 3);
                                    int rCount1 = (int) ((duration_v * 60) / delay_v);
                                    int rCount2 = (int) (duration_v * 60);
                                    boolean isauto = callActivity.get().prefs.getBoolean
                                            ("isauto", false);
                                    if (isauto) {
                                        if (callActivity.get().readingCount == rCount1) {
                                            callActivity.get().count_measure++;
                                            callActivity.get().chart_idx = 2;
                                            callActivity.get().currentSeries = callActivity.get()
                                                    .currentdataset
                                                    .getSeriesAt(1);
                                        } else if (callActivity.get().readingCount == rCount2) {
                                            callActivity.get().count_measure++;
                                            callActivity.get().chart_idx = 3;
                                            callActivity.get().currentSeries = callActivity.get()
                                                    .currentdataset
                                                    .getSeriesAt(2);
                                        }
                                    }
                                    //
                                    if (callActivity.get().count_measure != callActivity.get()
                                            .old_count_measure) {
                                        Date currentTime = new Date();
                                        // SimpleDateFormat formatter_date = new
                                        // SimpleDateFormat(
                                        // "yyyy_MM_dd_HH_mm_ss");
                                        SimpleDateFormat formatter_date = new SimpleDateFormat(
                                                "yyyyMMdd_HHmmss");
                                        // SimpleDateFormat formatter_time = new
                                        // SimpleDateFormat(
                                        // "HH_mm_ss");

                                        callActivity.get().chart_date = formatter_date
                                                .format(currentTime);

                                        if (callActivity.get().count_measure == 1) {
                                            callActivity.get().sub_dir_date = formatter_date
                                                    .format(currentTime);
                                        }
                                        // chart_time =
                                        // formatter_time.format(currentTime);
                                        // chart_idx++;
                                        callActivity.get().old_count_measure = callActivity.get()
                                                .count_measure;
                                        callActivity.get().mapChartDate.put(callActivity.get()
                                                .chart_idx, callActivity.get().chart_date);
                                    }

                                    if (callActivity.get().isTimerRunning) {
                                        callActivity.get().currentSeries.add(callActivity.get()
                                                .readingCount, co2);
                                        callActivity.get().mChartView.repaint();
//									executor.execute(new FileWriterThread(""
//											+ co2, "" + chart_idx, ""
//											+ count_measure, chart_date,
//											chart_time));
                                        int kppm = callActivity.get().prefs.getInt("kppm", -1);
                                        int volume = callActivity.get().prefs.getInt("volume", -1);

                                        String strppm = "";
                                        String strvolume = "";

                                        if (kppm == -1) {
                                            strppm = "_";
                                        } else {
                                            strppm = "_" + kppm;
                                        }

                                        if (volume == -1) {
                                            strvolume = "_";
                                        } else {
                                            strvolume = "_" + volume;
                                        }

                                        String str_uc = callActivity.get().prefs.getString("uc",
                                                "");
                                        if (strppm.equals("_")) {
                                            String filename1 = "";
                                            String dirname1 = "AEToC_MES_Files";
                                            String subDirname1 = "";

                                            filename1 = "MES_" + callActivity.get().chart_date +
                                                    strvolume + "_R" + callActivity.get()
                                                    .chart_idx + ".csv";
                                            //dirname1 = "AEToC_MES_Files";
                                            subDirname1 = "MES_" + callActivity.get()
                                                    .sub_dir_date + "_" + str_uc;//+"_"+strppm;

                                            callActivity.get().executor.execute(new
                                                    FileWriterThread(""
                                                    + co2, filename1, dirname1, subDirname1));
                                        } else {
                                            String filename2 = "";
                                            String dirname2 = "AEToC_CAL_Files";
                                            String subDirname2 = "";

                                            filename2 = "CAL_" + callActivity.get().chart_date +
                                                    strvolume + strppm + "_R" + callActivity.get
                                                    ().chart_idx + ".csv";
                                            //dirname2 = "AEToC_MES_Files";
                                            subDirname2 = "CAL_" + callActivity.get()
                                                    .sub_dir_date + "_" + str_uc;//strppm;//;

                                            callActivity.get().executor.execute(new
                                                    FileWriterThread(""
                                                    + co2, filename2, dirname2, subDirname2));
                                        }

                                    }

                                    if (co2 == 10000) {
                                        Toast.makeText(callActivity.get(), "Dilute sample", Toast
                                                .LENGTH_LONG).show();
                                    }
                                }

                                data += "\nCO2: " + co2 + " ppm";

                            } else {
//							String strHex = "";
//							for (byte b : arrT1) {
//								strHex = strHex + String.format("%02X-", b);
//							}
//							int end = strHex.length() - 1;
//							if (end != -1) {
//								data = strHex.substring(0, end);
//							}

                                // ASCII DATA
                                data = new String(arrT1);
                            }
                        } else {
//						String strHex = "";
//						for (byte b : arrT1) {
//							strHex = strHex + String.format("%02X-", b);
//						}
//						int end = strHex.length() - 1;
//						if (end != -1) {
//							data = strHex.substring(0, end);
//						}

                            // ASCII DATA
                            data = new String(arrT1);

                        }

                        //

                        appendText(callActivity.get().txtOutput, "Rx: " + data);
                        callActivity.get().mScrollView.smoothScrollTo(0, 0);

                        // txtOutput1.setText("Rx: " + strH + "\n"+
                        // txtOutput1.getText());
                        // mScrollView1.smoothScrollTo(0, 0);

                        // txtOutput.append("Rx: " + data + "\n");
                        // mScrollView.smoothScrollTo(0, txtOutput.getBottom());
                    }
                    break;
                    case 1: {
                        String command = (String) msg.obj;
                        sendCommand(command);
                    }
                    break;
                    default:
                        break;
                }
            }
        };

        //
        mReadIntent = true;

        // editor
        mEditor = (AdvancedEditText) findViewById(R.id.editor);
        mEditor.addTextChangedListener(this);
        mEditor.updateFromSettings();
        mWatcher = new TextChangeWatcher();
        mWarnedShouldQuit = false;
        mDoNotBackup = false;

        // search
        // mSearchLayout = findViewById(R.id.searchLayout);
        // mSearchInput = (EditText) findViewById(R.id.textSearch);
        // findViewById(R.id.buttonSearchClose).setOnClickListener(this);
        // findViewById(R.id.buttonSearchNext).setOnClickListener(this);
        // findViewById(R.id.buttonSearchPrev).setOnClickListener(this);

        // mHandler = new MyHandler(this);

        txtOutput = (TextView) findViewById(R.id.output);
        mScrollView = (ScrollView) findViewById(R.id.mScrollView);

        // Timer mTimer = new Timer();
        // mTimer.scheduleAtFixedRate(new TimerTask() {
        //
        // @Override
        // public void run() {
        // // TODO Auto-generated method stub
        // runOnUiThread(new Runnable() {
        //
        // @Override
        // public void run() {
        // // TODO Auto-generated method stub
        // txtOutput.append("asdf\n");
        // mScrollView.smoothScrollTo(0, txtOutput.getBottom());
        // }
        // });
        // }
        // }, 1000, 1000);


//		boolean isOn = prefs.getBoolean("ison", false);
//		if(isOn){
//			buttonOn.setText("Off");
//			//buttonOn.setTag("on");
//		}else{
//			buttonOn.setText("On");
//			//buttonOn.setTag("off");
//		}

        buttonOn1 = (Button) findViewById(R.id.buttonOn1);
        final String str_on_name1 = prefs.getString("on_name1", "On");
        //final String str_off_name1 = prefs.getString("off_name1", "");
        buttonOn1.setText(str_on_name1);
        buttonOn1.setTag("on");
        buttonOn1.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                // ASCII
                String str_on_name1t = prefs.getString("on_name1", "On");
                String str_off_name1t = prefs.getString("off_name1", "Off");

                String s = buttonOn1.getTag().toString();
                String command = "";//"/5H1000R";
                if (s.equals("on")) {
                    command = prefs.getString("on1", "");
                    buttonOn1.setText(str_off_name1t);
                    buttonOn1.setTag("off");
                } else {
                    command = prefs.getString("off1", "");
                    buttonOn1.setText(str_on_name1t);
                    buttonOn1.setTag("on");
                }

                appendText(txtOutput, "Tx: " + command);

                mScrollView.smoothScrollTo(0, 0);

                UsbService.write(command.getBytes());

                String cr = "\r";
                if (UsbService.serialPort != null) {
                    UsbService.write(cr.getBytes());
                }
            }
        });

        buttonOn1.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                // TODO Auto-generated method stub


                AlertDialog.Builder alertbuilder = new AlertDialog.Builder(
                        TedActivity.this);
                alertbuilder.setTitle("Set On/Off commands");

                LayoutInflater inflater = TedActivity.this.getLayoutInflater();
                View dialogView = inflater.inflate(
                        R.layout.layout_dialog_on_off, null);
                alertbuilder.setView(dialogView);

                final EditText editOn = (EditText) dialogView
                        .findViewById(R.id.editOn);
                final EditText editOff = (EditText) dialogView
                        .findViewById(R.id.editOff);
                final EditText editOn1 = (EditText) dialogView
                        .findViewById(R.id.editOn1);
                final EditText editOff1 = (EditText) dialogView
                        .findViewById(R.id.editOff1);


                String str_on = prefs.getString("on1", "");
                String str_off = prefs.getString("off1", "");
                String str_on_name = prefs.getString("on_name1", "On");
                String str_off_name = prefs.getString("off_name1", "Off");

                editOn.setText(str_on);
                editOff.setText(str_off);
                editOn1.setText(str_on_name);
                editOff1.setText(str_off_name);

                alertbuilder.setPositiveButton("Save",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                // TODO Auto-generated method stub
                                InputMethodManager inputManager = (InputMethodManager)
                                        getSystemService(Context.INPUT_METHOD_SERVICE);

                                inputManager.hideSoftInputFromWindow(
                                        editOn.getWindowToken(), 0);
                                inputManager.hideSoftInputFromWindow(
                                        editOff.getWindowToken(), 0);

                                String strOn = editOn.getText()
                                        .toString();
                                String strOff = editOff.getText()
                                        .toString();
                                String strOn1 = editOn1.getText()
                                        .toString();
                                String strOff1 = editOff1.getText()
                                        .toString();

                                if (strOn.equals("")
                                        || strOff.equals("") || strOn1.equals("")
                                        || strOff1.equals("")) {
                                    Toast.makeText(TedActivity.this,
                                            "Please enter all values",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }


                                Editor edit = prefs.edit();
                                edit.putString("on1", strOn);
                                edit.putString("off1", strOff);
                                edit.putString("on_name1", strOn1);
                                edit.putString("off_name1", strOff1);
                                edit.commit();

                                String s = buttonOn1.getTag().toString();
                                if (s.equals("on")) {
                                    buttonOn1.setText(strOn1);
                                } else {
                                    buttonOn1.setText(strOff1);
                                }

                                dialog.cancel();
                            }
                        });

                alertbuilder.setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                // TODO Auto-generated method stub
                                InputMethodManager inputManager = (InputMethodManager)
                                        getSystemService(Context.INPUT_METHOD_SERVICE);

                                inputManager.hideSoftInputFromWindow(
                                        editOn.getWindowToken(), 0);
                                inputManager.hideSoftInputFromWindow(
                                        editOff.getWindowToken(), 0);
                                dialog.cancel();
                            }
                        });

                alertbuilder.create().show();

                return true;
            }
        });

        buttonOn2 = (Button) findViewById(R.id.buttonOn2);
        final String str_on_name2 = prefs.getString("on_name2", "On");
        //final String str_off_name1 = prefs.getString("off_name1", "");
        buttonOn2.setText(str_on_name2);
        buttonOn2.setTag("on");
        buttonOn2.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                // ASCII
                String str_on_name2t = prefs.getString("on_name2", "On");
                String str_off_name2t = prefs.getString("off_name2", "Off");

                String s = buttonOn2.getTag().toString();
                String command = "";//"/5H1000R";
                if (s.equals("on")) {
                    command = prefs.getString("on2", "");
                    buttonOn2.setText(str_off_name2t);
                    buttonOn2.setTag("off");
                } else {
                    command = prefs.getString("off2", "");
                    buttonOn2.setText(str_on_name2t);
                    buttonOn2.setTag("on");
                }

                appendText(txtOutput, "Tx: " + command);
                mScrollView.smoothScrollTo(0, 0);

                UsbService.write(command.getBytes());

                String cr = "\r";
                if (UsbService.serialPort != null) {
                    UsbService.write(cr.getBytes());
                }
            }
        });

        buttonOn2.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                // TODO Auto-generated method stub


                AlertDialog.Builder alertbuilder = new AlertDialog.Builder(
                        TedActivity.this);
                alertbuilder.setTitle("Set On/Off commands");

                LayoutInflater inflater = TedActivity.this.getLayoutInflater();
                View dialogView = inflater.inflate(
                        R.layout.layout_dialog_on_off, null);
                alertbuilder.setView(dialogView);

                final EditText editOn = (EditText) dialogView
                        .findViewById(R.id.editOn);
                final EditText editOff = (EditText) dialogView
                        .findViewById(R.id.editOff);
                final EditText editOn1 = (EditText) dialogView
                        .findViewById(R.id.editOn1);
                final EditText editOff1 = (EditText) dialogView
                        .findViewById(R.id.editOff1);

                String str_on_name = prefs.getString("on_name2", "On");
                String str_off_name = prefs.getString("off_name2", "Off");


                String str_on = prefs.getString("on2", "");
                String str_off = prefs.getString("off2", "");

//				editOn.setText(str_on);
//				editOff.setText(str_off);

                editOn.setText(str_on);
                editOff.setText(str_off);
                editOn1.setText(str_on_name);
                editOff1.setText(str_off_name);

                alertbuilder.setPositiveButton("Save",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                // TODO Auto-generated method stub
                                InputMethodManager inputManager = (InputMethodManager)
                                        getSystemService(Context.INPUT_METHOD_SERVICE);

                                inputManager.hideSoftInputFromWindow(
                                        editOn.getWindowToken(), 0);
                                inputManager.hideSoftInputFromWindow(
                                        editOff.getWindowToken(), 0);

                                String strOn = editOn.getText()
                                        .toString();
                                String strOff = editOff.getText()
                                        .toString();
                                String strOn1 = editOn1.getText()
                                        .toString();
                                String strOff1 = editOff1.getText()
                                        .toString();

                                if (strOn.equals("")
                                        || strOff.equals("") || strOn1.equals("")
                                        || strOff1.equals("")) {
                                    Toast.makeText(TedActivity.this,
                                            "Please enter all values",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }


                                Editor edit = prefs.edit();
                                edit.putString("on2", strOn);
                                edit.putString("off2", strOff);
                                edit.putString("on_name2", strOn1);
                                edit.putString("off_name2", strOff1);
                                edit.commit();


                                String s = buttonOn2.getTag().toString();
                                if (s.equals("on")) {
                                    buttonOn2.setText(strOn1);
                                } else {
                                    buttonOn2.setText(strOff1);
                                }

                                dialog.cancel();
                            }
                        });

                alertbuilder.setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                // TODO Auto-generated method stub
                                InputMethodManager inputManager = (InputMethodManager)
                                        getSystemService(Context.INPUT_METHOD_SERVICE);

                                inputManager.hideSoftInputFromWindow(
                                        editOn.getWindowToken(), 0);
                                inputManager.hideSoftInputFromWindow(
                                        editOff.getWindowToken(), 0);
                                dialog.cancel();
                            }
                        });

                alertbuilder.create().show();

                return true;
            }
        });

//		buttonOff = (Button) findViewById(R.id.buttonOff);
//		buttonOff.setOnClickListener(new OnClickListener() {
//
//			@Override
//			public void onClick(View v) {
//				// TODO Auto-generated method stub
//				// ASCII
//				String command = "/5H0000R";
//				UsbService.write(command.getBytes());
//
//				String cr = "\r";
//				if (UsbService.serialPort != null) {
//					UsbService.write(cr.getBytes());
//				}
//			}
//		});

        buttonPpm = (Button) findViewById(R.id.buttonPpm);
        buttonPpm.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

            }
        });

        sendButton = (Button) findViewById(R.id.buttonSend);
        sendButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
//				if (isTimerRunning) {
//					Toast.makeText(TedActivity.this,
//							"Timer is running. Please wait", Toast.LENGTH_SHORT)
//							.show();
//				} else {
//					sendMessage();
//				}

                sendMessage();

            }
        });

        mEditor.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId,
                                          KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    // sendMessage();
                    handled = true;
                }
                return handled;
            }

        });

        // input1 = (EditText) findViewById(R.id.input1);
        // txtOutput1 = (TextView) findViewById(R.id.output1);
        // mScrollView1 = (ScrollView) findViewById(R.id.mScrollView1);
        // sendButton1 = (Button) findViewById(R.id.btnHex);
        //
        // sendButton1.setOnClickListener(new OnClickListener() {
        //
        // @Override
        // public void onClick(View v) {
        // String command = input1.getText().toString();
        // if(command.equals("")){
        // Toast.makeText(TedActivity.this,
        // "Please enter command", 1000).show();
        // return;
        // }
        //
        // if (UsbService.serialPort != null) {
        // String [] arr = command.split("-");
        //
        // byte[] bytes = new byte[arr.length];
        // for(int i=0;i<bytes.length;i++){
        // bytes[i] = (byte) Integer.parseInt(arr[i], 16);
        // }
        // UsbService.write(bytes);
        //
        // // String data = "";
        // // for(String s:arr){
        // // data = data + s;
        // // }
        // // byte [] arrT1 = data.getBytes();
        // // String strH="";
        // // for(byte b:arrT1){
        // // strH = strH + String.format("0x%02X", b);
        // // }
        // //
        // // UsbService.write(strH.getBytes());
        // } else {
        // Toast.makeText(TedActivity.this,
        // "serial port not found", 1000).show();
        // }
        //
        // txtOutput.setText("Tx: " + command + "\n"+txtOutput.getText());
        // mScrollView.smoothScrollTo(0, 0);
        //
        // txtOutput1.setText("Tx: " + command + "\n"+txtOutput1.getText());
        // mScrollView1.smoothScrollTo(0, 0);
        // }
        // });

        // PackageInfo pInfo;
        // try {
        // pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        // String version = pInfo.versionName;
        // TextView txtVersion = (TextView) findViewById(R.id.txtVersion);
        // txtVersion.setText("Version: "+version);
        // } catch (NameNotFoundException e) {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }

        buttonClear = (Button) findViewById(R.id.buttonClear);
        buttonMeasure = (Button) findViewById(R.id.buttonMeasure);
        //buttonCal = (Button) findViewById(R.id.buttonCal);

        buttonClear.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                if (isTimerRunning) {
                    Toast.makeText(TedActivity.this,
                            "Timer is running. Please wait", Toast.LENGTH_SHORT)
                            .show();
                    return;
                }

                CharSequence[] items = new CharSequence[]{"Tx", "LM",
                        "Chart 1", "Chart 2", "Chart 3"};
                boolean[] checkedItems = new boolean[]{false, false, false,
                        false, false};
                final HashMap<Integer, Boolean> mapItemsState = new HashMap<>();

                AlertDialog.Builder alert = new AlertDialog.Builder(
                        TedActivity.this);
                alert.setTitle("Select items");
                alert.setMultiChoiceItems(items, checkedItems,
                        new DialogInterface.OnMultiChoiceClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which, boolean isChecked) {
                                // TODO Auto-generated method stub
                                mapItemsState.put(which, isChecked);
                            }
                        });
                alert.setPositiveButton("Clear",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                // TODO Auto-generated method stub
                                if (mapItemsState.containsKey(0)) {
                                    if (mapItemsState.get(0)) {
                                        mEditor.setText("");
                                    }
                                }

                                if (mapItemsState.containsKey(1)) {
                                    if (mapItemsState.get(1)) {
                                        txtOutput.setText("");
                                    }
                                }

                                boolean isCleard = false;
                                if (mapItemsState.containsKey(2)) {
                                    if (mapItemsState.get(2)) {
                                        // txtOutput.setText("");
                                        if (currentdataset != null) {
                                            currentdataset.getSeriesAt(0)
                                                    .clear();
                                            //mChartView.repaint();
                                            isCleard = true;
                                        }

                                        if (mapChartDate.containsKey(1)) {
                                            deleteFiles(mapChartDate.get(1)
                                                    , "_R1");
                                        }

                                    }
                                }

                                if (mapItemsState.containsKey(3)) {
                                    if (mapItemsState.get(3)) {
                                        // txtOutput.setText("");
                                        if (currentdataset != null) {
                                            currentdataset.getSeriesAt(1)
                                                    .clear();
                                            //mChartView.repaint();
                                            isCleard = true;
                                        }

                                        if (mapChartDate.containsKey(2)) {
                                            deleteFiles(mapChartDate.get(2)
                                                    , "_R2");
                                        }
                                    }
                                }

                                if (mapItemsState.containsKey(4)) {
                                    if (mapItemsState.get(4)) {
                                        // txtOutput.setText("");
                                        if (currentdataset != null) {
                                            currentdataset.getSeriesAt(2)
                                                    .clear();
                                            //mChartView.repaint();
                                            isCleard = true;
                                        }

                                        if (mapChartDate.containsKey(3)) {
                                            deleteFiles(mapChartDate.get(3)
                                                    , "_R3");
                                        }
                                    }
                                }

                                if (isCleard) {
                                    renderer.setLabelsTextSize(12);
                                    mChartView.repaint();
                                    renderer.setLabelsTextSize(12);
                                }
                                dialog.cancel();
                            }
                        });
                alert.setNegativeButton("Close",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                // TODO Auto-generated method stub
                                dialog.cancel();
                            }
                        });
                alert.create().show();
            }
        });

        buttonMeasure.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                if (mEditor.getText().toString().equals("")) {
                    Toast.makeText(TedActivity.this, "Please enter command",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                if (isTimerRunning) {
                    Toast.makeText(TedActivity.this,
                            "Timer is running. Please wait", Toast.LENGTH_SHORT)
                            .show();
                    return;
                }

                if (renderer != null) {
                    XYSeries[] arrSeries = currentdataset.getSeries();

                    int i = 0;
                    boolean isChart1Clear = true;
                    boolean isChart2Clear = true;
                    boolean isChart3Clear = true;

                    for (XYSeries series : arrSeries) {
                        if (series.getItemCount() > 0) {
                            switch (i) {
                                case 0:
                                    isChart1Clear = false;
                                    break;
                                case 1:
                                    isChart2Clear = false;
                                    break;
                                case 2:
                                    isChart3Clear = false;
                                    break;
                                default:
                                    break;
                            }
                        }
                        i++;
                    }

                    if ((!isChart1Clear) && (!isChart2Clear)
                            && (!isChart3Clear)) {
                        Toast.makeText(
                                TedActivity.this,
                                "No chart available. Please clear one of the charts",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                AlertDialog.Builder alertbuilder = new AlertDialog.Builder(
                        TedActivity.this);
                alertbuilder.setTitle("Start Measure");

                LayoutInflater inflater = TedActivity.this.getLayoutInflater();
                View dialogView = inflater.inflate(
                        R.layout.layout_dialog_measure, null);
                alertbuilder.setView(dialogView);

                final EditText editDelay = (EditText) dialogView
                        .findViewById(R.id.editDelay);
                final EditText editDuration = (EditText) dialogView
                        .findViewById(R.id.editDuration);
                final EditText editKnownPpm = (EditText) dialogView
                        .findViewById(R.id.editKnownPpm);
                final EditText editVolume = (EditText) dialogView
                        .findViewById(R.id.editVolume);
                final EditText editUserComment = (EditText) dialogView
                        .findViewById(R.id.editUserComment);

                //chkAutoManual
                final CheckBox chkAutoManual = (CheckBox) dialogView
                        .findViewById(R.id.chkAutoManual);
                final CheckBox chkKnownPpm = (CheckBox) dialogView
                        .findViewById(R.id.chkKnownPpm);
                final LinearLayout llkppm = (LinearLayout) dialogView
                        .findViewById(R.id.llkppm);
                final LinearLayout ll_user_comment = (LinearLayout) dialogView
                        .findViewById(R.id.ll_user_comment);

                int delay_v = prefs.getInt("delay", 2);
                int duration_v = prefs.getInt("duration", 3);
                int volume = prefs.getInt("volume", 20);
                int kppm = prefs.getInt("kppm", -1);
                String user_comment = prefs.getString("uc", "");

                editDelay.setText("" + delay_v);
                editDuration.setText("" + duration_v);
                editVolume.setText("" + volume);
                editUserComment.setText(user_comment);

                if (kppm != -1) {
                    editKnownPpm.setText("" + kppm);
                }

                boolean isauto = prefs.getBoolean("isauto", false);
                if (isauto) {
                    chkAutoManual.setChecked(true);
                } else {
                    chkAutoManual.setChecked(false);
                }

                chkKnownPpm.setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        // TODO Auto-generated method stub
                        if (isChecked) {
                            editKnownPpm.setEnabled(true);
                            llkppm.setVisibility(View.VISIBLE);
                            //ll_user_comment.setVisibility(View.GONE);
                        } else {
                            editKnownPpm.setEnabled(false);
                            llkppm.setVisibility(View.GONE);
                            //ll_user_comment.setVisibility(View.VISIBLE);
                        }
                    }
                });

                //Editor edit = prefs.edit();
                //edit.putInt("kppm", -1);
                //edit.putInt("volume", -1);
                //edit.putString("uc", "");
                //edit.commit();

                // Button btnSave = (Button)
                // dialogView.findViewById(R.id.btnSave);
                // Button btnCancel = (Button)
                // dialogView.findViewById(R.id.btnCancel);
                //
                // btnSave.setOnClickListener(new OnClickListener() {
                //
                // @Override
                // public void onClick(View v) {
                // // TODO Auto-generated method stub
                //
                // }
                // });
                //
                // btnCancel.setOnClickListener(new OnClickListener() {
                //
                // @Override
                // public void onClick(View v) {
                // // TODO Auto-generated method stub
                //
                // }
                // });

                alertbuilder.setPositiveButton("Save",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                // TODO Auto-generated method stub
                                InputMethodManager inputManager = (InputMethodManager)
                                        getSystemService(Context.INPUT_METHOD_SERVICE);

                                inputManager.hideSoftInputFromWindow(
                                        editDelay.getWindowToken(), 0);
                                inputManager.hideSoftInputFromWindow(
                                        editDuration.getWindowToken(), 0);

                                String strDelay = editDelay.getText()
                                        .toString();
                                String strDuration = editDuration.getText()
                                        .toString();

                                if (strDelay.equals("")
                                        || strDuration.equals("")) {
                                    Toast.makeText(TedActivity.this,
                                            "Please enter all values",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }

                                if (chkKnownPpm.isChecked()) {
                                    String strkPPM = editKnownPpm.getText()
                                            .toString();
                                    if (strkPPM.equals("")) {
                                        Toast.makeText(TedActivity.this,
                                                "Please enter ppm values",
                                                Toast.LENGTH_LONG).show();
                                        return;
                                    } else {
                                        int kppm = Integer.parseInt(strkPPM);
                                        Editor edit = prefs.edit();
                                        edit.putInt("kppm", kppm);
                                        edit.commit();
                                    }
                                }

                                //else

                                {
                                    String str_uc = editUserComment.getText().toString();
                                    if (str_uc.equals("")) {
                                        Toast.makeText(TedActivity.this,
                                                "Please enter comments",
                                                Toast.LENGTH_LONG).show();
                                        return;
                                    } else {
                                        Editor edit = prefs.edit();
                                        edit.putString("uc", str_uc);
                                        edit.commit();
                                    }
                                }

                                String strVolume = editVolume.getText()
                                        .toString();
                                if (strVolume.equals("")) {
                                    Toast.makeText(TedActivity.this,
                                            "Please enter volume values",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                } else {
                                    int volume = Integer.parseInt(strVolume);
                                    Editor edit = prefs.edit();
                                    edit.putInt("volume", volume);
                                    edit.commit();
                                }

                                boolean b = chkAutoManual.isChecked();
                                Editor edit = prefs.edit();
                                edit.putBoolean("isauto", b);
                                edit.commit();

                                int delay = Integer.parseInt(strDelay);
                                int duration = Integer.parseInt(strDuration);

                                if ((delay == 0) || (duration == 0)) {
                                    Toast.makeText(TedActivity.this,
                                            "zero is not allowed",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                } else {

                                    // resest so user can set new delay or
                                    // duration
                                    // if(count_measure == 4){
                                    // old_count_measure=0;
                                    // count_measure=0;
                                    // }

                                    if (count_measure == 0) {
                                        getXYChart(duration, delay);
                                    }

                                    count_measure++;

                                    edit = prefs.edit();
                                    edit.putInt("delay", delay);
                                    edit.putInt("duration", duration);
                                    edit.commit();

                                    long future = duration * 60 * 1000;
                                    long delay_timer = delay * 1000;

                                    if (currentdataset.getSeriesAt(0)
                                            .getItemCount() == 0) {
                                        chart_idx = 1;
                                        readingCount = 0;
                                        currentSeries = currentdataset
                                                .getSeriesAt(0);
                                    } else if (currentdataset.getSeriesAt(1)
                                            .getItemCount() == 0) {
                                        chart_idx = 2;
                                        readingCount = (duration * 60) / delay;
                                        currentSeries = currentdataset
                                                .getSeriesAt(1);
                                    } else if (currentdataset.getSeriesAt(2)
                                            .getItemCount() == 0) {
                                        chart_idx = 3;
                                        readingCount = duration * 60;
                                        currentSeries = currentdataset
                                                .getSeriesAt(2);
                                    }

                                    // collect commands
                                    if (!mEditor.getText().toString().equals("")) {
                                        String multiLines = mEditor.getText().toString();
                                        String[] commands;
                                        String delimiter = "\n";

                                        commands = multiLines.split(delimiter);

                                        ArrayList<String> simpleCommands = new ArrayList<String>();
                                        ArrayList<String> loopCommands = new ArrayList<String>();
                                        boolean isLoop = false;
                                        int loopcmd1Idx = -1, loopcmd2Idx = -1;
                                        for (int i = 0; i < commands.length; i++) {
                                            String command = commands[i];
                                            //Log.d("command", command);
                                            if ((command != null) && (!command.equals(""))
                                                    && (!command.equals("\n"))) {
                                                if (command.contains("loop")) {
                                                    isLoop = true;
                                                    String lineNos = command.replace("loop", "");
                                                    lineNos = lineNos.replace("\n", "");
                                                    lineNos = lineNos.replace("\r", "");
                                                    lineNos = lineNos.trim();

                                                    String line1 = lineNos.substring(0, (lineNos
                                                            .length() / 2));
                                                    String line2 = lineNos.substring(lineNos
                                                            .length() / 2, lineNos.length());

                                                    loopcmd1Idx = Integer.parseInt(line1) - 1;
                                                    loopcmd2Idx = Integer.parseInt(line2) - 1;
                                                } else if (isLoop) {
                                                    if (i == loopcmd1Idx) {
                                                        loopCommands.add(command);
                                                    } else if (i == loopcmd2Idx) {
                                                        loopCommands.add(command);
                                                        isLoop = false;
                                                    }
                                                } else {
                                                    simpleCommands.add(command);
                                                }
                                            }
                                        }
                                        new SendCommandTask(simpleCommands, loopCommands).execute
                                                (new Long[]{future, delay_timer});
                                    }
                                    // end collect commands


//									ctimer = new CountDownTimer(future,
//											delay_timer) {
//
//										@Override
//										public void onTick(
//												long millisUntilFinished) {
//											// TODO Auto-generated method stub
//											readingCount = readingCount + 1;
//											sendMessage();
//
////											byte [] arr = new byte[]{(byte) 0xFE,0x44,0x11,
// 0x22,0x33,0x44,0x55};
////											Message msg = new Message();
////											msg.what = 0;
////											msg.obj = arr;
////											TedActivity.mHandler.sendMessage(msg);
//										}
//
//										@Override
//										public void onFinish() {
//											// TODO Auto-generated method stub
//											readingCount = readingCount + 1;
//											sendMessage();
//
////											byte [] arr = new byte[]{(byte) 0xFE,0x44,0x11,
// 0x22,0x33,0x44,0x55};
////											Message msg = new Message();
////											msg.what = 0;
////											msg.obj = arr;
////											TedActivity.mHandler.sendMessage(msg);
//
//											isTimerRunning = false;
//											Toast.makeText(TedActivity.this,
//													"Timer Finish",
//													Toast.LENGTH_LONG).show();
//										}
//									};
//
//									Toast.makeText(TedActivity.this,
//											"Timer Started", Toast.LENGTH_LONG)
//											.show();
//									isTimerRunning = true;
//									ctimer.start();

                                }
                                dialog.cancel();
                            }
                        });

                alertbuilder.setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                // TODO Auto-generated method stub
                                InputMethodManager inputManager = (InputMethodManager)
                                        getSystemService(Context.INPUT_METHOD_SERVICE);

                                inputManager.hideSoftInputFromWindow(
                                        editDelay.getWindowToken(), 0);
                                inputManager.hideSoftInputFromWindow(
                                        editDuration.getWindowToken(), 0);
                                dialog.cancel();
                            }
                        });

                alertbuilder.create().show();
            }
        });

//		buttonCal.setOnClickListener(new OnClickListener() {
//
//			@Override
//			public void onClick(View v) {
//				// TODO Auto-generated method stub
//				AlertDialog.Builder alertbuilder = new AlertDialog.Builder(
//						TedActivity.this);
//				alertbuilder.setTitle("Start Calibration");
//
//				LayoutInflater inflater = TedActivity.this.getLayoutInflater();
//				View dialogView = inflater.inflate(
//						R.layout.layout_dialog_cal, null);
//				alertbuilder.setView(dialogView);
//
//				final EditText editKnownPpm = (EditText) dialogView
//						.findViewById(R.id.editKnownPpm);
//
//
//				int ppm = prefs.getInt("ppm", 1000);
//				//int duration_v = prefs.getInt("duration", 3);
//
//				editKnownPpm.setText("");
//
//				alertbuilder.setPositiveButton("Save",
//						new DialogInterface.OnClickListener() {
//
//							@Override
//							public void onClick(DialogInterface dialog,
//									int which) {
//								// TODO Auto-generated method stub
//								InputMethodManager inputManager = (InputMethodManager)
// getSystemService(Context.INPUT_METHOD_SERVICE);
//
//								inputManager.hideSoftInputFromWindow(
//										editKnownPpm.getWindowToken(), 0);
//
//
//								String strKnownPpm = editKnownPpm.getText()
//										.toString();
//
//
//								if (strKnownPpm.equals("")) {
//									Toast.makeText(TedActivity.this,
//											"Please enter all values",
//											Toast.LENGTH_LONG).show();
//									return;
//								}
//
//								int kppm = Integer.parseInt(strKnownPpm);
//
//								if (kppm == 0) {
//									Toast.makeText(TedActivity.this,
//											"zero is not allowed",
//											Toast.LENGTH_LONG).show();
//									return;
//								} else {
//									Editor edit = prefs.edit();
//									edit.putInt("ppm", kppm);
//									edit.commit();
//
//									Toast.makeText(TedActivity.this,
//											"PPM values stored in preference", Toast.LENGTH_LONG)
//											.show();
//									isTimerRunning = true;
//
//
//								}
//								dialog.cancel();
//							}
//						});
//
//				alertbuilder.setNegativeButton("Cancel",
//						new DialogInterface.OnClickListener() {
//
//							@Override
//							public void onClick(DialogInterface dialog,
//									int which) {
//								// TODO Auto-generated method stub
//								InputMethodManager inputManager = (InputMethodManager)
// getSystemService(Context.INPUT_METHOD_SERVICE);
//
//								inputManager.hideSoftInputFromWindow(
//										editKnownPpm.getWindowToken(), 0);
//
//								dialog.cancel();
//							}
//						});
//
//				alertbuilder.create().show();
//			}
//		});
        setFilters();
        startService(new Intent(TedActivity.this, UsbService.class));

        int delay_v = prefs.getInt("delay", 2);
        int duration_v = prefs.getInt("duration", 3);
        getXYChart(duration_v, delay_v);


        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayUseLogoEnabled(true);
        actionBar.setLogo(R.drawable.ic_launcher);

        TextView titleView = (TextView) actionBar.getCustomView().findViewById(R.id.title);
        titleView.setTextColor(Color.WHITE);
        ((RelativeLayout.LayoutParams) titleView.getLayoutParams()).addRule(RelativeLayout
                .CENTER_HORIZONTAL, 0);

        // getTimeChart();
    }

    private void appendText(TextView txtOutput, String text) {
        if(!TextUtils.isEmpty(txtOutput.getText())) {
            txtOutput.setText(text + "\n"
                    + txtOutput.getText());
        } else {
            txtOutput.setText(text);
        }
    }

    @Override
    public int getLayoutId() {
        return R.layout.layout_editor;
    }

    @Override
    public Fragment getFirstFragment() {
        return new EmptyFragment();
    }

    public class SendCommandTask extends AsyncTask<Long, Integer, String> {
        ArrayList<String> simpleCommands = new ArrayList<>();
        ArrayList<String> loopCommands = new ArrayList<>();

        public SendCommandTask(ArrayList<String> simpleCommands, ArrayList<String> loopCommands) {
            this.simpleCommands = simpleCommands;
            this.loopCommands = loopCommands;
        }

        @Override
        protected String doInBackground(Long... params) {
            // TODO Auto-generated method stub
            Long future = params[0];
            Long delay = params[1];

            boolean isauto = prefs.getBoolean("isauto", false);
            if (isauto) {
                for (int l = 0; l < 3; l++) {
                    processChart(future, delay);
                }
            } else {
                processChart(future, delay);
            }


            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            // TODO Auto-generated method stub
            super.onPostExecute(result);
            isTimerRunning = false;

            Toast.makeText(TedActivity.this,
                    "Timer Stopped", Toast.LENGTH_LONG)
                    .show();
        }

        public void processChart(long future, long delay) {
            for (int i = 0; i < simpleCommands.size(); i++) {
                if (simpleCommands.get(i).contains("delay")) {
                    int delayC = Integer.parseInt(simpleCommands.get(i).replace("delay", "").trim
                            ());
                    try {
                        Thread.sleep(delayC);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } else {
                    Message msg = new Message();
                    msg.what = 1;
                    msg.obj = simpleCommands.get(i);
                    mHandler.sendMessage(msg);
                }


            }

            isTimerRunning = true;
            //int i = 0;
            long len = future / delay;
            long count = 0;

            //boolean isauto = prefs.getBoolean("isauto", false);

//			if(isauto){
//				len = 3 * len;
//			}

            while (count < len) {
                readingCount = readingCount + 1;


                String cmd = loopCommands.get(0);
                Message msg = new Message();
                msg.what = 1;
                msg.obj = cmd;
                mHandler.sendMessage(msg);
//
                try {
                    long half_delay = delay / 2;
                    Thread.sleep(half_delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
//
                cmd = loopCommands.get(1);
                msg = new Message();
                msg.what = 1;
                msg.obj = cmd;
                mHandler.sendMessage(msg);
//
                try {
                    long half_delay = delay / 2;
                    Thread.sleep(half_delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

//				byte [] arr = new byte[]{(byte) 0xFE,0x44,0x11,0x22,0x33,0x44,0x55};
//				Message msg = new Message();
//				msg.what = 0;
//				msg.obj = arr;
//				TedActivity.mHandler.sendMessage(msg);

                //future = future - delay;
//				if(i == 0){
//					i = 1;
//				}else{
//					i = 0;
//				}

//				try {
//					Thread.sleep(delay);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}

                count++;
            }
        }
    }

    public void deleteFiles(String date, String chartidx) {
        File dir = new File(Environment.getExternalStorageDirectory()
                + "/AEToC_MES_Files");
        String[] filenameArry = dir.list();
        if (filenameArry != null) {
            for (int i = 0; i < filenameArry.length; i++) {
                File subdir = new File(Environment.getExternalStorageDirectory()
                        + "/AEToC_MES_Files/" + filenameArry[i]);
                if (subdir.isDirectory()) {
                    String[] filenameArry1 = subdir.list();
                    for (int j = 0; j < filenameArry1.length; j++) {
                        if (filenameArry1[j].contains(date) && filenameArry1[j].contains
                                (chartidx)) {
                            File f = new File(subdir.getAbsolutePath()
                                    + "/" + filenameArry1[j]);
                            f.delete();
                        }
                    }
                }

            }
        }

        dir = new File(Environment.getExternalStorageDirectory()
                + "/AEToC_CAL_Files");
        filenameArry = dir.list();
        if (filenameArry != null) {
            for (int i = 0; i < filenameArry.length; i++) {
                File subdir = new File(Environment.getExternalStorageDirectory()
                        + "/AEToC_CAL_Files/" + filenameArry[i]);
                if (subdir.isDirectory()) {
                    String[] filenameArry1 = subdir.list();
                    for (int j = 0; j < filenameArry1.length; j++) {
                        if (filenameArry1[j].contains(date) && filenameArry1[j].contains
                                (chartidx)) {
                            File f = new File(subdir.getAbsolutePath()
                                    + "/" + filenameArry1[j]);
                            f.delete();
                        }
                    }
                }

            }
        }
    }

    // public static boolean deleteDir(File file) {
    // if (file != null && file.isFile()) {
    // file.delete();
    // }
    //
    // return dir.delete();
    // }

    private void addNewSeries() {
        String[] titles = new String[]{"Reponse %"};// "Crete Air Temperature",
        double[] xvArray = new double[1];
        double[] yvArray = new double[1];

        List<double[]> x = new ArrayList<double[]>();
        for (int i = 0; i < titles.length; i++) {
            // x.add(new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
            // 12,13,14,15,16,17,18,19,20,21,22,23,24,25 });
            x.add(xvArray);
        }

        List<double[]> values = new ArrayList<double[]>();
        // values.add(new double[] { 9, 10, 11, 15, 19, 23, 26, 25, 22, 18, 13,
        // 10, 9, 10, 11, 15, 19, 23, 26, 25, 22, 18, 13, 10, 9 });
        values.add(yvArray);

        renderer.addSeriesRenderer(getSeriesRenderer(Color.RED));
        XYSeries series = new XYSeries(titles[0]);
        currentSeries = series;
        currentdataset.addSeries(series);
        // mChartView.invalidate();
        // addXYSeries(currentdataset, titles, x, values, 0);

    }

    private XYSeriesRenderer getSeriesRenderer(final int color) {
        final XYSeriesRenderer r = new XYSeriesRenderer();
        r.setLineWidth(1.0f);
        r.setFillPoints(false);
        r.setDisplayChartValues(false);
        return r;
    }

    // ArrayList<String> xvList = new ArrayList<String>();
    // ArrayList<Double> yvList = new ArrayList<Double>();

    private void getXYChart(int mins, int secs) {
        // chart 2
        String[] titles = new String[]{"ppm", "ppm", "ppm"};// ,"","" };//
        // "Crete Air Temperature",

        // xvList.clear();
        // yvList.clear();

        // InputStream is = null;
        // try {
        // for (int i = 0; i < 4; i++) {
        // is = getAssets().open("data2.csv");
        // BufferedReader reader = new BufferedReader(
        // new InputStreamReader(is));
        // String line = null;
        // // int c = 0;
        // try {
        // while ((line = reader.readLine()) != null) {
        // // if (c != 0) {
        // if (!line.equals("")) {
        // String[] RowData = line.split(",");
        // xvList.add(RowData[0]);
        // yvList.add(Double.parseDouble(RowData[1]));
        // //yvList.add(0d);
        // }
        //
        // // }
        // // c++;
        // }
        //
        // } catch (Exception e) {
        // e.printStackTrace();
        // } finally {
        //
        // }
        // }
        // } catch (Exception e) {
        // // TODO: handle exception
        // e.printStackTrace();
        // }
        //
        double[] xvArray = new double[1];
        double[] yvArray = new double[1];

        // for (int i = 1; i <= xvList.size(); i++) {
        // xvArray[i - 1] = i;
        // }
        //
        // for (int i = 1; i <= yvList.size(); i++) {
        // //yvArray[i - 1] = yvList.get(i - 1);
        // yvArray[i - 1] = 0;
        // }

        List<double[]> x = new ArrayList<double[]>();
        for (int i = 0; i < titles.length; i++) {
            // x.add(new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
            // 12,13,14,15,16,17,18,19,20,21,22,23,24,25 });
            // xvArray[0] = i * ((mins * 60) / secs);
            x.add(xvArray);
        }

        List<double[]> values = new ArrayList<double[]>();
        // values.add(new double[] { 9, 10, 11, 15, 19, 23, 26, 25, 22, 18, 13,
        // 10, 9, 10, 11, 15, 19, 23, 26, 25, 22, 18, 13, 10, 9 });
        values.add(yvArray);
        // values.add(yvArray);
        // values.add(yvArray);

        int[] colors = new int[]{Color.BLACK, Color.RED, Color.BLUE};// };//
        // ,
        // Color.rgb(200,
        // 150,
        // 0)
        PointStyle[] styles = new PointStyle[]{PointStyle.CIRCLE,
                PointStyle.CIRCLE, PointStyle.CIRCLE};// };//
        // PointStyle.CIRCLE,
        renderer = buildRenderer(colors, styles);
        renderer.setPointSize(0f);
        int length = renderer.getSeriesRendererCount();
        for (int i = 0; i < length; i++) {
            XYSeriesRenderer r = (XYSeriesRenderer) renderer
                    .getSeriesRendererAt(i);
            r.setLineWidth(1.5f);
            r.setFillPoints(false);
            r.setDisplayChartValues(false);
        }

        int[] xlables = new int[4];
        int m = 0;
        for (int i = 0; i < 4; i++) {
            xlables[i] = m;
            m = m + mins;
        }

        // String[] xlables={"0","5","10","15"};

        int j = 0;
        for (int i = 0; i < xlables.length; i++) {
            if (xlables[i] == 0) {
                renderer.addXTextLabel(j, "");
            } else {
                renderer.addXTextLabel(j, "" + xlables[i]);
            }

            j = j + ((mins * 60) / secs);
        }

        double maxX = 3 * ((mins * 60) / secs);

        // renderer.setXLabelsAlign(Align.CENTER);
        // renderer.setXLabels(0);

        // Collections.sort(yvList);
        // // double minY = yvList.get(0);
        // double maxY = yvList.get(yvList.size() - 1);
        //
        // double segment_y = (maxY) / yvList.size();
        // maxY = maxY + segment_y;
        //
        // // Log.d("minY", ""+minY);
        // // Log.d("maxY", ""+maxY);
        //
        // double maxX = xvList.size();// 4 *
        // Weather data
        setChartSettings(renderer, "", "minutes", "ppm", 0, maxX, 0, 10,
                Color.LTGRAY, Color.WHITE);

        renderer.setXLabels(0);
        renderer.setYLabels(15);
        renderer.setLabelsTextSize(12);
        renderer.setShowGrid(true);
        renderer.setShowCustomTextGrid(true);
        renderer.setGridColor(Color.rgb(136, 136, 136));
        renderer.setBackgroundColor(Color.WHITE);
        renderer.setApplyBackgroundColor(true);
        renderer.setMargins(new int[]{0, 60, 0, 0});
        renderer.setXLabelsAlign(Align.RIGHT);
        renderer.setYLabelsAlign(Align.RIGHT);
        renderer.setYLabelsColor(0, Color.rgb(0, 171, 234));
        renderer.setYLabelsVerticalPadding(-15);
        renderer.setXLabelsPadding(-5f);
        renderer.setZoomButtonsVisible(false);
        // renderer.setPanLimits(new double[] { -10, 20, -10, 40 });
        // renderer.setZoomLimits(new double[] { -10, 20, -10, 40 });
        renderer.setZoomEnabled(false, false);
        renderer.setPanEnabled(false, false);
        renderer.setZoomButtonsVisible(false);
        renderer.setShowLegend(false);
        // renderer.setScale(1);

        currentdataset = buildDataset(titles, x, values);

        String[] types = new String[]{CubicLineChart.TYPE,
                CubicLineChart.TYPE, CubicLineChart.TYPE};// };

        Intent intent = ChartFactory.getCombinedXYChartIntent(TedActivity.this,
                currentdataset, renderer, types, "Weather parameters");
        //
        final LinearLayout view = (LinearLayout) findViewById(R.id.chart);

        LinearLayout topContainer = (LinearLayout) findViewById(R.id.top_container);

        int minHeight = topContainer.getMinimumHeight();

        if(minHeight == 0) {
            View textBelow = findViewById(R.id.scroll_below_text);

            AutoExpandKeyboardUtils.expand(this, topContainer, findViewById(R.id.bottom_fragment),
                     getToolbar(), textBelow);
            view.getLayoutParams().height = topContainer.getMinimumHeight() - 10;
        }

        AbstractChart mChart = (AbstractChart) intent.getExtras().get("chart");
        mChartView = new GraphicalView(this, mChart);

        // mChartView = ChartFactory.getCubeLineChartView(TedActivity.this,
        // currentdataset, renderer, 0.5f);

        // mChartView.addZoomListener(mZoomListener, true, false);
        view.removeAllViews();
        view.addView(mChartView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        // // to reflect margin
        renderer.setScale(1);
        mChartView.repaint();
        //

        // mHandler.postDelayed(new Runnable() {
        //
        // @Override
        // public void run() {
        // // TODO Auto-generated method stub
        // for(int i=0;i<15000;i++){
        // currentSeries.remove(i);
        // currentSeries.add(i, 4200);
        // mChartView.repaint();
        // //readingCount = readingCount + 100;
        // }
        // }
        // }, 1000);

        // Timer timer = new Timer();
        // timer.scheduleAtFixedRate(new TimerTask() {
        //
        // @Override
        // public void run() {
        // // TODO Auto-generated method stub
        // runOnUiThread(new Runnable() {
        // public void run() {
        //
        // //currentSeries.remove(readingCount);
        //
        // //Toast.makeText(TedActivity.this, "TImer",
        // Toast.LENGTH_SHORT).show();
        // }
        // });
        // }
        // }, 2000, 2000);

        currentSeries = currentdataset.getSeriesAt(0);

        // long future = 9 * 60 * 1000;
        // long delay_timer = 2 * 1000;
        // CountDownTimer ctimer = new CountDownTimer(future,delay_timer) {
        // int c = 0;
        // @Override
        // public void onTick(long millisUntilFinished) {
        // // TODO Auto-generated method stub
        // //XYSeries currentSeries = null;
        //
        // Random rand = new Random();
        // int co2 = rand.nextInt(8000);
        //
        // int yMax = (int) renderer.getYAxisMax();
        // if(co2 > yMax){
        // int vmax = (int) (co2 + ((co2 * 15)/100f));
        // renderer.setYAxisMax(vmax);
        // }
        //
        // // int yMin = (int) renderer.getYAxisMin();
        // // if(yMin == 0){
        // // int vmin = co2 - (co2 * (15/100));
        // // renderer.setYAxisMin(vmin);
        // // }else if(co2<yMin){
        // // int vmin = co2 - (co2 * (15/100));
        // // renderer.setYAxisMin(vmin);
        // // }
        //
        // int delay_v = 2;
        // int duration_v = 3;
        // int limit = (duration_v * 60)/delay_v;
        // if(readingCount != 0){
        // if((readingCount%limit) == 0){
        // //addNewSeries();
        // if(c<=1){
        // // Toast.makeText(TedActivity.this,
        // // "Series Changed",
        // // Toast.LENGTH_LONG).show();
        // currentSeries = currentdataset.getSeriesAt(c+1);
        // // if(c == 0){
        // // renderer.getSeriesRendererAt(0).setColor(Color.rgb(0, 171, 234));
        // // }else if(c == 1){
        // // renderer.getSeriesRendererAt(0).setColor(Color.RED);
        // // }
        //
        // SimpleDateFormat formatter_date = new SimpleDateFormat(
        // "yyyy-MM-dd", Locale.ENGLISH);
        // SimpleDateFormat formatter_time = new SimpleDateFormat(
        // "HH:mm:ss", Locale.ENGLISH);
        // Date currentTime = new Date();
        //
        // String current_date = formatter_date.format(currentTime);
        // String current_time = formatter_time.format(currentTime);
        // //filename = current_date+"_"+current_time+"_set_"+c+".csv";
        //
        // //addNewSeries();
        // c++;
        // }
        // }
        // }else if(readingCount == 0){
        // SimpleDateFormat formatter_date = new SimpleDateFormat(
        // "yyyy-MM-dd", Locale.ENGLISH);
        // SimpleDateFormat formatter_time = new SimpleDateFormat(
        // "HH:mm:ss", Locale.ENGLISH);
        // Date currentTime = new Date();
        //
        // String current_date = formatter_date.format(currentTime);
        // String current_time = formatter_time.format(currentTime);
        // //filename = current_date+"_"+current_time+"_set_"+c+".csv";
        // }
        //
        // // SimpleDateFormat formatter = new SimpleDateFormat(
        // // "yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        // // Date currentTime = new Date();
        // // try {
        // // File dir = new File(
        // // Environment
        // // .getExternalStorageDirectory(),
        // // "AEToCLogs");
        // // dir.mkdirs();
        // // System.out.println("file != null");
        // //
        // // File file;
        // // if(filename.equals("")){
        // // file = new File(dir, "sensordata.csv");
        // // }else{
        // // file = new File(dir, filename);
        // // }
        // //
        // // FileOutputStream fos = new FileOutputStream(
        // // file, true);
        // // new PrintStream(fos).print(formatter
        // // .format(currentTime)
        // // + ","
        // // + randomNum + "\n");
        // // fos.close();
        // // } catch (Exception e) {
        // // // TODO: handle exception
        // // e.printStackTrace();
        // // }
        //
        // //XYSeries currentSeries = currentdataset.getSeriesAt(0);
        //
        // currentSeries.add(readingCount, co2);
        // mChartView.repaint();
        // mChartView.invalidate();
        // readingCount = readingCount + 1;
        // }
        //
        // @Override
        // public void onFinish() {
        // // TODO Auto-generated method stub
        //
        // Toast.makeText(TedActivity.this,
        // "Timer Finish",
        // Toast.LENGTH_LONG).show();
        // }
        // };
        // currentSeries = currentdataset.getSeriesAt(0);
        // ctimer.start();
    }

    private void getTimeChart() {
        final long HOUR = 3600 * 1000;

        final long DAY = HOUR * 24;

        final int HOURS = 24;

        String[] titles = new String[]{"Reponse %"};
        long now = Math.round(new Date().getTime() / DAY) * DAY;
        List<Date[]> x = new ArrayList<Date[]>();
        for (int i = 0; i < titles.length; i++) {
            Date[] dates = new Date[HOURS];
            for (int j = 0; j < HOURS; j++) {
                dates[j] = new Date(now - (HOURS - j) * HOUR);
            }
            x.add(dates);
        }
        List<double[]> values = new ArrayList<double[]>();

        values.add(new double[]{21.2, 21.5, 21.7, 21.5, 21.4, 21.4, 21.3,
                21.1, 20.6, 20.3, 20.2, 19.9, 19.7, 19.6, 19.9, 20.3, 20.6,
                20.9, 21.2, 21.6, 21.9, 22.1, 21.7, 21.5});
        // values.add(new double[] { 1.9, 1.2, 0.9, 0.5, 0.1, -0.5, -0.6,
        // MathHelper.NULL_VALUE,
        // MathHelper.NULL_VALUE, -1.8, -0.3, 1.4, 3.4, 4.9, 7.0, 6.4, 3.4, 2.0,
        // 1.5, 0.9, -0.5,
        // MathHelper.NULL_VALUE, -1.9, -2.5, -4.3 });

        int[] colors = new int[]{Color.BLACK};
        PointStyle[] styles = new PointStyle[]{PointStyle.CIRCLE};
        XYMultipleSeriesRenderer renderer = buildRenderer(colors, styles);
        int length = renderer.getSeriesRendererCount();
        renderer.setPointSize(0f);
        for (int i = 0; i < length; i++) {
            XYSeriesRenderer r = (XYSeriesRenderer) renderer
                    .getSeriesRendererAt(i);
            r.setLineWidth(1.0f);
            r.setFillPoints(false);
            r.setDisplayChartValues(false);
        }

        // setChartSettings(renderer, "", "Time", "Reponse %", 0,
        // 10, 0, 40, Color.LTGRAY, Color.WHITE);

        setChartSettings(renderer, "", "Time", "Response %",
                x.get(0)[0].getTime(), x.get(0)[HOURS - 1].getTime(), -5, 30,
                Color.LTGRAY, Color.WHITE);

        renderer.setXLabels(4);
        renderer.setYLabels(10);
        renderer.setShowGrid(true);
        renderer.setGridColor(Color.rgb(136, 136, 136));
        renderer.setBackgroundColor(Color.WHITE);
        renderer.setApplyBackgroundColor(true);
        renderer.setMargins(new int[]{0, 35, 0, 0});
        renderer.setXLabelsAlign(Align.CENTER);
        renderer.setYLabelsAlign(Align.RIGHT);
        renderer.setYLabelsColor(0, Color.rgb(0, 171, 234));
        renderer.setYLabelsVerticalPadding(-15);
        renderer.setXLabelsPadding(-5f);
        renderer.setZoomButtonsVisible(false);
        // renderer.setPanLimits(new double[] { -10, 20, -10, 40 });
        // renderer.setZoomLimits(new double[] { -10, 20, -10, 40 });
        renderer.setZoomEnabled(false, false);
        renderer.setPanEnabled(false, false);
        renderer.setZoomButtonsVisible(false);
        renderer.setScale(1);

        Intent intent = ChartFactory.getTimeChartIntent(TedActivity.this,
                buildDateDataset(titles, x, values), renderer, "h:mm a");

        final LinearLayout view = (LinearLayout) findViewById(R.id.chart);
        AbstractChart mChart = (AbstractChart) intent.getExtras().get("chart");
        GraphicalView mChartView = new GraphicalView(this, mChart);
        // mChartView.addZoomListener(mZoomListener, true, false);
        view.addView(mChartView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        // to reflect margin
        // renderer.setScale(1);
        // mChartView.repaint();
    }

    /**
     * Builds an XY multiple time dataset using the provided values.
     *
     * @param titles  the series titles
     * @param xValues the values for the X axis
     * @param yValues the values for the Y axis
     * @return the XY multiple time dataset
     */
    protected XYMultipleSeriesDataset buildDateDataset(String[] titles,
                                                       List<Date[]> xValues, List<double[]>
                                                               yValues) {
        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
        int length = titles.length;
        for (int i = 0; i < length; i++) {
            TimeSeries series = new TimeSeries(titles[i]);
            Date[] xV = xValues.get(i);
            double[] yV = yValues.get(i);
            int seriesLength = xV.length;
            for (int k = 0; k < seriesLength; k++) {
                series.add(xV[k], yV[k]);
            }
            dataset.addSeries(series);
        }
        return dataset;
    }

    private void sendCommand(String command) {
        if ((command != null) && (!command.equals(""))
                && (!command.equals("\n"))) {
            command = command.replace("\r", "");
            command = command.replace("\n", "");
            command = command.trim();

            if (UsbService.serialPort != null) {
                if (command.contains("(") && command.contains(")")) {
                    // HEX
                    command = command.replace("(", "");
                    command = command.replace(")", "");
                    command = command.trim();
                    String[] arr = command.split("-");

                    byte[] bytes = new byte[arr.length];
                    for (int j = 0; j < bytes.length; j++) {
                        bytes[j] = (byte) Integer.parseInt(arr[j], 16);
                    }
                    UsbService.write(bytes);
                } else {
                    // ASCII
                    UsbService.write(command.getBytes());

                    String cr = "\r";
                    if (UsbService.serialPort != null) {
                        UsbService.write(cr.getBytes());
                    }
                }

                appendText(txtOutput, "Tx: " + command);
                mScrollView.smoothScrollTo(0, 0);
            } else {
                Toast.makeText(TedActivity.this,
                        "serial port not found", Toast.LENGTH_LONG).show();
            }

        }
    }

    private void sendMessage() {
        // TODO Auto-generated method stub

        // TODO Auto-generated method stub
        if (!mEditor.getText().toString().equals("")) {
            String multiLines = mEditor.getText().toString();
            String[] commands;
            String delimiter = "\n";

            commands = multiLines.split(delimiter);

            for (int i = 0; i < commands.length; i++) {
                String command = commands[i];
                Log.d("command", command);
                if ((command != null) && (!command.equals(""))
                        && (!command.equals("\n"))) {
                    command = command.replace("\r", "");
                    command = command.replace("\n", "");
                    command = command.trim();

                    if (UsbService.serialPort != null) {
                        if (command.contains("(") && command.contains(")")) {
                            // HEX
                            command = command.replace("(", "");
                            command = command.replace(")", "");
                            command = command.trim();
                            String[] arr = command.split("-");

                            byte[] bytes = new byte[arr.length];
                            for (int j = 0; j < bytes.length; j++) {
                                bytes[j] = (byte) Integer.parseInt(arr[j], 16);
                            }
                            UsbService.write(bytes);
                        } else {
                            // ASCII
                            UsbService.write(command.getBytes());

                            String cr = "\r";
                            if (UsbService.serialPort != null) {
                                UsbService.write(cr.getBytes());
                            }
                        }

                        appendText(txtOutput, "Tx: " + command);
                        mScrollView.smoothScrollTo(0, 0);
                    } else {
                        Toast.makeText(TedActivity.this,
                                "serial port not found", Toast.LENGTH_LONG).show();
                    }

                    // txtOutput.append("Tx: " + command + "\n");
                    // mScrollView.smoothScrollTo(0, txtOutput.getBottom());

                    // String cr = "\r";
                    // if (UsbService.serialPort != null){ // if UsbService
                    // // was correctly
                    // // binded, Send
                    // // data
                    // UsbService.write(cr.getBytes());
                    // }

                    // txtOutput.append("Tx: " + cr + "\n");
                    // mScrollView.smoothScrollTo(0, txtOutput.getBottom());
                }
            }

            // String data = mEditor.getText().toString();
            // //data = data.replace("CR", "\u0000");
            // if(usbService != null) // if UsbService was correctly
            // binded, Send data
            // usbService.write(data.getBytes());
            //
            // String data1 = "\r";
            // if(usbService != null) // if UsbService was correctly
            // binded, Send data
            // usbService.write(data1.getBytes());
        } else {
            String data = "\r";
            // data = data.replace("CR", "\u0000");
            if (UsbService.serialPort != null) { // if UsbService was
                // correctly binded,
                // Send data
                UsbService.write(data.getBytes());
            } else {
                Toast.makeText(TedActivity.this, "serial port not found", Toast.LENGTH_LONG)
                        .show();
            }

            // txtOutput.append("Tx: " + data + "\n");
            // mScrollView.smoothScrollTo(0, txtOutput.getBottom());
        }

    }

    /**
     * Builds an XY multiple series renderer.
     *
     * @param colors the series rendering colors
     * @param styles the series point styles
     * @return the XY multiple series renderers
     */
    protected XYMultipleSeriesRenderer buildRenderer(int[] colors,
                                                     PointStyle[] styles) {
        XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
        setRenderer(renderer, colors, styles);
        return renderer;
    }

    protected void setRenderer(XYMultipleSeriesRenderer renderer, int[] colors,
                               PointStyle[] styles) {
        renderer.setAxisTitleTextSize(16);
        renderer.setChartTitleTextSize(20);
        renderer.setLabelsTextSize(15);
        renderer.setLegendTextSize(15);
        renderer.setPointSize(5f);
        renderer.setMargins(new int[]{20, 30, 15, 20});
        int length = colors.length;
        for (int i = 0; i < length; i++) {
            XYSeriesRenderer r = new XYSeriesRenderer();
            r.setColor(colors[i]);
            r.setPointStyle(styles[i]);
            renderer.addSeriesRenderer(r);
        }
    }

    /**
     * Sets a few of the series renderer settings.
     *
     * @param renderer    the renderer to set the properties to
     * @param title       the chart title
     * @param xTitle      the title for the X axis
     * @param yTitle      the title for the Y axis
     * @param xMin        the minimum value on the X axis
     * @param xMax        the maximum value on the X axis
     * @param yMin        the minimum value on the Y axis
     * @param yMax        the maximum value on the Y axis
     * @param axesColor   the axes color
     * @param labelsColor the labels color
     */
    protected void setChartSettings(XYMultipleSeriesRenderer renderer,
                                    String title, String xTitle, String yTitle, double xMin,
                                    double xMax, double yMin, double yMax, int axesColor,
                                    int labelsColor) {
        renderer.setChartTitle(title);
        renderer.setXTitle(xTitle);
        renderer.setYTitle(yTitle);
        renderer.setXAxisMin(xMin);
        renderer.setXAxisMax(xMax);
        renderer.setYAxisMin(yMin);
        renderer.setYAxisMax(yMax);
        renderer.setAxesColor(axesColor);
        renderer.setLabelsColor(labelsColor);
    }

    /**
     * Builds an XY multiple dataset using the provided values.
     *
     * @param titles  the series titles
     * @param xValues the values for the X axis
     * @param yValues the values for the Y axis
     * @return the XY multiple dataset
     */
    protected XYMultipleSeriesDataset buildDataset(String[] titles,
                                                   List<double[]> xValues, List<double[]> yValues) {
        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
        addXYSeries(dataset, titles, xValues, yValues, 0);
        return dataset;
    }

    public void addXYSeries(XYMultipleSeriesDataset dataset, String[] titles,
                            List<double[]> xValues, List<double[]> yValues, int scale) {
        int length = titles.length;
        for (int i = 0; i < length; i++) {
            XYSeries series = new XYSeries(titles[i], scale);
            // double[] xV = xValues.get(i);
            // double[] yV = yValues.get(i);
            // int seriesLength = xV.length;
            // for (int k = 0; k < seriesLength; k++) {
            // series.add(xV[k], yV[k]);
            // }
            // currentSeries = series;
            dataset.addSeries(series);
        }
    }

    // chart
    // @Override
    // public void onClick(final View v) {
    // switch (v.getId()) {
    // case R.id.zoom_in:
    // mChartView.zoomIn();
    // break;
    //
    // case R.id.zoom_out:
    // mChartView.zoomOut();
    // break;
    //
    // case R.id.zoom_reset:
    // mChartView.zoomReset();
    // break;
    //
    // default:
    // break;
    // }
    //
    // }

    //
    // private double randomValue() {
    // final int value = Math.abs(RAND.nextInt(32));
    // final double percent = (value * 100) / 31.0;
    // return ((int) (percent * 10)) / 10.0;
    // }
    //
    // private void addValue() {
    // final double value = randomValue();
    // if (mYAxisMin > value) mYAxisMin = value;
    // if (mYAxisMax < value) mYAxisMax = value;
    //
    // final Date now = new Date();
    // final long time = now.getTime();
    //
    // if (time - mLastItemChange > 10000) {
    // mLastItemChange = time;
    // mItemIndex = Math.abs(RAND.nextInt(ITEMS.length));
    // }
    //
    // final String item = ITEMS[mItemIndex];
    // final int color = COLORS[mItemIndex];
    // final int lastItemIndex = mItems.lastIndexOf(item);
    // mItems.add(item);
    //
    // if (lastItemIndex > -1) {
    // boolean otherItemBetween = false;
    // for (int i = lastItemIndex + 1; i < mItems.size(); i++) {
    // if (!item.equals(mItems.get(i))) {
    // otherItemBetween = true;
    // break;
    // }
    // }
    // if (otherItemBetween) {
    // addSeries(null, now, value, item, color);
    // }
    // else {
    // mSeries.get(item).add(now, value);
    // }
    // }
    // else {
    // addSeries(item, now, value, item, color);
    // }
    //
    // scrollGraph(time);
    // //mChartView.repaint();
    // }
    //
    // private void addSeries(final String title, final Date time, final double
    // value, final String item, final int color) {
    // for (int i = 0; i < THRESHOLD_COLORS.length; i++) {
    // mThresholds[i].add(new Date(time.getTime() + 1000 * 60 * 5),
    // THRESHOLD_VALUES[i]);
    // }
    //
    // final TimeSeries series = new TimeSeries(title);
    // series.add(time, value);
    // mSeries.put(item, series);
    // mDataset.addSeries(series);
    // mRenderer.addSeriesRenderer(getSeriesRenderer(color));
    // }
    //
    // private void scrollGraph(final long time) {
    // final double[] limits = new double[] { time - TEN_SEC * mZoomLevel, time
    // + TWO_SEC * mZoomLevel, mYAxisMin - mYAxisPadding,
    // mYAxisMax + mYAxisPadding };
    // mRenderer.setRange(limits);
    // }
    //
    // private XYSeriesRenderer getSeriesRenderer(final int color) {
    // final XYSeriesRenderer r = new XYSeriesRenderer();
    // r.setDisplayChartValues(true);
    // r.setChartValuesTextSize(20);
    // r.setPointStyle(PointStyle.CIRCLE);
    // r.setColor(color);
    // r.setFillPoints(false);
    // r.setLineWidth(1.5f);
    // return r;
    // }
    //
    // private static int randomColor() {
    // final float hue = (RAND.nextInt(360) + RATIO);
    // return Color.HSVToColor(new float[] { hue, 0.8f, 0.9f });
    // }
    //

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
//		if (ctimer != null) {
//			ctimer.cancel();
//		}

        if (executor != null) {
            executor.shutdown();
            while (!executor.isTerminated()) {
            }
        }

        if (UsbService.mHandlerStop != null) {
            UsbService.mHandlerStop.sendEmptyMessage(0);
        }
    }

    /**
     * @see android.app.Activity#onStart()
     */
    protected void onStart() {
        super.onStart();

        // TedChangelog changeLog;
        // SharedPreferences prefs;

        // changeLog = new TedChangelog();
        // prefs = getSharedPreferences(Constants.PREFERENCES_NAME,
        // Context.MODE_PRIVATE);
        //
        // if (changeLog.isFirstLaunchAfterUpdate(this, prefs)) {
        // Builder builder = new Builder(this);
        // String message = getString(changeLog.getTitleResource(this))
        // + "\n\n" + getString(changeLog.getChangeLogResource(this));
        // builder.setTitle(R.string.ui_whats_new);
        // builder.setMessage(message);
        // builder.setCancelable(true);
        // builder.setPositiveButton(android.R.string.ok,
        // new DialogInterface.OnClickListener() {
        // public void onClick(DialogInterface dialog, int which) {
        // dialog.dismiss();
        // }
        // });
        //
        // builder.create().show();
        // }
        //
        // changeLog.saveCurrentVersion(this, prefs);
    }

    /**
     * @see android.app.Activity#onRestart()
     */
    protected void onRestart() {
        super.onRestart();
        mReadIntent = false;
    }

    /**
     * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
     */
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d("TED", "onRestoreInstanceState");
        Log.v("TED", mEditor.getText().toString());
    }

    /**
     * @see android.app.Activity#onResume()
     */
    protected void onResume() {
        super.onResume();
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onResume");

        if (mReadIntent) {
            readIntent();
        }

        mReadIntent = false;

        updateTitle();
        mEditor.updateFromSettings();
    }

    /**
     * @see android.app.Activity#onPause()
     */
    protected void onPause() {
        super.onPause();
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onPause");

        if (Settings.FORCE_AUTO_SAVE && mDirty && (!mReadOnly)) {
            if ((mCurrentFilePath == null) || (mCurrentFilePath.length() == 0))
                doAutoSaveFile();
            else if (Settings.AUTO_SAVE_OVERWRITE)
                doSaveFile(mCurrentFilePath);
        }
    }

    /**
     * @see android.app.Activity#onActivityResult(int, int,
     * android.content.Intent)
     */
    @TargetApi(11)
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Bundle extras;
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onActivityResult");
        mReadIntent = false;

        if (resultCode == RESULT_CANCELED) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Result canceled");
            return;
        }

        if ((resultCode != RESULT_OK) || (data == null)) {
            if (BuildConfig.DEBUG)
                Log.e(TAG, "Result error or null data! / " + resultCode);
            return;
        }

        extras = data.getExtras();
        if (extras == null) {
            if (BuildConfig.DEBUG)
                Log.e(TAG, "No extra data ! ");
            return;
        }

        switch (requestCode) {
            case REQUEST_SAVE_AS:
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Save as : " + extras.getString("path"));
                doSaveFile(extras.getString("path"));
                break;
            case REQUEST_OPEN:
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Open : " + extras.getString("path"));
                if (extras.getString("path").endsWith(".txt")) {
                    doOpenFile(new File(extras.getString("path")), false);
                } else if (extras.getString("path").endsWith(".csv")) {
                    openchart1(extras.getString("path"));
                } else {
                    Toast.makeText(TedActivity.this, "Invalid File",
                            Toast.LENGTH_SHORT).show();
                }

                break;
        }
    }

    @Override
    public int getFolderDrawable() {
        return R.drawable.folder;
    }

    @Override
    public int getFileDrawable() {
        return R.drawable.file;
    }

    /**
     * @see android.app.Activity#onConfigurationChanged(android.content.res.Configuration)
     */
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onConfigurationChanged");
    }

    /**
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        return true;
    }

    /**
     * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
     */
    @TargetApi(11)
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);


        Log.d("onPrepareOptionsMenu", "onPrepareOptionsMenu");
        menu.clear();
        menu.close();

        // boolean isUsbConnected = checkUsbConnection();
        if (isUsbConnected) {
            wrapMenuItem(addMenuItem(menu, MENU_ID_CONNECT_DISCONNECT,
                    R.string.menu_disconnect, R.drawable.usb_connected), true);
        } else {
            wrapMenuItem(addMenuItem(menu, MENU_ID_CONNECT_DISCONNECT,
                    R.string.menu_connect, R.drawable.usb_disconnected), true);

        }

        wrapMenuItem(addMenuItem(menu, MENU_ID_NEW, R.string.menu_new,
                R.drawable.ic_menu_file_new), false);
        wrapMenuItem(addMenuItem(menu, MENU_ID_OPEN, R.string.menu_open,
                R.drawable.ic_menu_file_open), false);

        wrapMenuItem(addMenuItem(menu, MENU_ID_OPEN_CHART, R.string.menu_open_chart,
                R.drawable.ic_menu_file_open), false);

        if (!mReadOnly)
            wrapMenuItem(addMenuItem(menu, MENU_ID_SAVE, R.string.menu_save,
                    R.drawable.ic_menu_save), false);

        // if ((!mReadOnly) && Settings.UNDO)
        // addMenuItem(menu, MENU_ID_UNDO, R.string.menu_undo,
        // R.drawable.ic_menu_undo);

        // addMenuItem(menu, MENU_ID_SEARCH, R.string.menu_search,
        // R.drawable.ic_menu_search);

        if (RecentFiles.getRecentFiles().size() > 0)
            wrapMenuItem(addMenuItem(menu, MENU_ID_OPEN_RECENT, R.string.menu_open_recent,
                    R.drawable.ic_menu_recent), false);

        wrapMenuItem(addMenuItem(menu, MENU_ID_SAVE_AS, R.string.menu_save_as, 0), false);

        wrapMenuItem(addMenuItem(menu, MENU_ID_SETTINGS, R.string.menu_settings, 0), false);

        if (Settings.BACK_BTN_AS_UNDO && Settings.UNDO)
            wrapMenuItem(addMenuItem(menu, MENU_ID_QUIT, R.string.menu_quit, 0), false);

        // if ((!mReadOnly) && Settings.UNDO) {
        // showMenuItemAsAction(menu.findItem(MENU_ID_UNDO),
        // R.drawable.ic_menu_undo, MenuItem.SHOW_AS_ACTION_IF_ROOM);
        // }

        // showMenuItemAsAction(menu.findItem(MENU_ID_SEARCH),
        // R.drawable.ic_menu_search);

        if (isUsbConnected) {
            showMenuItemAsAction(menu.findItem(MENU_ID_CONNECT_DISCONNECT),
                    R.drawable.usb_connected, MenuItem.SHOW_AS_ACTION_IF_ROOM
                            | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        } else {
            showMenuItemAsAction(menu.findItem(MENU_ID_CONNECT_DISCONNECT),
                    R.drawable.usb_disconnected,
                    MenuItem.SHOW_AS_ACTION_IF_ROOM
                            | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }

        return true;
    }

    private void wrapMenuItem(MenuItem menuItem, boolean isShow) {
        if (isShow) {
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }
    }

    /**
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        mWarnedShouldQuit = false;
        switch (item.getItemId()) {
            case MENU_ID_CONNECT_DISCONNECT:
                Log.d("isUsbConnected", "" + isUsbConnected);
                if (isUsbConnected) {
                    // unregisterReceiver(mUsbReceiver);
                    // unbindService(usbConnection);
                    // if(UsbService.mHandlerStop != null){
                    // UsbService.mHandlerStop.sendEmptyMessage(0);
                    // }
                } else {
                    // setFilters(); // Start listening notifications from
                    // UsbService
                    // startService(UsbService.class, usbConnection, null); // Start
                    // UsbService(if it was not started before) and Bind it
                    // startService(new Intent(TedActivity.this, UsbService.class));
                }
                break;
            case MENU_ID_NEW:
                newContent();
                return true;
            case MENU_ID_SAVE:
                saveContent();
                break;
            case MENU_ID_SAVE_AS:
                saveContentAs();
                break;
            case MENU_ID_OPEN:
                openFile();
                break;
            case MENU_ID_OPEN_CHART:
                openFile();
                //openChart();
                break;
            case MENU_ID_OPEN_RECENT:
                openRecentFile();
                break;
            // case MENU_ID_SEARCH:
            // search();
            // break;
            case MENU_ID_SETTINGS:
                settingsActivity();
                return true;
            // case MENU_ID_ABOUT:
            // aboutActivity();
            // return true;
            case MENU_ID_QUIT:
                quit();
                return true;
            case MENU_ID_UNDO:
                if (!undo()) {
                    Crouton.showText(this, R.string.toast_warn_no_undo, Style.INFO);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * @see android.text.TextWatcher#beforeTextChanged(java.lang.CharSequence,
     * int, int, int)
     */
    public void beforeTextChanged(CharSequence s, int start, int count,
                                  int after) {
        if (Settings.UNDO && (!mInUndo) && (mWatcher != null))
            mWatcher.beforeChange(s, start, count, after);
    }

    /**
     * @see android.text.TextWatcher#onTextChanged(java.lang.CharSequence, int,
     * int, int)
     */
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mInUndo)
            return;

        if (Settings.UNDO && (!mInUndo) && (mWatcher != null))
            mWatcher.afterChange(s, start, before, count);

    }

    /**
     * @see android.text.TextWatcher#afterTextChanged(android.text.Editable)
     */
    public void afterTextChanged(Editable s) {
        if (!mDirty) {
            mDirty = true;
            updateTitle();
        }
    }

    /**
     * @see android.app.Activity#onKeyUp(int, android.view.KeyEvent)
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // if (mSearchLayout.getVisibility() != View.GONE)
                // search();
                // else
                if (Settings.UNDO && Settings.BACK_BTN_AS_UNDO) {
                    if (!undo())
                        warnOrQuit();
                } else if (shouldQuit()) {
                    quit();
                } else {
                    mWarnedShouldQuit = false;
                    return super.onKeyUp(keyCode, event);
                }
                return true;
            // case KeyEvent.KEYCODE_SEARCH:
            // search();
            // mWarnedShouldQuit = false;
            // return true;
        }
        mWarnedShouldQuit = false;
        return super.onKeyUp(keyCode, event);
    }

    private boolean shouldQuit() {
        int entriesCount = getSupportFragmentManager().getBackStackEntryCount();
        return entriesCount == 0;
    }

    /**
     * @see OnClickListener#onClick(View)
     */
    // public void onClick(View v) {
    // mWarnedShouldQuit = false;
    // // switch (v.getId()) {
    // // case R.id.buttonSearchClose:
    // // search();
    // // break;
    // // case R.id.buttonSearchNext:
    // // searchNext();
    // // break;
    // // case R.id.buttonSearchPrev:
    // // searchPrevious();
    // // break;
    // // }
    // }

    /**
     * Read the intent used to start this activity (open the text file) as well
     * as the non configuration instance if activity is started after a screen
     * rotate
     */
    protected void readIntent() {
        Intent intent;
        String action;
        File file;

        intent = getIntent();
        if (intent == null) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "No intent found, use default instead");
            doDefaultAction();
            return;
        }

        action = intent.getAction();
        if (action == null) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Intent w/o action, default action");
            doDefaultAction();
        } else if ((action.equals(Intent.ACTION_VIEW))
                || (action.equals(Intent.ACTION_EDIT))) {
            try {
                file = new File(new URI(intent.getData().toString()));
                doOpenFile(file, false);
            } catch (URISyntaxException e) {
                Crouton.showText(this, R.string.toast_intent_invalid_uri,
                        Style.ALERT);
            } catch (IllegalArgumentException e) {
                Crouton.showText(this, R.string.toast_intent_illegal,
                        Style.ALERT);
            }
        } else if (action.equals(ACTION_WIDGET_OPEN)) {
            try {
                file = new File(new URI(intent.getData().toString()));
                doOpenFile(file,
                        intent.getBooleanExtra(EXTRA_FORCE_READ_ONLY, false));
            } catch (URISyntaxException e) {
                Crouton.showText(this, R.string.toast_intent_invalid_uri,
                        Style.ALERT);
            } catch (IllegalArgumentException e) {
                Crouton.showText(this, R.string.toast_intent_illegal,
                        Style.ALERT);
            }
        } else {
            doDefaultAction();
        }
    }

    /**
     * Run the default startup action
     */
    protected void doDefaultAction() {
        File file;
        boolean loaded;
        loaded = false;

        if (doOpenBackup())
            loaded = true;

        if ((!loaded) && Settings.USE_HOME_PAGE) {
            file = new File(Settings.HOME_PAGE_PATH);
            if ((file == null) || (!file.exists())) {
                Crouton.showText(this, R.string.toast_open_home_page_error,
                        Style.ALERT);
            } else if (!file.canRead()) {
                Crouton.showText(this, R.string.toast_home_page_cant_read,
                        Style.ALERT);
            } else {
                loaded = doOpenFile(file, false);
            }
        }

        if (!loaded)
            doClearContents();
    }

    /**
     * Clears the content of the editor. Assumes that user was prompted and
     * previous data was saved
     */
    protected void doClearContents() {
        mWatcher = null;
        mInUndo = true;
        mEditor.setText("");
        mCurrentFilePath = null;
        mCurrentFileName = null;
        Settings.END_OF_LINE = Settings.DEFAULT_END_OF_LINE;
        mDirty = false;
        mReadOnly = false;
        mWarnedShouldQuit = false;
        mWatcher = new TextChangeWatcher();
        mInUndo = false;
        mDoNotBackup = false;

        TextFileUtils.clearInternal(getApplicationContext());

        updateTitle();
    }

    /**
     * Opens the given file and replace the editors content with the file.
     * Assumes that user was prompted and previous data was saved
     *
     * @param file          the file to load
     * @param forceReadOnly force the file to be used as read only
     * @return if the file was loaded successfully
     */
    protected boolean doOpenFile(File file, boolean forceReadOnly) {
        String text;

        if (file == null)
            return false;

        if (BuildConfig.DEBUG)
            Log.i(TAG, "Openning file " + file.getName());

        try {
            text = TextFileUtils.readTextFile(file);
            if (text != null) {
                mInUndo = true;
                if (mEditor.getText().toString().equals("")) {
                    mEditor.append(text);// change by nkl ori settext
                } else {
                    mEditor.append("\n" + text);// change by nkl ori settext
                }
                mWatcher = new TextChangeWatcher();
                mCurrentFilePath = getCanonizePath(file);
                mCurrentFileName = file.getName();
                RecentFiles.updateRecentList(mCurrentFilePath);
                RecentFiles.saveRecentList(getSharedPreferences(
                        PREFERENCES_NAME, MODE_PRIVATE));
                mDirty = false;
                mInUndo = false;
                mDoNotBackup = false;
                if (file.canWrite() && (!forceReadOnly)) {
                    mReadOnly = false;
                    mEditor.setEnabled(true);
                } else {
                    mReadOnly = true;
                    mEditor.setEnabled(false);
                }

                updateTitle();

                return true;
            } else {
                Crouton.showText(this, R.string.toast_open_error, Style.ALERT);
            }
        } catch (OutOfMemoryError e) {
            Crouton.showText(this, R.string.toast_memory_open, Style.ALERT);
        }

        return false;
    }

    /**
     * Open the last backup file
     *
     * @return if a backup file was loaded
     */
    protected boolean doOpenBackup() {

        String text;

        try {
            text = TextFileUtils.readInternal(this);
            if (!TextUtils.isEmpty(text)) {
                mInUndo = true;
                mEditor.setText(text);
                mWatcher = new TextChangeWatcher();
                mCurrentFilePath = null;
                mCurrentFileName = null;
                mDirty = false;
                mInUndo = false;
                mDoNotBackup = false;
                mReadOnly = false;
                mEditor.setEnabled(true);

                updateTitle();

                return true;
            } else {
                return false;
            }
        } catch (OutOfMemoryError e) {
            Crouton.showText(this, R.string.toast_memory_open, Style.ALERT);
        }

        return true;
    }

    /**
     * Saves the text editor's content into a file at the given path. If an
     * after save {@link Runnable} exists, run it
     *
     * @param path the path to the file (must be a valid path and not null)
     */
    protected void doSaveFile(String path) {
        String content;

        if (path == null) {
            Crouton.showText(this, R.string.toast_save_null, Style.ALERT);
            return;
        }

        content = mEditor.getText().toString();

        if (!TextFileUtils.writeTextFile(path + ".tmp", content)) {
            Crouton.showText(this, R.string.toast_save_temp, Style.ALERT);
            return;
        }

        if (!deleteItem(path)) {
            Crouton.showText(this, R.string.toast_save_delete, Style.ALERT);
            return;
        }

        if (!renameItem(path + ".tmp", path)) {
            Crouton.showText(this, R.string.toast_save_rename, Style.ALERT);
            return;
        }

        mCurrentFilePath = getCanonizePath(new File(path));
        mCurrentFileName = (new File(path)).getName();
        RecentFiles.updateRecentList(path);
        RecentFiles.saveRecentList(getSharedPreferences(PREFERENCES_NAME,
                MODE_PRIVATE));
        mReadOnly = false;
        mDirty = false;
        updateTitle();
        Crouton.showText(this, R.string.toast_save_success, Style.CONFIRM);

        runAfterSave();
    }

    protected void doAutoSaveFile() {
        if (mDoNotBackup) {
            doClearContents();
        }

        String text = mEditor.getText().toString();
        if (text.length() == 0)
            return;

        if (TextFileUtils.writeInternal(this, text)) {
            showToast(this, R.string.toast_file_saved_auto, false);
        }
    }

    /**
     * Undo the last change
     *
     * @return if an undo was don
     */
    protected boolean undo() {
        boolean didUndo = false;
        mInUndo = true;
        int caret;
        caret = mWatcher.undo(mEditor.getText());
        if (caret >= 0) {
            mEditor.setSelection(caret, caret);
            didUndo = true;
        }
        mInUndo = false;

        return didUndo;
    }

    /**
     * Prompt the user to save the current file before doing something else
     */
    protected void promptSaveDirty() {
        Builder builder;

        if (!mDirty) {
            runAfterSave();
            return;
        }

        builder = new Builder(this);
        builder.setTitle(R.string.app_name);
        builder.setMessage(R.string.ui_save_text);

        builder.setPositiveButton(R.string.ui_save,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        saveContent();
                        mDoNotBackup = true;
                    }
                });
        builder.setNegativeButton(R.string.ui_cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        builder.setNeutralButton(R.string.ui_no_save,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        runAfterSave();
                        mDoNotBackup = true;
                    }
                });

        builder.create().show();

    }

    // protected boolean checkUsbConnection(){
    // return (usbService != null);
    // }

    /**
     *
     */
    protected void newContent() {
        mAfterSave = new Runnable() {
            public void run() {
                doClearContents();
            }
        };

        // promptSaveDirty();
        // added by nkl
        runAfterSave();
    }

    /**
     * Runs the after save to complete
     */
    protected void runAfterSave() {
        if (mAfterSave == null) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "No After shave, ignoring...");
            return;
        }

        mAfterSave.run();

        mAfterSave = null;
    }

    /**
     * Starts an activity to choose a file to open
     */
    protected void openFile() {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "openFile");

        mAfterSave = new Runnable() {
            public void run() {
                Intent open = new Intent();
                open.setClass(getApplicationContext(), TedOpenActivity.class);
                // open = new Intent(ACTION_OPEN);
                open.putExtra(EXTRA_REQUEST_CODE, REQUEST_OPEN);
                try {
                    startActivityForResult(open, REQUEST_OPEN);
                } catch (ActivityNotFoundException e) {
                    Crouton.showText(TedActivity.this,
                            R.string.toast_activity_open, Style.ALERT);
                }
            }
        };

        // change by nkl
        // promptSaveDirty();

        // added by nkl
        runAfterSave();
    }

    XYSeries chartSeries = null;

    private void openchart1(String filep) {
        String filepath = filep;

        boolean isLogsExist = false;

        if (filepath.contains("R1")) {
            isLogsExist = true;
            currentdataset.getSeriesAt(0).clear();
            chartSeries = currentdataset.getSeriesAt(0);
        } else if (filepath.contains("R2")) {
            isLogsExist = true;
            currentdataset.getSeriesAt(1).clear();
            chartSeries = currentdataset.getSeriesAt(1);
        } else if (filepath.contains("R3")) {
            isLogsExist = true;
            currentdataset.getSeriesAt(2).clear();
            chartSeries = currentdataset.getSeriesAt(2);
        }

        if (!isLogsExist) {
//			Toast.makeText(TedActivity.this,
//					"Required Log files not available",
//					Toast.LENGTH_SHORT).show();
            return;
        }

        final Handler mChartHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                // TODO Auto-generated method stub
                // super.handleMessage(msg);
                String strMsg = (String) msg.obj;
                String[] arr = strMsg.split(",");
                int c = Integer.parseInt(arr[0]);
                double co2 = Double.parseDouble(arr[1]);

                int yMax = (int) renderer.getYAxisMax();
                if (co2 >= yMax) {
                    // int vmax = (int) (co2 + (co2*15)/100f);
                    // int vmax_extra = (int) Math.ceil(1.5 *
                    // (co2/20)) ;
                    // int vmax = co2 + vmax_extra;
                    if (chartSeries.getItemCount() == 0) {
                        int vmax = (int) (3 * co2);
                        renderer.setYAxisMax(vmax);
                    } else {
                        int vmax = (int) (co2 + (co2 * 15) / 100f);
                        renderer.setYAxisMax(vmax);
                    }

                }

                chartSeries.add(c, co2);
                mChartView.repaint();
            }
        };

        new AsyncTask<String, Integer, String>() {

            @Override
            protected String doInBackground(String... params) {
                // TODO Auto-generated method stub
                String fpath = params[0];

                File file = new File(fpath);
                try {
                    BufferedReader br = new BufferedReader(
                            new FileReader(file));
                    String line;

                    int c = 1;
                    while ((line = br.readLine()) != null) {
                        c++;
                    }
                    br.close();

                    if (fpath.contains("R1")) {
                        c = 1;
                    } else if (fpath.contains("R2")) {
                        //
                    } else if (fpath.contains("R3")) {
                        c = 2 * c;
                    }

                    br = new BufferedReader(
                            new FileReader(file));

                    while ((line = br.readLine()) != null) {
                        if (!line.equals("")) {
                            String[] arr = line.split(",");
                            double co2 = Double
                                    .parseDouble(arr[1]);

                            Message msg = new Message();
                            msg.obj = c + "," + co2;
                            c++;

                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch
                                // block
                                e.printStackTrace();
                            }
                            mChartHandler.sendMessageDelayed(
                                    msg, 0);

                        }
                    }
                    br.close();

                } catch (IOException e) {
                    // You'll need to add proper error handling
                    // here
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            protected void onPostExecute(String result) {
                Toast.makeText(TedActivity.this,
                        "File reading done", Toast.LENGTH_SHORT)
                        .show();
                // dialog.cancel();
            }

            ;
        }.execute(new String[]{filepath});
    }

    /**
     * render chart from selected file
     */
    private void openChart() {
        if (isTimerRunning) {
            Toast.makeText(TedActivity.this, "Timer is running. Please wait",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        File dir = new File(Environment.getExternalStorageDirectory()
                + "/AEToCLogs_MES");

        if (!dir.exists()) {
            Toast.makeText(TedActivity.this, "Logs diretory is not available",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String[] filenameArry = dir.list();
        if (filenameArry == null) {
            Toast.makeText(TedActivity.this,
                    "Logs not available. Logs directory is empty",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // if(filenameArry != null){
        // for(int i=0;i<filenameArry.length;i++){
        // if(filenameArry[i].contains(substr)){
        // File f = new
        // File(Environment.getExternalStorageDirectory()+"/AEToCLogs/"+filenameArry[i]);
        // f.delete();
        // }
        // }
        // }

        final CharSequence[] items = new CharSequence[filenameArry.length];
        for (int i = 0; i < filenameArry.length; i++) {
            items[i] = filenameArry[i];
        }

        // boolean[] checkedItems = new boolean[] { false,
        // false,false,false,false };
        // final HashMap<Integer, Boolean> mapItemsState = new HashMap<>();

        AlertDialog.Builder alert = new AlertDialog.Builder(TedActivity.this);
        alert.setTitle("Select file");
        alert.setSingleChoiceItems(items, 0,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub
                        String filename = (String) items[which];

                        boolean isLogsExist = false;

                        if (filename.contains("R1")) {
                            isLogsExist = true;
                            currentdataset.getSeriesAt(0).clear();
                            chartSeries = currentdataset.getSeriesAt(0);
                        } else if (filename.contains("R2")) {
                            isLogsExist = true;
                            currentdataset.getSeriesAt(1).clear();
                            chartSeries = currentdataset.getSeriesAt(1);
                        } else if (filename.contains("R3")) {
                            isLogsExist = true;
                            currentdataset.getSeriesAt(2).clear();
                            chartSeries = currentdataset.getSeriesAt(2);
                        }

                        if (!isLogsExist) {
                            Toast.makeText(TedActivity.this,
                                    "Required Log files not available",
                                    Toast.LENGTH_SHORT).show();
                            dialog.cancel();
                            return;
                        }

                        final Handler mChartHandler = new Handler() {
                            @Override
                            public void handleMessage(Message msg) {
                                // TODO Auto-generated method stub
                                // super.handleMessage(msg);
                                String strMsg = (String) msg.obj;
                                String[] arr = strMsg.split(",");
                                int c = Integer.parseInt(arr[0]);
                                double co2 = Double.parseDouble(arr[1]);

                                int yMax = (int) renderer.getYAxisMax();
                                if (co2 >= yMax) {
                                    // int vmax = (int) (co2 + (co2*15)/100f);
                                    // int vmax_extra = (int) Math.ceil(1.5 *
                                    // (co2/20)) ;
                                    // int vmax = co2 + vmax_extra;
                                    if (chartSeries.getItemCount() == 0) {
                                        int vmax = (int) (3 * co2);
                                        renderer.setYAxisMax(vmax);
                                    } else {
                                        int vmax = (int) (co2 + (co2 * 15) / 100f);
                                        renderer.setYAxisMax(vmax);
                                    }

                                }

                                chartSeries.add(c, co2);
                                mChartView.repaint();
                            }
                        };

                        new AsyncTask<String, Integer, String>() {

                            @Override
                            protected String doInBackground(String... params) {
                                // TODO Auto-generated method stub
                                String fname = params[0];

                                File file = new File(Environment
                                        .getExternalStorageDirectory()
                                        + "/AEToCLogs_MES/" + fname);
                                try {
                                    BufferedReader br = new BufferedReader(
                                            new FileReader(file));
                                    String line;

                                    int c = 1;
                                    while ((line = br.readLine()) != null) {
                                        c++;
                                    }
                                    br.close();

                                    if (fname.contains("R1")) {
                                        c = 1;
                                    } else if (fname.contains("R2")) {
                                        //
                                    } else if (fname.contains("R3")) {
                                        c = 2 * c;
                                    }

                                    br = new BufferedReader(
                                            new FileReader(file));

                                    while ((line = br.readLine()) != null) {
                                        if (!line.equals("")) {
                                            String[] arr = line.split(",");
                                            double co2 = Double
                                                    .parseDouble(arr[1]);

                                            Message msg = new Message();
                                            msg.obj = c + "," + co2;
                                            c++;

                                            try {
                                                Thread.sleep(50);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch
                                                // block
                                                e.printStackTrace();
                                            }
                                            mChartHandler.sendMessageDelayed(
                                                    msg, 0);

                                        }
                                    }
                                    br.close();

                                } catch (IOException e) {
                                    // You'll need to add proper error handling
                                    // here
                                    e.printStackTrace();
                                }

                                return null;
                            }

                            @Override
                            protected void onPostExecute(String result) {
                                Toast.makeText(TedActivity.this,
                                        "File reading done", Toast.LENGTH_SHORT)
                                        .show();
                                // dialog.cancel();
                            }

                            ;
                        }.execute(new String[]{filename});

                        dialog.cancel();
                    }
                });

        // alert.setPositiveButton("Clear",
        // new DialogInterface.OnClickListener() {
        //
        // @Override
        // public void onClick(DialogInterface dialog,
        // int which) {
        //
        // }
        // });
        alert.setNegativeButton("Close", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                // TODO Auto-generated method stub
                dialog.cancel();
            }
        });
        alert.create().show();
    }

    /**
     * Open the recent files activity to open
     */
    protected void openRecentFile() {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "openRecentFile");

        if (RecentFiles.getRecentFiles().size() == 0) {
            Crouton.showText(this, R.string.toast_no_recent_files, Style.ALERT);
            return;
        }

        mAfterSave = new Runnable() {
            public void run() {
                Intent open;

                open = new Intent();
                open.setClass(TedActivity.this, TedOpenRecentActivity.class);
                try {
                    startActivityForResult(open, REQUEST_OPEN);
                } catch (ActivityNotFoundException e) {
                    Crouton.showText(TedActivity.this,
                            R.string.toast_activity_open_recent, Style.ALERT);
                }
            }
        };

        // promptSaveDirty();
        // added by nkl
        runAfterSave();
    }

    /**
     * Warns the user that the next back press will qui the application, or quit
     * if the warning has already been shown
     */
    protected void warnOrQuit() {
        if (mWarnedShouldQuit) {
            quit();
        } else {
            Crouton.showText(this, R.string.toast_warn_no_undo_will_quit,
                    Style.INFO);
            mWarnedShouldQuit = true;
        }
    }

    /**
     * Quit the app (user pressed back)
     */
    protected void quit() {
        mAfterSave = new Runnable() {
            public void run() {


                finish();
            }
        };

        // promptSaveDirty();
        // added by nkl
        runAfterSave();
    }

    /**
     * General save command : check if a path exist for the current content,
     * then save it , else invoke the {@link TedActivity#saveContentAs()} method
     */
    protected void saveContent() {
        if ((mCurrentFilePath == null) || (mCurrentFilePath.length() == 0)) {
            saveContentAs();
        } else {
            doSaveFile(mCurrentFilePath);
        }
    }

    /**
     * General Save as command : prompt the user for a location and file name,
     * then save the editor'd content
     */
    protected void saveContentAs() {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "saveContentAs");
        Intent saveAs;
        saveAs = new Intent();
        saveAs.setClass(this, TedSaveAsActivity.class);
        try {
            startActivityForResult(saveAs, REQUEST_SAVE_AS);
        } catch (ActivityNotFoundException e) {
            Crouton.showText(this, R.string.toast_activity_save_as, Style.ALERT);
        }
    }

    /**
     * Opens / close the search interface
     */
    // protected void search() {
    // if (BuildConfig.DEBUG)
    // Log.d(TAG, "search");
    // switch (mSearchLayout.getVisibility()) {
    // case View.GONE:
    // mSearchLayout.setVisibility(View.VISIBLE);
    // break;
    // case View.VISIBLE:
    // default:
    // mSearchLayout.setVisibility(View.GONE);
    // break;
    // }
    // }

    /**
     * Uses the user input to search a file
     */
    // protected void searchNext() {
    // String search, text;
    // int selection, next;
    //
    // search = mSearchInput.getText().toString();
    // text = mEditor.getText().toString();
    // selection = mEditor.getSelectionEnd();
    //
    // if (search.length() == 0) {
    // Crouton.showText(this, R.string.toast_search_no_input, Style.INFO);
    // return;
    // }
    //
    // if (!Settings.SEARCHMATCHCASE) {
    // search = search.toLowerCase();
    // text = text.toLowerCase();
    // }
    //
    // next = text.indexOf(search, selection);
    //
    // if (next > -1) {
    // mEditor.setSelection(next, next + search.length());
    // if (!mEditor.isFocused())
    // mEditor.requestFocus();
    // } else {
    // if (Settings.SEARCHWRAP) {
    // next = text.indexOf(search);
    // if (next > -1) {
    // mEditor.setSelection(next, next + search.length());
    // if (!mEditor.isFocused())
    // mEditor.requestFocus();
    // } else {
    // Crouton.showText(this, R.string.toast_search_not_found,
    // Style.INFO);
    // }
    // } else {
    // Crouton.showText(this, R.string.toast_search_eof, Style.INFO);
    // }
    // }
    // }

    /**
     * Uses the user input to search a file
     */
    // protected void searchPrevious() {
    // String search, text;
    // int selection, next;
    //
    // search = mSearchInput.getText().toString();
    // text = mEditor.getText().toString();
    // selection = mEditor.getSelectionStart() - 1;
    //
    // if (search.length() == 0) {
    // Crouton.showText(this, R.string.toast_search_no_input, Style.INFO);
    // return;
    // }
    //
    // if (!Settings.SEARCHMATCHCASE) {
    // search = search.toLowerCase();
    // text = text.toLowerCase();
    // }
    //
    // next = text.lastIndexOf(search, selection);
    //
    // if (next > -1) {
    // mEditor.setSelection(next, next + search.length());
    // if (!mEditor.isFocused())
    // mEditor.requestFocus();
    // } else {
    // if (Settings.SEARCHWRAP) {
    // next = text.lastIndexOf(search);
    // if (next > -1) {
    // mEditor.setSelection(next, next + search.length());
    // if (!mEditor.isFocused())
    // mEditor.requestFocus();
    // } else {
    // Crouton.showText(this, R.string.toast_search_not_found,
    // Style.INFO);
    // }
    // } else {
    // Crouton.showText(this, R.string.toast_search_eof, Style.INFO);
    // }
    // }
    // }

    /**
     * Opens the settings activity
     */
    protected void settingsActivity() {

        mAfterSave = new Runnable() {
            public void run() {
                Intent settings = new Intent();
                settings.setClass(TedActivity.this, TedSettingsActivity.class);
                try {
                    startActivity(settings);
                } catch (ActivityNotFoundException e) {
                    Crouton.showText(TedActivity.this,
                            R.string.toast_activity_settings, Style.ALERT);
                }
            }
        };

        // promptSaveDirty();
        // added by nkl
        runAfterSave();
    }

    /**
     * Update the window title
     */
    @TargetApi(11)
    protected void updateTitle() {
        String title;
        String name;

        // name = "?";
        // if ((mCurrentFileName != null) && (mCurrentFileName.length() > 0))
        // name = mCurrentFileName;
        //
        // if (mReadOnly)
        // title = getString(R.string.title_editor_readonly, name);
        // else if (mDirty)
        // title = getString(R.string.title_editor_dirty, name);
        // else
        // title = getString(R.string.title_editor, name);
        //
        // setTitle(title);
        //
        // if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB)
        // invalidateOptionsMenu();
    }

    private void startService(Class<?> service,
                              ServiceConnection serviceConnection, Bundle extras) {
        Intent startService = new Intent(this, service);
        startService(startService);

        // if(UsbService.SERVICE_CONNECTED == false)
        // {
        // Intent startService = new Intent(this, service);
        // if(extras != null && !extras.isEmpty())
        // {
        // Set<String> keys = extras.keySet();
        // for(String key: keys)
        // {
        // String extra = extras.getString(key);
        // startService.putExtra(key, extra);
        // }
        // }
        // startService(startService);
        // }
        // Intent bindingIntent = new Intent(this, service);
        // bindService(bindingIntent, serviceConnection,
        // Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        filter.addAction(ACTION_USB_DETACHED);
        filter.addAction(ACTION_USB_ATTACHED);
        registerReceiver(mUsbReceiver, filter);
    }

	/*
     * This handler will be passed to UsbService. Dara received from serial port
	 * is displayed through this handler
	 */
    // private static class MyHandler extends Handler {
    // private final WeakReference<TedActivity> mActivity;
    //
    // public MyHandler(TedActivity activity) {
    // mActivity = new WeakReference<TedActivity>(activity);
    // }
    //
    // @Override
    // public void handleMessage(Message msg) {
    // switch (msg.what) {
    // case UsbService.MESSAGE_FROM_SERIAL_PORT:
    // String data = (String) msg.obj;
    // mActivity.get().txtOutput.append("Rx: " + data + "\n");
    // mActivity.get().mScrollView.smoothScrollTo(0,
    // mActivity.get().txtOutput.getBottom());
    // break;
    // }
    // }
    // }

    /*
	 * Notifications from UsbService will be received here.
	 */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equals(
                    UsbService.ACTION_USB_PERMISSION_GRANTED)) // USB PERMISSION
            // GRANTED
            {
                isUsbConnected = true;
                invalidateOptionsMenu();

                Toast customToast = new Toast(arg0);
                customToast = Toast.makeText(arg0, "USB Ready",
                        Toast.LENGTH_LONG);
                customToast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 0);
                customToast.show();
            } else if (arg1.getAction().equals(
                    UsbService.ACTION_USB_PERMISSION_NOT_GRANTED)) // USB
            // PERMISSION
            // NOT
            // GRANTED
            {
                isUsbConnected = false;
                invalidateOptionsMenu();

                // Toast.makeText(arg0, "USB Permission not granted",
                // Toast.LENGTH_SHORT).show();
                Toast customToast = new Toast(arg0);
                customToast = Toast.makeText(arg0,
                        "USB Permission not granted", Toast.LENGTH_LONG);
                customToast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 0);
                customToast.show();

                finish();
            } else if (arg1.getAction().equals(UsbService.ACTION_NO_USB)) // NO
            // USB
            // CONNECTED
            {
                isUsbConnected = false;
                invalidateOptionsMenu();

                // Toast.makeText(arg0, "No USB connected",
                // Toast.LENGTH_SHORT).show();
                Toast customToast = new Toast(arg0);
                customToast = Toast.makeText(arg0, "No USB connected",
                        Toast.LENGTH_LONG);
                customToast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 0);
                customToast.show();
            } else if (arg1.getAction().equals(
                    UsbService.ACTION_USB_DISCONNECTED)) // USB DISCONNECTED
            {
                isUsbConnected = false;
                invalidateOptionsMenu();

                // Toast.makeText(arg0, "USB disconnected",
                // Toast.LENGTH_SHORT).show();
                Toast customToast = new Toast(arg0);
                customToast = Toast.makeText(arg0, "USB disconnected",
                        Toast.LENGTH_LONG);
                customToast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 0);
                customToast.show();
                if (UsbService.mHandlerStop != null) {
                    UsbService.mHandlerStop.sendEmptyMessage(0);
                }
                // finish();
            } else if (arg1.getAction().equals(
                    UsbService.ACTION_USB_NOT_SUPPORTED)) // USB NOT SUPPORTED
            {
                isUsbConnected = false;
                invalidateOptionsMenu();

                // Toast.makeText(arg0, "USB device not supported",
                // Toast.LENGTH_SHORT).show();
                Toast customToast = new Toast(arg0);
                customToast = Toast.makeText(arg0, "USB device not supported",
                        Toast.LENGTH_LONG);
                customToast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 0);
                customToast.show();
            } else if (arg1.getAction().equals(ACTION_USB_ATTACHED)) {
                Toast customToast = new Toast(arg0);
                customToast = Toast.makeText(arg0, "USB Device Attached",
                        Toast.LENGTH_LONG);
                customToast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 0);
                customToast.show();

                startService(new Intent(TedActivity.this, UsbService.class));

            } else if (arg1.getAction().equals(ACTION_USB_DETACHED)) {
                isUsbConnected = false;
                invalidateOptionsMenu();

                Toast customToast = new Toast(arg0);
                customToast = Toast.makeText(arg0, "USB Device Detached",
                        Toast.LENGTH_LONG);
                customToast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 0);
                customToast.show();

                if (UsbService.mHandlerStop != null) {
                    UsbService.mHandlerStop.sendEmptyMessage(0);
                }

                // finish();
            }

        }
    };

    // private final ServiceConnection usbConnection = new ServiceConnection()
    // {
    // @Override
    // public void onServiceConnected(ComponentName arg0, IBinder arg1)
    // {
    // usbService = ((UsbService.UsbBinder) arg1).getService();
    // usbService.setHandler(mHandler);
    //
    // //Toast.makeText(TedActivity.this, text, duration)
    // //setFilters();
    // //invalidateOptionsMenu();
    // }
    //
    // @Override
    // public void onServiceDisconnected(ComponentName arg0)
    // {
    // usbService = null;
    // isUsbConnected = false;
    // invalidateOptionsMenu();
    // //unregisterReceiver(mUsbReceiver);
    //
    // //if(!isFinishing())
    // //invalidateOptionsMenu();
    // }
    // };

    /**
     * the text editor
     */
    protected AdvancedEditText mEditor;
    /**
     * the path of the file currently opened
     */
    protected String mCurrentFilePath;
    /**
     * the name of the file currently opened
     */
    protected String mCurrentFileName;
    /**
     * the runable to run after a save
     */
    protected Runnable mAfterSave; // Mennen ? Axe ?

    /**
     * is dirty ?
     */
    protected boolean mDirty;
    /**
     * is read only
     */
    protected boolean mReadOnly;

    /** the search layout root */
    // protected View mSearchLayout;
    /** the search input */
    // protected EditText mSearchInput;

    /**
     * Undo watcher
     */
    protected TextChangeWatcher mWatcher;
    protected boolean mInUndo;
    protected boolean mWarnedShouldQuit;
    protected boolean mDoNotBackup;

    /**
     * are we in a post activity result ?
     */
    protected boolean mReadIntent;

}