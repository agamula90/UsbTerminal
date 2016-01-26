package com.ismet.usbterminal.updated.mainscreen;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.ismet.usbterminal.BuildConfig;
import com.ismet.usbterminal.EmptyFragment;
import com.ismet.usbterminal.R;
import com.ismet.usbterminal.updated.EToCApplication;
import com.ismet.usbterminal.updated.TedOpenActivity;
import com.ismet.usbterminal.updated.TedOpenRecentActivity;
import com.ismet.usbterminal.updated.TedSaveAsActivity;
import com.ismet.usbterminal.updated.TedSettingsActivity;
import com.ismet.usbterminal.updated.data.PullState;
import com.ismet.usbterminal.updated.services.PullStateManagingService;
import com.ismet.usbterminal.updated.services.UsbService;
import com.ismet.usbterminal.updated.UsbServiceWritable;
import com.ismet.usbterminal.updated.data.AppData;
import com.ismet.usbterminal.updated.data.PowerState;
import com.ismet.usbterminal.updated.data.PrefConstants;
import com.ismet.usbterminal.updated.data.TemperatureData;
import com.ismet.usbterminal.updated.mainscreen.tasks.EToCOpenChartTask;
import com.ismet.usbterminal.updated.mainscreen.tasks.SendDataToUsbTask;
import com.ismet.usbterminal.utils.AlertDialogTwoButtonsCreator;
import com.ismet.usbterminal.utils.GraphData;
import com.ismet.usbterminal.utils.GraphPopulatorUtils;
import com.ismet.usbterminal.utils.Utils;
import com.proggroup.areasquarecalculator.activities.BaseAttachableActivity;

import org.achartengine.GraphicalView;
import org.achartengine.chart.AbstractChart;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.neofonie.mobile.app.android.widget.crouton.Crouton;
import de.neofonie.mobile.app.android.widget.crouton.Style;
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
import static fr.xgouchet.texteditor.common.Constants.*;

public class EToCMainActivity extends BaseAttachableActivity implements TextWatcher {

    private Handler mHandler;

    /*
     * Notifications from UsbService will be received here.
     */
    private final BroadcastReceiver mUsbReceiver = new EToCMainUsbReceiver(this);

    /**
     * the path of the file currently opened
     */
    protected String mCurrentFilePath;

    /**
     * the name of the file currently opened
     */
    protected String mCurrentFileName;

    /**
     * is dirty ?
     */
    protected boolean mDirty;

    /**
     * is read only
     */
    protected boolean mReadOnly;

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

    /**
     * the text editor
     */
    protected AdvancedEditText mAdvancedEditText;

    private boolean mIsUsbConnected = false;

    // String filename = "";
    private int countMeasures = 0, oldCountMeasures = 0;
    //String chart_time = "";

    private boolean mIsTimerRunning = false;

    private int mReadingCount = 0;

    // int idx_count = 0;
    private int mChartIndex = 0;

    private String mChartDate = "", mSubDirDate = null;

    private Map<Integer, String> mMapChartIndexToDate = new HashMap<>();

    private SharedPreferences mPrefs;

    private XYSeries mCurrentSeries;

    private GraphicalView mChartView;

    private XYMultipleSeriesDataset mGraphSeriesDataset;

    private XYMultipleSeriesRenderer mRenderer;

    private XYSeries mGraphSeries = null;

    private ServiceConnection mServiceConnection;

    private boolean mServiceBounded;

    private UsbServiceWritable mUsbServiceWritable;

    private ExecutorService mExecutor;
    //private CountDownTimer ctimer;

    private EToCOpenChartTask mEToCOpenChartTask;

    private SendDataToUsbTask mSendDataToUsbTask;

    private Runnable mRunnable;

    private TextView mTxtOutput;

    private ScrollView mScrollView;

    private Button mButtonOn1, mButtonOn2, mButtonOn3;

    private Button mSendButton, mButtonClear, mButtonMeasure;

    private Button mPower, mTemperature, mCo2;

    private LinearLayout mExportLayout, mMarginLayout;

    private TemperatureData mTemperatureData;

    private int mTemperatureShift;

    private
    @PowerState
    int mPowerState;

    /**
     * @see android.app.Activity#onCreate(Bundle)
     */
    protected void onCreate(Bundle savedInstanceState) {
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        //        WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onCreate");

        mExecutor = Executors.newSingleThreadExecutor();

        mPowerState = PowerState.OFF;

        mServiceBounded = false;

        mPrefs = PreferenceManager.getDefaultSharedPreferences(EToCMainActivity.this);

        Settings.updateFromPreferences(getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE));

        loadPreferencesFromLocalData();

        mHandler = new EToCMainHandler(this);

        //
        mReadIntent = true;

        mExportLayout = getExportLayout();
        mMarginLayout = (LinearLayout) findViewById(R.id.margin_layout);

        // editor
        mAdvancedEditText = (AdvancedEditText) findViewById(R.id.editor);
        mAdvancedEditText.addTextChangedListener(this);
        mAdvancedEditText.updateFromSettings();
        mWatcher = new TextChangeWatcher();
        mWarnedShouldQuit = false;
        mDoNotBackup = false;

        mTxtOutput = (TextView) findViewById(R.id.output);
        mScrollView = (ScrollView) findViewById(R.id.mScrollView);

        mPower = (Button) findViewById(R.id.power);
        mPower.setText(mPrefs.getString(PrefConstants.POWER_ON_NAME, PrefConstants
                .POWER_ON_NAME_DEFAULT));
        mPower.setTag(PrefConstants.POWER_ON_NAME_DEFAULT.toLowerCase());

        mPower.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String powerOnName = mPrefs.getString(PrefConstants.POWER_ON_NAME, PrefConstants
                        .POWER_ON_NAME_DEFAULT);
                String powerOffName = mPrefs.getString(PrefConstants.POWER_OFF_NAME, PrefConstants
                        .POWER_OFF_NAME_DEFAULT);

                String s = mPower.getTag().toString();

                boolean powerOn;

                if (s.equals(PrefConstants.POWER_ON_NAME_DEFAULT.toLowerCase())) {
                    mPower.setText(powerOffName);
                    mPower.setTag(PrefConstants.POWER_OFF_NAME_DEFAULT.toLowerCase());
                    powerOn = true;
                } else {
                    mPower.setText(powerOnName);
                    mPower.setTag(PrefConstants.POWER_ON_NAME_DEFAULT.toLowerCase());
                    powerOn = false;
                }

                mPowerState = powerOn ? PowerState.ON : PowerState.OFF;
            }
        });

        mPower.setOnLongClickListener(new OnLongClickListener() {

            private EditText editOn, editOff, editOn1, editOff1;

            @Override
            public boolean onLongClick(View v) {
                AlertDialogTwoButtonsCreator.OnInitLayoutListener initLayoutListener = new
                        AlertDialogTwoButtonsCreator.OnInitLayoutListener() {

                            @Override
                            public void onInitLayout(View contentView) {
                                editOn = (EditText) contentView.findViewById(R.id.editOn);
                                editOff = (EditText) contentView.findViewById(R.id.editOff);
                                editOn1 = (EditText) contentView.findViewById(R.id.editOn1);
                                editOff1 = (EditText) contentView.findViewById(R.id.editOff1);

                                String str_on_name = mPrefs.getString(PrefConstants.POWER_ON_NAME,
                                        PrefConstants.POWER_ON_NAME_DEFAULT);
                                String str_off_name = mPrefs.getString(PrefConstants
                                        .POWER_OFF_NAME, PrefConstants.POWER_OFF_NAME_DEFAULT);

                                String str_on = mPrefs.getString(PrefConstants.POWER_ON,
                                        PrefConstants.POWER_ON_COMMAND_DEFAULT);
                                String str_off = mPrefs.getString(PrefConstants.POWER_OFF,
                                        PrefConstants.POWER_OFF_COMMAND_DEFAULT);

                                editOn.setText(str_on);
                                editOff.setText(str_off);
                                editOn1.setText(str_on_name);
                                editOff1.setText(str_off_name);
                            }
                        };

                DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener
                        () {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService
                                (Context.INPUT_METHOD_SERVICE);

                        inputManager.hideSoftInputFromWindow(((AlertDialog) dialog)
                                .getCurrentFocus().getWindowToken(), 0);

                        String strOn = editOn.getText().toString();
                        String strOff = editOff.getText().toString();
                        String strOn1 = editOn1.getText().toString();
                        String strOff1 = editOff1.getText().toString();

                        if (strOn.equals("") || strOff.equals("") || strOn1.equals("") ||
                                strOff1.equals("")) {
                            Toast.makeText(EToCMainActivity.this, "Please enter all values", Toast
                                    .LENGTH_LONG).show();
                            return;
                        }

                        Editor edit = mPrefs.edit();
                        edit.putString(PrefConstants.POWER_ON, strOn);
                        edit.putString(PrefConstants.POWER_OFF, strOff);
                        edit.putString(PrefConstants.POWER_ON_NAME, strOn1);
                        edit.putString(PrefConstants.POWER_OFF_NAME, strOff1);
                        edit.apply();

                        String a = mPrefs.getString(PrefConstants.POWER_ON_NAME, "asd");

                        String s = mPower.getTag().toString();
                        if (s.equals(PrefConstants.POWER_ON_NAME_DEFAULT.toLowerCase())) {
                            mPower.setText(strOn1);
                        } else {
                            mPower.setText(strOff1);
                        }

                        dialog.cancel();
                    }
                };

                DialogInterface.OnClickListener cancelListener = new DialogInterface
                        .OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService
                                (Context.INPUT_METHOD_SERVICE);

                        inputManager.hideSoftInputFromWindow(((AlertDialog) dialog)
                                .getCurrentFocus().getWindowToken(), 0);
                        dialog.cancel();
                    }
                };

                AlertDialogTwoButtonsCreator.createTwoButtonsAlert(EToCMainActivity.this, R.layout
                                .layout_dialog_on_off, "Set On/Off commands", okListener,
                        cancelListener, initLayoutListener).create().show();

                return true;
            }
        });

        mTemperature = (Button) findViewById(R.id.temperature);

        changeBackground(mTemperature, false);

        mCo2 = (Button) findViewById(R.id.co2);

        changeBackground(mCo2, false);

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
        // mTxtOutput.append("asdf\n");
        // mScrollView.smoothScrollTo(0, mTxtOutput.getBottom());
        // }
        // });
        // }
        // }, 1000, 1000);

        mButtonOn1 = (Button) findViewById(R.id.buttonOn1);
        mButtonOn1.setText(mPrefs.getString(PrefConstants.ON_NAME1, PrefConstants
                .ON_NAME_DEFAULT));
        mButtonOn1.setTag(PrefConstants.ON_NAME_DEFAULT.toLowerCase());
        mButtonOn1.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // ASCII
                String str_on_name1t = mPrefs.getString(PrefConstants.ON_NAME1, PrefConstants
                        .ON_NAME_DEFAULT);
                String str_off_name1t = mPrefs.getString(PrefConstants.OFF_NAME1, PrefConstants
                        .OFF_NAME_DEFAULT);

                String s = mButtonOn1.getTag().toString();
                String command = "";//"/5H1000R";
                if (s.equals(PrefConstants.ON_NAME_DEFAULT.toLowerCase())) {
                    command = mPrefs.getString(PrefConstants.ON1, "");
                    mButtonOn1.setText(str_off_name1t);
                    mButtonOn1.setTag(PrefConstants.OFF_NAME_DEFAULT.toLowerCase());
                } else {
                    command = mPrefs.getString(PrefConstants.OFF1, "");
                    mButtonOn1.setText(str_on_name1t);
                    mButtonOn1.setTag(PrefConstants.ON_NAME_DEFAULT.toLowerCase());
                }

	            EToCApplication.getInstance().setPullState(PullState.NONE);
	            startService(PullStateManagingService.intentForService(EToCMainActivity.this,
			            false));
	            sendCommand(command);
	            mHandler.postDelayed(new Runnable() {

		            @Override
		            public void run() {
			            startService(PullStateManagingService.intentForService(EToCMainActivity
					            .this, true));
		            }
	            }, 1000);
            }
        });

        mButtonOn1.setOnLongClickListener(new OnLongClickListener() {

            private EditText editOn, editOff, editOn1, editOff1;

            @Override
            public boolean onLongClick(View v) {
                AlertDialogTwoButtonsCreator.OnInitLayoutListener initLayoutListener = new
                        AlertDialogTwoButtonsCreator.OnInitLayoutListener() {

                            @Override
                            public void onInitLayout(View contentView) {
                                editOn = (EditText) contentView.findViewById(R.id.editOn);
                                editOff = (EditText) contentView.findViewById(R.id.editOff);
                                editOn1 = (EditText) contentView.findViewById(R.id.editOn1);
                                editOff1 = (EditText) contentView.findViewById(R.id.editOff1);

                                String str_on = mPrefs.getString(PrefConstants.ON1, "");
                                String str_off = mPrefs.getString(PrefConstants.OFF1, "");
                                String str_on_name = mPrefs.getString(PrefConstants.ON_NAME1,
                                        PrefConstants.ON_NAME_DEFAULT);
                                String str_off_name = mPrefs.getString(PrefConstants.OFF_NAME1,
                                        PrefConstants.OFF_NAME_DEFAULT);

                                editOn.setText(str_on);
                                editOff.setText(str_off);
                                editOn1.setText(str_on_name);
                                editOff1.setText(str_off_name);
                            }
                        };

                DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener
                        () {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService
                                (Context.INPUT_METHOD_SERVICE);

                        inputManager.hideSoftInputFromWindow(((AlertDialog) dialog)
                                .getCurrentFocus().getWindowToken(), 0);

                        String strOn = editOn.getText().toString();
                        String strOff = editOff.getText().toString();
                        String strOn1 = editOn1.getText().toString();
                        String strOff1 = editOff1.getText().toString();

                        if (strOn.equals("") || strOff.equals("") || strOn1.equals("") ||
                                strOff1.equals("")) {
                            Toast.makeText(EToCMainActivity.this, "Please enter all values", Toast
                                    .LENGTH_LONG).show();
                            return;
                        }


                        Editor edit = mPrefs.edit();
                        edit.putString(PrefConstants.ON1, strOn);
                        edit.putString(PrefConstants.OFF1, strOff);
                        edit.putString(PrefConstants.ON_NAME1, strOn1);
                        edit.putString(PrefConstants.OFF_NAME1, strOff1);
                        edit.apply();

                        String s = mButtonOn1.getTag().toString();
                        if (s.equals("on")) {
                            mButtonOn1.setText(strOn1);
                        } else {
                            mButtonOn1.setText(strOff1);
                        }

                        dialog.cancel();
                    }
                };

                DialogInterface.OnClickListener cancelListener = new DialogInterface
                        .OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService
                                (Context.INPUT_METHOD_SERVICE);

                        inputManager.hideSoftInputFromWindow(((AlertDialog) dialog)
                                .getCurrentFocus().getWindowToken(), 0);
                        dialog.cancel();
                    }
                };

                AlertDialogTwoButtonsCreator.createTwoButtonsAlert(EToCMainActivity.this, R.layout
                                .layout_dialog_on_off, "Set On/Off " + "commands", okListener,
                        cancelListener, initLayoutListener).create().show();

                return true;
            }
        });

        mButtonOn2 = (Button) findViewById(R.id.buttonOn2);
        mButtonOn2.setText(mPrefs.getString(PrefConstants.ON_NAME2, PrefConstants
                .ON_NAME_DEFAULT));
        mButtonOn2.setTag(PrefConstants.ON_NAME_DEFAULT.toLowerCase());
        mButtonOn2.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // ASCII
                String str_on_name2t = mPrefs.getString(PrefConstants.ON_NAME2, PrefConstants
                        .ON_NAME_DEFAULT);
                String str_off_name2t = mPrefs.getString(PrefConstants.OFF_NAME2, PrefConstants
                        .OFF_NAME_DEFAULT);

                String s = mButtonOn2.getTag().toString();
                String command = "";//"/5H1000R";
                if (s.equals(PrefConstants.ON_NAME_DEFAULT.toLowerCase())) {
                    command = mPrefs.getString(PrefConstants.ON2, "");
                    mButtonOn2.setText(str_off_name2t);
                    mButtonOn2.setTag(PrefConstants.OFF_NAME_DEFAULT.toLowerCase());
                } else {
                    command = mPrefs.getString(PrefConstants.OFF2, "");
                    mButtonOn2.setText(str_on_name2t);
                    mButtonOn2.setTag(PrefConstants.ON_NAME_DEFAULT.toLowerCase());
                }

	            EToCApplication.getInstance().setPullState(PullState.NONE);
	            startService(PullStateManagingService.intentForService(EToCMainActivity.this,
			            false));
	            sendCommand(command);
	            mHandler.postDelayed(new Runnable() {

		            @Override
		            public void run() {
			            startService(PullStateManagingService.intentForService(EToCMainActivity.this,
					            true));
		            }
	            }, 1000);
            }
        });

        mButtonOn2.setOnLongClickListener(new OnLongClickListener() {

            private EditText editOn, editOff, editOn1, editOff1;

            @Override
            public boolean onLongClick(View v) {
                AlertDialogTwoButtonsCreator.OnInitLayoutListener initLayoutListener = new
                        AlertDialogTwoButtonsCreator.OnInitLayoutListener() {

                            @Override
                            public void onInitLayout(View contentView) {
                                editOn = (EditText) contentView.findViewById(R.id.editOn);
                                editOff = (EditText) contentView.findViewById(R.id.editOff);
                                editOn1 = (EditText) contentView.findViewById(R.id.editOn1);
                                editOff1 = (EditText) contentView.findViewById(R.id.editOff1);

                                String str_on_name = mPrefs.getString(PrefConstants.ON_NAME2,
                                        PrefConstants.ON_NAME_DEFAULT);
                                String str_off_name = mPrefs.getString(PrefConstants.OFF_NAME2,
                                        PrefConstants.OFF_NAME_DEFAULT);


                                String str_on = mPrefs.getString(PrefConstants.ON2, "");
                                String str_off = mPrefs.getString(PrefConstants.OFF2, "");

                                editOn.setText(str_on);
                                editOff.setText(str_off);
                                editOn1.setText(str_on_name);
                                editOff1.setText(str_off_name);
                            }
                        };

                DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener
                        () {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService
                                (Context.INPUT_METHOD_SERVICE);

                        inputManager.hideSoftInputFromWindow(((AlertDialog) dialog)
                                .getCurrentFocus().getWindowToken(), 0);

                        String strOn = editOn.getText().toString();
                        String strOff = editOff.getText().toString();
                        String strOn1 = editOn1.getText().toString();
                        String strOff1 = editOff1.getText().toString();

                        if (strOn.equals("") || strOff.equals("") || strOn1.equals("") ||
                                strOff1.equals("")) {
                            Toast.makeText(EToCMainActivity.this, "Please enter all values", Toast
                                    .LENGTH_LONG).show();
                            return;
                        }

                        Editor edit = mPrefs.edit();
                        edit.putString(PrefConstants.ON2, strOn);
                        edit.putString(PrefConstants.OFF2, strOff);
                        edit.putString(PrefConstants.ON_NAME2, strOn1);
                        edit.putString(PrefConstants.OFF_NAME2, strOff1);
                        edit.apply();

                        String s = mButtonOn2.getTag().toString();
                        if (s.equals(PrefConstants.ON_NAME_DEFAULT.toLowerCase())) {
                            mButtonOn2.setText(strOn1);
                        } else {
                            mButtonOn2.setText(strOff1);
                        }

                        dialog.cancel();
                    }
                };

                DialogInterface.OnClickListener cancelListener = new DialogInterface
                        .OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService
                                (Context.INPUT_METHOD_SERVICE);

                        inputManager.hideSoftInputFromWindow(((AlertDialog) dialog)
                                .getCurrentFocus().getWindowToken(), 0);
                        dialog.cancel();
                    }
                };

                AlertDialogTwoButtonsCreator.createTwoButtonsAlert(EToCMainActivity.this, R.layout
                                .layout_dialog_on_off, "Set On/Off commands", okListener,
                        cancelListener,
                        initLayoutListener).create().show();

                return true;
            }
        });

        mButtonOn3 = (Button) findViewById(R.id.buttonPpm);
        //final String str_off_name1 = mPrefs.getString("off_name1", "");
        mButtonOn3.setText(mPrefs.getString(PrefConstants.ON_NAME3, PrefConstants
                .ON_NAME_DEFAULT));
        mButtonOn3.setTag(PrefConstants.ON_NAME_DEFAULT.toLowerCase());
        mButtonOn3.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                String str_on_name3 = mPrefs.getString(PrefConstants.ON_NAME3, PrefConstants
                        .ON_NAME_DEFAULT);
                String str_off_name3 = mPrefs.getString(PrefConstants.OFF_NAME3, PrefConstants
                        .OFF_NAME_DEFAULT);

                String s = mButtonOn3.getTag().toString();
                if (s.equals(PrefConstants.ON_NAME_DEFAULT.toLowerCase())) {
                    mButtonOn3.setText(str_off_name3);
                    mButtonOn3.setTag(PrefConstants.OFF_NAME_DEFAULT.toLowerCase());
                } else {
                    mButtonOn3.setText(str_on_name3);
                    mButtonOn3.setTag(PrefConstants.ON_NAME_DEFAULT.toLowerCase());
                }
            }
        });

        mButtonOn3.setOnLongClickListener(new OnLongClickListener() {

            private EditText editOn, editOff, editOn1, editOff1;

            @Override
            public boolean onLongClick(View v) {
                AlertDialogTwoButtonsCreator.OnInitLayoutListener initLayoutListener = new
                        AlertDialogTwoButtonsCreator.OnInitLayoutListener() {

                            @Override
                            public void onInitLayout(View contentView) {
                                editOn = (EditText) contentView.findViewById(R.id.editOn);
                                editOff = (EditText) contentView.findViewById(R.id.editOff);
                                editOn1 = (EditText) contentView.findViewById(R.id.editOn1);
                                editOff1 = (EditText) contentView.findViewById(R.id.editOff1);

                                String str_on_name = mPrefs.getString(PrefConstants.ON_NAME3,
                                        PrefConstants.ON_NAME_DEFAULT);
                                String str_off_name = mPrefs.getString(PrefConstants.OFF_NAME3,
                                        PrefConstants.OFF_NAME_DEFAULT);


                                String str_on = mPrefs.getString(PrefConstants.ON3, "");
                                String str_off = mPrefs.getString(PrefConstants.OFF3, "");

                                editOn.setText(str_on);
                                editOff.setText(str_off);
                                editOn1.setText(str_on_name);
                                editOff1.setText(str_off_name);
                            }
                        };

                DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener
                        () {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService
                                (Context.INPUT_METHOD_SERVICE);

                        inputManager.hideSoftInputFromWindow(((AlertDialog) dialog)
                                .getCurrentFocus().getWindowToken(), 0);

                        String strOn = editOn.getText().toString();
                        String strOff = editOff.getText().toString();
                        String strOn1 = editOn1.getText().toString();
                        String strOff1 = editOff1.getText().toString();

                        if (strOn.equals("") || strOff.equals("") || strOn1.equals("") ||
                                strOff1.equals("")) {
                            Toast.makeText(EToCMainActivity.this, "Please enter all values", Toast
                                    .LENGTH_LONG).show();
                            return;
                        }

                        Editor edit = mPrefs.edit();
                        edit.putString(PrefConstants.ON3, strOn);
                        edit.putString(PrefConstants.OFF3, strOff);
                        edit.putString(PrefConstants.ON_NAME3, strOn1);
                        edit.putString(PrefConstants.OFF_NAME3, strOff1);
                        edit.apply();

                        String s = mButtonOn3.getTag().toString();
                        if (s.equals(PrefConstants.ON_NAME_DEFAULT.toLowerCase())) {
                            mButtonOn3.setText(strOn1);
                        } else {
                            mButtonOn3.setText(strOff1);
                        }

                        dialog.cancel();
                    }
                };

                DialogInterface.OnClickListener cancelListener = new DialogInterface
                        .OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService
                                (Context.INPUT_METHOD_SERVICE);

                        inputManager.hideSoftInputFromWindow(((AlertDialog) dialog)
                                .getCurrentFocus().getWindowToken(), 0);
                        dialog.cancel();
                    }
                };

                AlertDialogTwoButtonsCreator.createTwoButtonsAlert(EToCMainActivity.this, R.layout
                                .layout_dialog_on_off, "Set On/Off commands", okListener,
                        cancelListener,
                        initLayoutListener).create().show();

                return true;
            }
        });

        mSendButton = (Button) findViewById(R.id.buttonSend);
        mSendButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                //				if (mIsTimerRunning) {
                //					Toast.makeText(EToCMainActivity.this,
                //							"Timer is running. Please wait", Toast.LENGTH_SHORT)
                //							.show();
                //				} else {
                //					sendMessage();
                //				}

                EToCApplication.getInstance().setPullState(PullState.NONE);
                startService(PullStateManagingService.intentForService(EToCMainActivity.this,
		                false));
                sendMessage();
				mHandler.postDelayed(new Runnable() {

					@Override
					public void run() {
						startService(PullStateManagingService.intentForService(EToCMainActivity.this,
								true));
					}
				}, 1000);
            }
        });

        mAdvancedEditText.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    // sendMessage();
                    handled = true;
                }
                return handled;
            }

        });

        mButtonClear = (Button) findViewById(R.id.buttonClear);
        mButtonMeasure = (Button) findViewById(R.id.buttonMeasure);

        mButtonClear.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mIsTimerRunning) {
                    Toast.makeText(EToCMainActivity.this, "Timer is running. Please wait", Toast
                            .LENGTH_SHORT).show();
                    return;
                }

                CharSequence[] items = new CharSequence[]{"New Measure", "Tx",
                        "LM", "Chart 1", "Chart 2", "Chart" + " 3"};
                boolean[] checkedItems = new boolean[]{false, false, false, false, false, false};
                final SparseBooleanArray mItemsChecked = new SparseBooleanArray(checkedItems
                        .length);
                for (int i = 0; i < checkedItems.length; i++) {
                    mItemsChecked.put(i, checkedItems[i]);
                }

                AlertDialog.Builder alert = new AlertDialog.Builder(EToCMainActivity.this);
                alert.setTitle("Select items");
                alert.setMultiChoiceItems(items, checkedItems, new DialogInterface
                        .OnMultiChoiceClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        mItemsChecked.put(which, isChecked);
                    }
                });
                alert.setPositiveButton("Clear", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mItemsChecked.get(1)) {
                            mAdvancedEditText.setText("");
                        }

                        if (mItemsChecked.get(2)) {
                            mTxtOutput.setText("");
                        }

                        boolean isCleared = false;
                        if (mItemsChecked.get(3)) {
                            // mTxtOutput.setText("");
                            if (mGraphSeriesDataset != null) {
                                mGraphSeriesDataset.getSeriesAt(0).clear();
                                //mChartView.repaint();
                                isCleared = true;
                            }

                            if (mMapChartIndexToDate.containsKey(1)) {
                                Utils.deleteFiles(mMapChartIndexToDate.get(1), "_R1");
                            }
                        }

                        if (mItemsChecked.get(4)) {
                            // mTxtOutput.setText("");
                            if (mGraphSeriesDataset != null) {
                                mGraphSeriesDataset.getSeriesAt(1).clear();
                                //mChartView.repaint();
                                isCleared = true;
                            }

                            if (mMapChartIndexToDate.containsKey(2)) {
                                Utils.deleteFiles(mMapChartIndexToDate.get(2), "_R2");
                            }
                        }

                        if (mItemsChecked.get(5)) {
                            // mTxtOutput.setText("");
                            if (mGraphSeriesDataset != null) {
                                mGraphSeriesDataset.getSeriesAt(2).clear();
                                //mChartView.repaint();
                                isCleared = true;
                            }

                            if (mMapChartIndexToDate.containsKey(3)) {
                                Utils.deleteFiles(mMapChartIndexToDate.get(3), "_R3");
                            }
                        }

                        if (isCleared) {
                            boolean existInitedGraphCurve = false;
                            for (int i = 0; i < mGraphSeriesDataset.getSeriesCount(); i++) {
                                if (mGraphSeriesDataset.getSeriesAt(i).getItemCount() != 0) {
                                    existInitedGraphCurve = true;
                                    break;
                                }
                            }
                            if (!existInitedGraphCurve) {
                                GraphPopulatorUtils.clearYTextLabels(mRenderer);
                            }
                            mChartView.repaint();
                            //mRenderer.setLabelsTextSize(12);
                        }

                        if (mItemsChecked.get(0)) {
                            clearData();
                        }

                        dialog.cancel();
                    }
                });
                alert.setNegativeButton("Close", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                alert.create().show();
            }
        });

        mButtonMeasure.setOnClickListener(new OnClickListener() {

            EditText editDelay, editDuration, editKnownPpm, editVolume, editUserComment,
                    commandsEditText1, commandsEditText2, commandsEditText3;

            CheckBox chkAutoManual, chkKnownPpm;

            LinearLayout llkppm, ll_user_comment;

            RadioGroup mRadioGroup;

            RadioButton mRadio1, mRadio2, mRadio3;

            @Override
            public void onClick(View v) {
                /*if (mAdvancedEditText.getText().toString().isEmpty()) {
                    Toast.makeText(EToCMainActivity.this, "Please enter command", Toast
                            .LENGTH_SHORT).show();
                    return;
                }*/

                EToCApplication.getInstance().setPullState(PullState.NONE);
                startService(PullStateManagingService.intentForService(EToCMainActivity.this,
                        false));

                if (mIsTimerRunning) {
                    Toast.makeText(EToCMainActivity.this, "Timer is running. Please wait", Toast
                            .LENGTH_SHORT).show();
                    return;
                }

                if (mRenderer != null) {
                    XYSeries[] arrSeries = mGraphSeriesDataset.getSeries();

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

                    if ((!isChart1Clear) && (!isChart2Clear) && (!isChart3Clear)) {
                        Toast.makeText(EToCMainActivity.this, "No chart available. Please clear " +
                                "one of " + "the charts", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                AlertDialogTwoButtonsCreator.OnInitLayoutListener initLayoutListener = new
                        AlertDialogTwoButtonsCreator.OnInitLayoutListener() {

                            @Override
                            public void onInitLayout(View contentView) {
                                editDelay = (EditText) contentView.findViewById(R.id.editDelay);
                                editDuration = (EditText) contentView.findViewById(R.id
                                        .editDuration);
                                editKnownPpm = (EditText) contentView.findViewById(R.id
                                        .editKnownPpm);
                                editVolume = (EditText) contentView.findViewById(R.id.editVolume);
                                editUserComment = (EditText) contentView.findViewById(R.id
                                        .editUserComment);

                                //chkAutoManual
                                chkAutoManual = (CheckBox) contentView.findViewById(R.id
                                        .chkAutoManual);
                                chkKnownPpm = (CheckBox) contentView.findViewById(R.id.chkKnownPpm);
                                llkppm = (LinearLayout) contentView.findViewById(R.id.llkppm);
                                ll_user_comment = (LinearLayout) contentView.findViewById(R.id
                                        .ll_user_comment);

                                commandsEditText1 = (EditText) contentView.findViewById(R.id
                                        .commandsEditText1);

                                commandsEditText2 = (EditText) contentView.findViewById(R.id
                                        .commandsEditText2);

                                commandsEditText3 = (EditText) contentView.findViewById(R.id
                                        .commandsEditText3);

                                mRadio1 = (RadioButton) contentView.findViewById(R.id.radio1);
                                mRadio2 = (RadioButton) contentView.findViewById(R.id.radio2);
                                mRadio3 = (RadioButton) contentView.findViewById(R.id.radio3);

                                mRadioGroup = (RadioGroup) contentView.findViewById(R.id
                                        .radio_group);

                                int delay_v = mPrefs.getInt(PrefConstants.DELAY, PrefConstants
                                        .DELAY_DEFAULT);
                                int duration_v = mPrefs.getInt(PrefConstants.DURATION,
                                        PrefConstants.DURATION_DEFAULT);
                                int volume = mPrefs.getInt(PrefConstants.VOLUME, PrefConstants
                                        .VOLUME_DEFAULT);
                                int kppm = mPrefs.getInt(PrefConstants.KPPM, -1);
                                String user_comment = mPrefs.getString(PrefConstants
                                        .USER_COMMENT, "");

                                editDelay.setText("" + delay_v);
                                editDuration.setText("" + duration_v);
                                editVolume.setText("" + volume);
                                editUserComment.setText(user_comment);

                                commandsEditText1.setText(mPrefs.getString(PrefConstants
                                        .MEASURE_FILE_NAME1, PrefConstants
                                        .MEASURE_FILE_NAME1_DEFAULT));
                                commandsEditText2.setText(mPrefs.getString(PrefConstants
                                        .MEASURE_FILE_NAME2, PrefConstants
                                        .MEASURE_FILE_NAME2_DEFAULT));
                                commandsEditText3.setText(mPrefs.getString(PrefConstants
                                        .MEASURE_FILE_NAME3, PrefConstants
                                        .MEASURE_FILE_NAME3_DEFAULT));

                                if (kppm != -1) {
                                    editKnownPpm.setText("" + kppm);
                                }

                                boolean isauto = mPrefs.getBoolean(PrefConstants.IS_AUTO, false);
                                if (isauto) {
                                    chkAutoManual.setChecked(true);
                                } else {
                                    chkAutoManual.setChecked(false);
                                }

                                chkKnownPpm.setOnCheckedChangeListener(new OnCheckedChangeListener() {

                                    @Override
                                    public void onCheckedChanged(CompoundButton buttonView, boolean
                                            isChecked) {
                                        if (isChecked) {
                                            editKnownPpm.setEnabled(true);
                                            llkppm.setVisibility(View.VISIBLE);
                                        } else {
                                            editKnownPpm.setEnabled(false);
                                            llkppm.setVisibility(View.GONE);
                                        }
                                    }
                                });
                            }
                        };

                DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener
                        () {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService
                                (Context.INPUT_METHOD_SERVICE);

                        inputManager.hideSoftInputFromWindow(((AlertDialog) dialog)
                                .getCurrentFocus().getWindowToken(), 0);

                        String strDelay = editDelay.getText().toString();
                        String strDuration = editDuration.getText().toString();

                        if (strDelay.equals("") || strDuration.equals("")) {
                            Toast.makeText(EToCMainActivity.this, "Please enter all values", Toast
                                    .LENGTH_LONG).show();
                            return;
                        }

                        if (chkKnownPpm.isChecked()) {
                            String strkPPM = editKnownPpm.getText().toString();
                            if (strkPPM.equals("")) {
                                Toast.makeText(EToCMainActivity.this, "Please enter ppm " +
                                        "values", Toast.LENGTH_LONG).show();
                                return;
                            } else {
                                int kppm = Integer.parseInt(strkPPM);
                                Editor edit = mPrefs.edit();
                                edit.putInt(PrefConstants.KPPM, kppm);
                                edit.apply();
                            }
                        } else {
                            Editor edit = mPrefs.edit();
                            edit.remove(PrefConstants.KPPM);
                            edit.apply();
                        }

                        //else

                        {
                            String str_uc = editUserComment.getText().toString();
                            if (str_uc.equals("")) {
                                Toast.makeText(EToCMainActivity.this, "Please enter comments",
                                        Toast.LENGTH_LONG).show();
                                return;
                            } else {
                                Editor edit = mPrefs.edit();
                                edit.putString(PrefConstants.USER_COMMENT, str_uc);
                                edit.apply();
                            }
                        }

                        String strVolume = editVolume.getText().toString();
                        if (strVolume.equals("")) {
                            Toast.makeText(EToCMainActivity.this, "Please enter volume values",
                                    Toast.LENGTH_LONG).show();
                            return;
                        } else {
                            int volume = Integer.parseInt(strVolume);
                            Editor edit = mPrefs.edit();
                            edit.putInt(PrefConstants.VOLUME, volume);
                            edit.apply();
                        }

                        boolean b = chkAutoManual.isChecked();
                        Editor edit = mPrefs.edit();
                        edit.putBoolean(PrefConstants.IS_AUTO, b);
                        edit.putBoolean(PrefConstants.SAVE_AS_CALIBRATION, chkKnownPpm.isChecked());
                        edit.apply();

                        int delay = Integer.parseInt(strDelay);
                        int duration = Integer.parseInt(strDuration);

                        if ((delay == 0) || (duration == 0)) {
                            Toast.makeText(EToCMainActivity.this, "zero is not allowed", Toast
                                    .LENGTH_LONG).show();
                            return;
                        } else {

                            // resest so user can set new delay or
                            // duration
                            // if(countMeasures == 4){
                            // oldCountMeasures=0;
                            // countMeasures=0;
                            // }

                            if (countMeasures == 0) {
                                GraphData graphData = GraphPopulatorUtils.createXYChart(duration,
                                        delay, EToCMainActivity.this);
                                mRenderer = graphData.renderer;
                                mGraphSeriesDataset = graphData.seriesDataset;
                                mCurrentSeries = graphData.xySeries;
                                Intent intent = graphData.intent;

                                mChartView = GraphPopulatorUtils.attachXYChartIntoLayout
                                        (EToCMainActivity
                                                .this, (AbstractChart) intent.getExtras().get
                                                ("chart"));
                            }

                            countMeasures++;

                            edit = mPrefs.edit();
                            edit.putInt(PrefConstants.DELAY, delay);
                            edit.putInt(PrefConstants.DURATION, duration);
                            edit.apply();

                            long future = duration * 60 * 1000;
                            long delay_timer = delay * 1000;

                            if (mGraphSeriesDataset.getSeriesAt(0).getItemCount() == 0) {
                                mChartIndex = 1;
                                mReadingCount = 0;
                                mCurrentSeries = mGraphSeriesDataset.getSeriesAt(0);
                            } else if (mGraphSeriesDataset.getSeriesAt(1).getItemCount() == 0) {
                                mChartIndex = 2;
                                mReadingCount = (duration * 60) / delay;
                                mCurrentSeries = mGraphSeriesDataset.getSeriesAt(1);
                            } else if (mGraphSeriesDataset.getSeriesAt(2).getItemCount() == 0) {
                                mChartIndex = 3;
                                mReadingCount = duration * 60;
                                mCurrentSeries = mGraphSeriesDataset.getSeriesAt(2);
                            }

                            int checkedId = mRadioGroup.getCheckedRadioButtonId();

                            if (mAdvancedEditText.getText().toString().equals("") && checkedId ==
                                    -1) {
                                Toast.makeText(EToCMainActivity.this, "Please enter command", Toast
                                        .LENGTH_SHORT).show();
                                return;
                            }

                            final String contentForUpload;
                            final boolean success;

                            if (checkedId != -1) {
                                if (mRadio1.getId() == checkedId) {
                                    contentForUpload = TextFileUtils.readTextFile(new File(new File
                                            (Environment.getExternalStorageDirectory(), AppData
                                                    .SYSTEM_SETTINGS_FOLDER_NAME), commandsEditText1
                                            .getText().toString()));
                                    success = true;
                                } else if (mRadio2.getId() == checkedId) {
                                    contentForUpload = TextFileUtils.readTextFile(new File(new File
                                            (Environment.getExternalStorageDirectory(), AppData
                                                    .SYSTEM_SETTINGS_FOLDER_NAME), commandsEditText2
                                            .getText().toString()));
                                    success = true;
                                } else if (mRadio3.getId() == checkedId) {
                                    contentForUpload = TextFileUtils.readTextFile(new File(new File
                                            (Environment.getExternalStorageDirectory(), AppData
                                                    .SYSTEM_SETTINGS_FOLDER_NAME), commandsEditText3
                                            .getText().toString()));
                                    success = true;
                                } else {
                                    contentForUpload = null;
                                    success = false;
                                }
                            } else {
                                contentForUpload = mAdvancedEditText.getText().toString();
                                success = true;
                            }

                            Editor editor = getPrefs().edit();
                            editor.putString(PrefConstants.MEASURE_FILE_NAME1, commandsEditText1
                                    .getText().toString());
                            editor.putString(PrefConstants.MEASURE_FILE_NAME2, commandsEditText2
                                    .getText().toString());
                            editor.putString(PrefConstants.MEASURE_FILE_NAME3, commandsEditText3
                                    .getText().toString());
                            editor.apply();

                            // collect commands
                            if (contentForUpload != null && !contentForUpload.isEmpty()) {
                                String multiLines = contentForUpload;
                                String[] commands;
                                String delimiter = "\n";

                                commands = multiLines.split(delimiter);

                                List<String> simpleCommands = new ArrayList<>();
                                List<String> loopCommands = new ArrayList<>();
                                boolean isLoop = false;
                                int loopcmd1Idx = -1, loopcmd2Idx = -1;

                                boolean autoPpm = false;

                                for (int i = 0; i < commands.length; i++) {
                                    String command = commands[i];
                                    //Log.d("command", command);
                                    if ((command != null) && (!command.equals("")) &&
                                            (!command.equals("\n"))) {
                                        if (command.contains("loop")) {
                                            isLoop = true;
                                            String lineNos = command.replace("loop", "");
                                            lineNos = lineNos.replace("\n", "");
                                            lineNos = lineNos.replace("\r", "");
                                            lineNos = lineNos.trim();

                                            String line1 = lineNos.substring(0, (lineNos.length()
                                                    / 2));
                                            String line2 = lineNos.substring(lineNos.length() / 2,
                                                    lineNos.length());

                                            loopcmd1Idx = Integer.parseInt(line1) - 1;
                                            loopcmd2Idx = Integer.parseInt(line2) - 1;
                                        } else if (command.equals("autoppm")) {
                                            autoPpm = true;
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

                                if (mSendDataToUsbTask != null && mSendDataToUsbTask.getStatus()
                                        == AsyncTask.Status.RUNNING) {
                                    mSendDataToUsbTask.cancel(true);
                                }
                                mSendDataToUsbTask = new SendDataToUsbTask(simpleCommands,
                                        loopCommands, autoPpm, EToCMainActivity.this);

                                mSendDataToUsbTask.execute(future, delay_timer);
                            } else if (success) {
                                Toast.makeText(EToCMainActivity.this, "File not found", Toast
                                        .LENGTH_LONG).show();
                                return;
                            } else {
                                Toast.makeText(EToCMainActivity.this, "Unexpected error", Toast
                                        .LENGTH_LONG).show();
                                return;
                            }

                            // end collect commands


                            //									ctimer = new
                            // CountDownTimer(future,
                            //											delay_timer) {
                            //
                            //										@Override
                            //										public void onTick(
                            //												long
                            // millisUntilFinished) {
                            //											// TODO
                            // Auto-generated method stub
                            //											mReadingCount =
                            // mReadingCount + 1;
                            //											sendMessage();
                            //
                            ////											byte [] arr =
                            // new byte[]{(byte) 0xFE,0x44,0x11,
                            // 0x22,0x33,0x44,0x55};
                            ////											Message msg =
                            // new Message();
                            ////											msg.what = 0;
                            ////											msg.obj = arr;
                            ////											EToCMainActivity
                            // .sHandler.sendMessage(msg);
                            //										}
                            //
                            //										@Override
                            //										public void onFinish
                            // () {
                            //											// TODO
                            // Auto-generated method stub
                            //											mReadingCount =
                            // mReadingCount + 1;
                            //											sendMessage();
                            //
                            ////											byte [] arr =
                            // new byte[]{(byte) 0xFE,0x44,0x11,
                            // 0x22,0x33,0x44,0x55};
                            ////											Message msg =
                            // new Message();
                            ////											msg.what = 0;
                            ////											msg.obj = arr;
                            ////											EToCMainActivity
                            // .sHandler.sendMessage(msg);
                            //
                            //											mIsTimerRunning =
                            // false;
                            //											Toast.makeText
                            // (EToCMainActivity.this,
                            //													"Timer
                            // Finish",
                            //													Toast
                            // .LENGTH_LONG).show();
                            //										}
                            //									};
                            //
                            //									Toast.makeText(EToCMainActivity
                            // .this,
                            //											"Timer Started",
                            // Toast.LENGTH_LONG)
                            //											.show();
                            //									mIsTimerRunning = true;
                            //									ctimer.start();

                        }
                        dialog.cancel();
                    }
                };

                DialogInterface.OnClickListener cancelListener = new DialogInterface
                        .OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager inputManager = (InputMethodManager) getSystemService
                                (Context.INPUT_METHOD_SERVICE);

                        inputManager.hideSoftInputFromWindow(((AlertDialog) dialog)
                                .getCurrentFocus().getWindowToken(), 0);
                        dialog.cancel();

                        EToCApplication.getInstance().setPullState(PullState.TEMPERATURE);
                        startService(PullStateManagingService.intentForService(EToCMainActivity
                                .this, true));
                    }
                };

                AlertDialogTwoButtonsCreator.createTwoButtonsAlert(EToCMainActivity.this, R
                                .layout.layout_dialog_measure, "Start Measure", okListener,
                        cancelListener, initLayoutListener).create().show();
            }
        });

        setFilters();

        startService(PullStateManagingService.intentForService(this, true));

        mServiceConnection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                UsbService.UsbBinder binder = (UsbService.UsbBinder) service;
                mUsbServiceWritable = binder.getApi();
                mUsbServiceWritable.searchForUsbDevice();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (mUsbServiceWritable != null) {
                    mUsbServiceWritable.disconnect();
                    mUsbServiceWritable = null;
                }
            }
        };

        mServiceBounded = true;
        bindService(new Intent(EToCMainActivity.this, UsbService.class),
                mServiceConnection, BIND_AUTO_CREATE);

        int delay_v = mPrefs.getInt(PrefConstants.DELAY, PrefConstants.DELAY_DEFAULT);
        int duration_v = mPrefs.getInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT);
        SharedPreferences preferences = getPrefs();

        if (!preferences.contains(PrefConstants.DELAY)) {
            SharedPreferences.Editor editor = getPrefs().edit();
            editor.putInt(PrefConstants.DELAY, PrefConstants.DELAY_DEFAULT);
            editor.putInt(PrefConstants.DURATION, PrefConstants.DURATION_DEFAULT);
            editor.putInt(PrefConstants.VOLUME, PrefConstants.VOLUME_DEFAULT);
            editor.apply();
        }

        GraphData graphData = GraphPopulatorUtils.createXYChart(duration_v,
                delay_v, EToCMainActivity.this);
        mRenderer = graphData.renderer;
        mGraphSeriesDataset = graphData.seriesDataset;
        mCurrentSeries = graphData.xySeries;
        Intent intent = graphData.intent;

        mChartView = GraphPopulatorUtils.attachXYChartIntoLayout(EToCMainActivity
                .this, (AbstractChart) intent.getExtras().get("chart"));

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayUseLogoEnabled(true);
        actionBar.setLogo(R.drawable.ic_launcher);

        TextView titleView = (TextView) actionBar.getCustomView().findViewById(R.id.title);
        titleView.setTextColor(Color.WHITE);
        ((RelativeLayout.LayoutParams) titleView.getLayoutParams()).addRule(RelativeLayout
                .CENTER_HORIZONTAL, 0);

        //Intent timeIntent = GraphPopulatorUtils.createTimeChart(this);
        //GraphPopulatorUtils.attachTimeChartIntoLayout(this, (AbstractChart)timeIntent.getExtras
        //		 ().get("chart"));
    }

    private void loadPreferencesFromLocalData() {
        File settingsFolder = new File(Environment.getExternalStorageDirectory(), AppData
                .SYSTEM_SETTINGS_FOLDER_NAME);
        if (!settingsFolder.exists()) {
            return;
        }
        File button1DataFile = new File(settingsFolder, AppData.BUTTON1_DATA);
        if (button1DataFile.exists()) {
            String button1Data = TextFileUtils.readTextFile(button1DataFile);
            if (!button1Data.isEmpty()) {
                String values[] = button1Data.split(AppData.SPLIT_STRING);
                if (values.length == 4) {
                    SharedPreferences.Editor editor = getPrefs().edit();
                    editor.putString(PrefConstants.ON_NAME1, values[0]);
                    editor.putString(PrefConstants.OFF_NAME1, values[1]);
                    editor.putString(PrefConstants.ON1, values[2]);
                    editor.putString(PrefConstants.OFF1, values[3]);
                    editor.apply();
                }
            }
        }

        File button2DataFile = new File(settingsFolder, AppData.BUTTON2_DATA);
        if (button2DataFile.exists()) {
            String button2Data = TextFileUtils.readTextFile(button2DataFile);
            if (!button2Data.isEmpty()) {
                String values[] = button2Data.split(AppData.SPLIT_STRING);
                if (values.length == 4) {
                    SharedPreferences.Editor editor = getPrefs().edit();
                    editor.putString(PrefConstants.ON_NAME2, values[0]);
                    editor.putString(PrefConstants.OFF_NAME2, values[1]);
                    editor.putString(PrefConstants.ON2, values[2]);
                    editor.putString(PrefConstants.OFF2, values[3]);
                    editor.apply();
                }
            }
        }

        File button3DataFile = new File(settingsFolder, AppData.BUTTON3_DATA);
        if (button3DataFile.exists()) {
            String button3Data = TextFileUtils.readTextFile(button3DataFile);
            if (!button3Data.isEmpty()) {
                String values[] = button3Data.split(AppData.SPLIT_STRING);
                if (values.length == 4) {
                    SharedPreferences.Editor editor = getPrefs().edit();
                    editor.putString(PrefConstants.ON_NAME3, values[0]);
                    editor.putString(PrefConstants.OFF_NAME3, values[1]);
                    editor.putString(PrefConstants.ON3, values[2]);
                    editor.putString(PrefConstants.OFF3, values[3]);
                    editor.apply();
                }
            }
        }

        File buttonPowerDataFile = new File(settingsFolder, AppData.POWER_DATA);
        if (buttonPowerDataFile.exists()) {
            String powerData = TextFileUtils.readTextFile(buttonPowerDataFile);
            if (!powerData.isEmpty()) {
                String values[] = powerData.split(AppData.SPLIT_STRING);
                if (values.length == 4) {
                    SharedPreferences.Editor editor = getPrefs().edit();
                    editor.putString(PrefConstants.POWER_ON_NAME, values[0]);
                    editor.putString(PrefConstants.POWER_OFF_NAME, values[1]);
                    editor.putString(PrefConstants.POWER_ON, values[2]);
                    editor.putString(PrefConstants.POWER_OFF, values[3]);
                    editor.apply();
                }
            }
        }

        File temperatureShiftFolder = new File(settingsFolder, AppData.TEMPERATURE_SHIFT_FILE);
        if (temperatureShiftFolder.exists()) {
            String temperatureData = TextFileUtils.readTextFile(temperatureShiftFolder);
            if (!temperatureData.isEmpty()) {
                try {
                    mTemperatureShift = Integer.parseInt(temperatureData);
                } catch (NumberFormatException e) {
                    mTemperatureShift = 0;
                }
            }
        } else {
            mTemperatureShift = 0;
        }

        File measureDefaultFilesFile = new File(settingsFolder, AppData.MEASURE_DEFAULT_FILES);
        if (measureDefaultFilesFile.exists()) {
            String measureFilesData = TextFileUtils.readTextFile(measureDefaultFilesFile);
            if (!measureFilesData.isEmpty()) {
                String values[] = measureFilesData.split(AppData.SPLIT_STRING);
                if (values.length == 3) {
                    SharedPreferences.Editor editor = getPrefs().edit();
                    editor.putString(PrefConstants.MEASURE_FILE_NAME1, values[0]);
                    editor.putString(PrefConstants.MEASURE_FILE_NAME2, values[1]);
                    editor.putString(PrefConstants.MEASURE_FILE_NAME3, values[2]);
                    editor.apply();
                }
            }
        }
    }

    private void savePreferencesToLocalData() throws IOException {
        File settingsFolder = new File(Environment.getExternalStorageDirectory(), AppData
                .SYSTEM_SETTINGS_FOLDER_NAME);
        if (!settingsFolder.exists()) {
            settingsFolder.mkdir();
        }

        SharedPreferences preferences = getPrefs();

        File button1DataFile = new File(settingsFolder, AppData.BUTTON1_DATA);
        button1DataFile.createNewFile();

        StringBuilder button1DataBuilder = new StringBuilder();
        button1DataBuilder.append(preferences.getString(PrefConstants.ON_NAME1, PrefConstants
                .ON_NAME_DEFAULT));
        button1DataBuilder.append(AppData.SPLIT_STRING);
        button1DataBuilder.append(preferences.getString(PrefConstants.OFF_NAME1, PrefConstants
                .OFF_NAME_DEFAULT));
        button1DataBuilder.append(AppData.SPLIT_STRING);
        button1DataBuilder.append(preferences.getString(PrefConstants.ON1, ""));
        button1DataBuilder.append(AppData.SPLIT_STRING);
        button1DataBuilder.append(preferences.getString(PrefConstants.OFF1, ""));

        TextFileUtils.writeTextFile(button1DataFile.getAbsolutePath(), button1DataBuilder
                .toString());

        File button2DataFile = new File(settingsFolder, AppData.BUTTON2_DATA);
        button2DataFile.createNewFile();
        StringBuilder button2DataBuilder = new StringBuilder();
        button2DataBuilder.append(preferences.getString(PrefConstants.ON_NAME2, PrefConstants
                .ON_NAME_DEFAULT));
        button2DataBuilder.append(AppData.SPLIT_STRING);
        button2DataBuilder.append(preferences.getString(PrefConstants.OFF_NAME2, PrefConstants
                .OFF_NAME_DEFAULT));
        button2DataBuilder.append(AppData.SPLIT_STRING);
        button2DataBuilder.append(preferences.getString(PrefConstants.ON2, ""));
        button2DataBuilder.append(AppData.SPLIT_STRING);
        button2DataBuilder.append(preferences.getString(PrefConstants.OFF2, ""));

        TextFileUtils.writeTextFile(button2DataFile.getAbsolutePath(), button2DataBuilder
                .toString());

        File button3DataFile = new File(settingsFolder, AppData.BUTTON3_DATA);
        button2DataFile.createNewFile();
        StringBuilder button3DataBuilder = new StringBuilder();
        button3DataBuilder.append(preferences.getString(PrefConstants.ON_NAME3, PrefConstants
                .ON_NAME_DEFAULT));
        button3DataBuilder.append(AppData.SPLIT_STRING);
        button3DataBuilder.append(preferences.getString(PrefConstants.OFF_NAME3, PrefConstants
                .OFF_NAME_DEFAULT));
        button3DataBuilder.append(AppData.SPLIT_STRING);
        button3DataBuilder.append(preferences.getString(PrefConstants.ON3, ""));
        button3DataBuilder.append(AppData.SPLIT_STRING);
        button3DataBuilder.append(preferences.getString(PrefConstants.OFF3, ""));

        TextFileUtils.writeTextFile(button3DataFile.getAbsolutePath(), button3DataBuilder
                .toString());

        File buttonPowerDataFile = new File(settingsFolder, AppData.POWER_DATA);
        buttonPowerDataFile.createNewFile();
        StringBuilder buttonPowerDataBuilder = new StringBuilder();
        buttonPowerDataBuilder.append(preferences.getString(PrefConstants.POWER_ON_NAME,
                PrefConstants
                .POWER_ON_NAME_DEFAULT));
        buttonPowerDataBuilder.append(AppData.SPLIT_STRING);
        buttonPowerDataBuilder.append(preferences.getString(PrefConstants.POWER_OFF_NAME,
                PrefConstants.POWER_OFF_NAME_DEFAULT));
        buttonPowerDataBuilder.append(AppData.SPLIT_STRING);
        buttonPowerDataBuilder.append(preferences.getString(PrefConstants.POWER_ON, PrefConstants
                .POWER_ON_COMMAND_DEFAULT));
        buttonPowerDataBuilder.append(AppData.SPLIT_STRING);
        buttonPowerDataBuilder.append(preferences.getString(PrefConstants.POWER_OFF,
                PrefConstants.POWER_OFF_COMMAND_DEFAULT));

        TextFileUtils.writeTextFile(buttonPowerDataFile.getAbsolutePath(), buttonPowerDataBuilder
                .toString());

        File measureDefaultFilesFile = new File(settingsFolder, AppData.MEASURE_DEFAULT_FILES);
        measureDefaultFilesFile.createNewFile();

        StringBuilder measureDefaultFilesBuilder = new StringBuilder();
        measureDefaultFilesBuilder.append(preferences.getString(PrefConstants
                .MEASURE_FILE_NAME1, PrefConstants.MEASURE_FILE_NAME1_DEFAULT));
        measureDefaultFilesBuilder.append(AppData.SPLIT_STRING);
        measureDefaultFilesBuilder.append(preferences.getString(PrefConstants
                .MEASURE_FILE_NAME2, PrefConstants.MEASURE_FILE_NAME2_DEFAULT));
        measureDefaultFilesBuilder.append(AppData.SPLIT_STRING);
        measureDefaultFilesBuilder.append(preferences.getString(PrefConstants
                .MEASURE_FILE_NAME3, PrefConstants.MEASURE_FILE_NAME3_DEFAULT));

        TextFileUtils.writeTextFile(measureDefaultFilesFile.getAbsolutePath(),
                measureDefaultFilesBuilder.toString());
    }

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

    @Override
    public int getLayoutId() {
        return R.layout.layout_editor_updated;
    }

    @Override
    public Fragment getFirstFragment() {
        return new EmptyFragment();
    }

    @Override
    public int getFolderDrawable() {
        return R.drawable.folder;
    }

    @Override
    public LinearLayout graphContainer() {
        return (LinearLayout) findViewById(R.id.exported_chart_layout);
    }

    @Override
    public int getFileDrawable() {
        return R.drawable.file;
    }

    @Override
    public void onGraphAttached() {
        mMarginLayout.setBackgroundColor(Color.BLACK);
        mExportLayout.setBackgroundColor(Color.WHITE);
    }

    @Override
    public void onGraphDetached() {
        mMarginLayout.setBackgroundColor(Color.TRANSPARENT);
        mExportLayout.setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    public String toolbarTitle() {
        return getString(R.string.app_name_with_version, BuildConfig.VERSION_NAME);
    }

    public static void sendBroadCastWithData(Context context, String data) {
        Intent intent = new Intent(EToCMainHandler.USB_DATA_READY);
        intent.putExtra(EToCMainHandler.DATA_EXTRA, data);
        context.sendBroadcast(intent);
    }

    public void sendCommand(String command) {
        if ((command != null) && (!command.equals("")) && (!command.equals("\n"))) {
            command = command.replace("\r", "");
            command = command.replace("\n", "");
            command = command.trim();

            if (mUsbServiceWritable != null) {
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
                    mUsbServiceWritable.writeToUsb(bytes);
                } else {
                    // ASCII
                    mUsbServiceWritable.writeToUsb(command.getBytes());
                    mUsbServiceWritable.writeToUsb("\r".getBytes());
                }

	            //if(Utils.isPullStateNone()) {
		            Utils.appendText(mTxtOutput, "Tx: " + command);
		            mScrollView.smoothScrollTo(0, 0);
	            //}
            } else {
                Toast.makeText(EToCMainActivity.this, "serial port not found", Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    private void sendMessage() {
        if (!mAdvancedEditText.getText().toString().equals("")) {
            String multiLines = mAdvancedEditText.getText().toString();
            String[] commands;
            String delimiter = "\n";

            commands = multiLines.split(delimiter);

            for (int i = 0; i < commands.length; i++) {
                String command = commands[i];
                Log.d("command", command);
                sendCommand(command);
            }
        } else {
            if (mUsbServiceWritable != null) { // if UsbService was
                // correctly binded,
                // Send data
                mUsbServiceWritable.writeToUsb("\r".getBytes());
            } else {
                Toast.makeText(EToCMainActivity.this, "serial port not found", Toast.LENGTH_LONG)
                        .show();
            }

            // mTxtOutput.append("Tx: " + data + "\n");
            // mScrollView.smoothScrollTo(0, mTxtOutput.getBottom());
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
        //		if (ctimer != null) {
        //			ctimer.cancel();
        //		}

        if (mExecutor != null) {
            mExecutor.shutdown();
            while (!mExecutor.isTerminated()) {
            }
        }

        if (mEToCOpenChartTask != null && mEToCOpenChartTask.getStatus() == AsyncTask.Status
                .RUNNING) {
            mEToCOpenChartTask.cancel(true);
            mEToCOpenChartTask = null;
        }

        if (mSendDataToUsbTask != null && mSendDataToUsbTask.getStatus() == AsyncTask.Status
                .RUNNING) {
            mSendDataToUsbTask.cancel(true);
            mSendDataToUsbTask = null;
        }

        if (mServiceBounded) {
            unbindService(mServiceConnection);
        }

        if (mHandler != null) {
            mHandler.removeCallbacks(null);
        }

        stopService(new Intent(this, PullStateManagingService.class));
    }

    /**
     * @see android.app.Activity#onRestart()
     */
    protected void onRestart() {
        super.onRestart();
        mReadIntent = false;
    }

    /**
     * @see android.app.Activity#onRestoreInstanceState(Bundle)
     */
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d("TED", "onRestoreInstanceState");
        Log.v("TED", mAdvancedEditText.getText().toString());
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
        mAdvancedEditText.updateFromSettings();
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
     * Intent)
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
                    Toast.makeText(EToCMainActivity.this, "Invalid File", Toast.LENGTH_SHORT)
                            .show();
                }

                break;
        }
    }

    /**
     * @see android.app.Activity#onConfigurationChanged(Configuration)
     */
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onConfigurationChanged");
    }

    /**
     * @see android.app.Activity#onCreateOptionsMenu(Menu)
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        return true;
    }

    /**
     * @see android.app.Activity#onPrepareOptionsMenu(Menu)
     */
    @TargetApi(11)
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);


        Log.d("onPrepareOptionsMenu", "onPrepareOptionsMenu");
        menu.clear();
        menu.close();

        // boolean isUsbConnected = checkUsbConnection();
        if (mIsUsbConnected) {
            wrapMenuItem(addMenuItem(menu, MENU_ID_CONNECT_DISCONNECT, R.string.menu_disconnect, R
                    .drawable.usb_connected), true);
        } else {
            wrapMenuItem(addMenuItem(menu, MENU_ID_CONNECT_DISCONNECT, R.string.menu_connect, R
                    .drawable.usb_disconnected), true);

        }

        wrapMenuItem(addMenuItem(menu, MENU_ID_NEW, R.string.menu_new, R.drawable
                .ic_menu_file_new), false);
        wrapMenuItem(addMenuItem(menu, MENU_ID_OPEN, R.string.menu_open, R.drawable
                .ic_menu_file_open), false);

        wrapMenuItem(addMenuItem(menu, MENU_ID_OPEN_CHART, R.string.menu_open_chart, R.drawable
                .ic_menu_file_open), false);

        if (!mReadOnly)
            wrapMenuItem(addMenuItem(menu, MENU_ID_SAVE, R.string.menu_save, R.drawable
                    .ic_menu_save), false);

        // if ((!mReadOnly) && Settings.UNDO)
        // addMenuItem(menu, MENU_ID_UNDO, R.string.menu_undo,
        // R.drawable.ic_menu_undo);

        // addMenuItem(menu, MENU_ID_SEARCH, R.string.menu_search,
        // R.drawable.ic_menu_search);

        if (RecentFiles.getRecentFiles().size() > 0)
            wrapMenuItem(addMenuItem(menu, MENU_ID_OPEN_RECENT, R.string.menu_open_recent, R
                    .drawable.ic_menu_recent), false);

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

        if (mIsUsbConnected) {
            showMenuItemAsAction(menu.findItem(MENU_ID_CONNECT_DISCONNECT), R.drawable
                    .usb_connected, MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem
                    .SHOW_AS_ACTION_WITH_TEXT);

        } else {
            showMenuItemAsAction(menu.findItem(MENU_ID_CONNECT_DISCONNECT), R.drawable
                    .usb_disconnected, MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem
                    .SHOW_AS_ACTION_WITH_TEXT);
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
     * @see android.app.Activity#onOptionsItemSelected(MenuItem)
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        mWarnedShouldQuit = false;
        switch (item.getItemId()) {
            case MENU_ID_CONNECT_DISCONNECT:
                Log.d("isUsbConnected", "" + mIsUsbConnected);
                if (mIsUsbConnected) {
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
                    // startService(new Intent(EToCMainActivity.this, UsbService.class));
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
            case MENU_ID_SETTINGS:
                settingsActivity();
                return true;
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
     * @see TextWatcher#beforeTextChanged(CharSequence,
     * int, int, int)
     */
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (Settings.UNDO && (!mInUndo) && (mWatcher != null))
            mWatcher.beforeChange(s, start, count, after);
    }

    /**
     * @see TextWatcher#onTextChanged(CharSequence, int,
     * int, int)
     */
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mInUndo)
            return;

        if (Settings.UNDO && (!mInUndo) && (mWatcher != null))
            mWatcher.afterChange(s, start, before, count);

    }

    /**
     * @see TextWatcher#afterTextChanged(Editable)
     */
    public void afterTextChanged(Editable s) {
        if (!mDirty) {
            mDirty = true;
            updateTitle();
        }
    }

    /**
     * @see android.app.Activity#onKeyUp(int, KeyEvent)
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
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
        }
        mWarnedShouldQuit = false;
        return super.onKeyUp(keyCode, event);
    }

    private boolean shouldQuit() {
        int entriesCount = getSupportFragmentManager().getBackStackEntryCount();
        return entriesCount == 0 && mExportLayout.getChildCount() == 0;
    }

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
        } else if ((action.equals(Intent.ACTION_VIEW)) || (action.equals(Intent.ACTION_EDIT))) {
            try {
                file = new File(new URI(intent.getData().toString()));
                doOpenFile(file, false);
            } catch (URISyntaxException e) {
                Crouton.showText(this, R.string.toast_intent_invalid_uri, Style.ALERT);
            } catch (IllegalArgumentException e) {
                Crouton.showText(this, R.string.toast_intent_illegal, Style.ALERT);
            }
        } else if (action.equals(ACTION_WIDGET_OPEN)) {
            try {
                file = new File(new URI(intent.getData().toString()));
                doOpenFile(file, intent.getBooleanExtra(EXTRA_FORCE_READ_ONLY, false));
            } catch (URISyntaxException e) {
                Crouton.showText(this, R.string.toast_intent_invalid_uri, Style.ALERT);
            } catch (IllegalArgumentException e) {
                Crouton.showText(this, R.string.toast_intent_illegal, Style.ALERT);
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
                Crouton.showText(this, R.string.toast_open_home_page_error, Style.ALERT);
            } else if (!file.canRead()) {
                Crouton.showText(this, R.string.toast_home_page_cant_read, Style.ALERT);
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
        mAdvancedEditText.setText("");
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
                if (mAdvancedEditText.getText().toString().equals("")) {
                    mAdvancedEditText.append(text);// change by nkl ori settext
                } else {
                    mAdvancedEditText.append("\n" + text);// change by nkl ori settext
                }
                mWatcher = new TextChangeWatcher();
                mCurrentFilePath = getCanonizePath(file);
                mCurrentFileName = file.getName();
                RecentFiles.updateRecentList(mCurrentFilePath);
                RecentFiles.saveRecentList(getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE));
                mDirty = false;
                mInUndo = false;
                mDoNotBackup = false;
                if (file.canWrite() && (!forceReadOnly)) {
                    mReadOnly = false;
                    mAdvancedEditText.setEnabled(true);
                } else {
                    mReadOnly = true;
                    mAdvancedEditText.setEnabled(false);
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
                mAdvancedEditText.setText(text);
                mWatcher = new TextChangeWatcher();
                mCurrentFilePath = null;
                mCurrentFileName = null;
                mDirty = false;
                mInUndo = false;
                mDoNotBackup = false;
                mReadOnly = false;
                mAdvancedEditText.setEnabled(true);

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

        content = mAdvancedEditText.getText().toString();

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
        RecentFiles.saveRecentList(getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE));
        mReadOnly = false;
        mDirty = false;
        updateTitle();
        Crouton.showText(this, R.string.toast_save_success, Style.CONFIRM);
    }

    protected void doAutoSaveFile() {
        if (mDoNotBackup) {
            doClearContents();
        }

        String text = mAdvancedEditText.getText().toString();
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
        caret = mWatcher.undo(mAdvancedEditText.getText());
        if (caret >= 0) {
            mAdvancedEditText.setSelection(caret, caret);
            didUndo = true;
        }
        mInUndo = false;

        return didUndo;
    }

    /**
     * Prompt the user to save the current file before doing something else
     */
    protected void promptSaveDirty() {
        if (!mDirty) {
            executeRunnableAndClean();
            return;
        }

        DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                saveContent();
                mDoNotBackup = true;
            }
        };
        DialogInterface.OnClickListener cancelListener = new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {

            }
        };

        AlertDialog.Builder builder = AlertDialogTwoButtonsCreator.createTwoButtonsAlert(this, 0,
                getString(R.string.app_name), okListener, cancelListener, null);

        builder.setMessage(R.string.ui_save_text);

        builder.setNeutralButton(R.string.ui_no_save, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                executeRunnableAndClean();
                mDoNotBackup = true;
            }
        });

        builder.create().show();
    }

    /**
     *
     */
    protected void newContent() {
        mRunnable = new Runnable() {

            public void run() {
                doClearContents();
            }
        };

        // promptSaveDirty();
        // added by nkl
        executeRunnableAndClean();
    }

    /**
     * Runs the after save to complete
     */
    protected void executeRunnableAndClean() {
        if (mRunnable == null) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "No runnable, ignoring...");
            return;
        }

        mRunnable.run();

        mRunnable = null;
    }

    /**
     * Starts an activity to choose a file to open
     */
    protected void openFile() {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "openFile");

        mRunnable = new Runnable() {

            public void run() {
                Intent open = new Intent();
                open.setClass(getApplicationContext(), TedOpenActivity.class);
                // open = new Intent(ACTION_OPEN);
                open.putExtra(EXTRA_REQUEST_CODE, REQUEST_OPEN);
                try {
                    startActivityForResult(open, REQUEST_OPEN);
                } catch (ActivityNotFoundException e) {
                    Crouton.showText(EToCMainActivity.this, R.string.toast_activity_open, Style
                            .ALERT);
                }
            }
        };

        // change by nkl
        // promptSaveDirty();

        // added by nkl
        executeRunnableAndClean();
    }

    private void openchart1(String filep) {
        String filepath = filep;

        boolean isLogsExist = false;

        if (filepath.contains("R1")) {
            isLogsExist = true;
            mGraphSeriesDataset.getSeriesAt(0).clear();
            mGraphSeries = mGraphSeriesDataset.getSeriesAt(0);
        } else if (filepath.contains("R2")) {
            isLogsExist = true;
            mGraphSeriesDataset.getSeriesAt(1).clear();
            mGraphSeries = mGraphSeriesDataset.getSeriesAt(1);
        } else if (filepath.contains("R3")) {
            isLogsExist = true;
            mGraphSeriesDataset.getSeriesAt(2).clear();
            mGraphSeries = mGraphSeriesDataset.getSeriesAt(2);
        }

        if (!isLogsExist) {
            //			Toast.makeText(EToCMainActivity.this,
            //					"Required Log files not available",
            //					Toast.LENGTH_SHORT).show();
            return;
        }

        if (mEToCOpenChartTask != null && mEToCOpenChartTask.getStatus() == AsyncTask.Status
                .RUNNING) {
            mEToCOpenChartTask.cancel(true);
        }
        mEToCOpenChartTask = new EToCOpenChartTask(this);

        mEToCOpenChartTask.execute(filepath);
    }

    /**
     * render chart from selected file
     */
    private void openChart() {
        if (mIsTimerRunning) {
            Toast.makeText(EToCMainActivity.this, "Timer is running. Please wait", Toast
                    .LENGTH_SHORT).show();
            return;
        }

        File dir = new File(Environment.getExternalStorageDirectory() + "/AEToCLogs_MES");

        if (!dir.exists()) {
            Toast.makeText(EToCMainActivity.this, "Logs diretory is not available", Toast
                    .LENGTH_SHORT).show();
            return;
        }

        String[] filenameArry = dir.list();
        if (filenameArry == null) {
            Toast.makeText(EToCMainActivity.this, "Logs not available. Logs directory is empty",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] items = new CharSequence[filenameArry.length];
        for (int i = 0; i < filenameArry.length; i++) {
            items[i] = filenameArry[i];
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(EToCMainActivity.this);
        alert.setTitle("Select file");
        alert.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String filename = (String) items[which];

                boolean isLogsExist = false;

                if (filename.contains("R1")) {
                    isLogsExist = true;
                    mGraphSeriesDataset.getSeriesAt(0).clear();
                    mGraphSeries = mGraphSeriesDataset.getSeriesAt(0);
                } else if (filename.contains("R2")) {
                    isLogsExist = true;
                    mGraphSeriesDataset.getSeriesAt(1).clear();
                    mGraphSeries = mGraphSeriesDataset.getSeriesAt(1);
                } else if (filename.contains("R3")) {
                    isLogsExist = true;
                    mGraphSeriesDataset.getSeriesAt(2).clear();
                    mGraphSeries = mGraphSeriesDataset.getSeriesAt(2);
                }

                if (!isLogsExist) {
                    Toast.makeText(EToCMainActivity.this, "Required Log files not available",
                            Toast.LENGTH_SHORT).show();
                    dialog.cancel();
                    return;
                }

                File logFile = new File(new File(Environment.getExternalStorageDirectory(),
                        "AEToCLogs_MES"), filename);

                if (mEToCOpenChartTask != null && mEToCOpenChartTask.getStatus() == AsyncTask.Status
                        .RUNNING) {
                    mEToCOpenChartTask.cancel(true);
                }
                mEToCOpenChartTask = new EToCOpenChartTask(EToCMainActivity.this);

                mEToCOpenChartTask.execute(logFile.getAbsolutePath());

                dialog.cancel();
            }
        });

        alert.setNegativeButton("Close", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
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

        mRunnable = new Runnable() {

            public void run() {
                Intent open;

                open = new Intent();
                open.setClass(EToCMainActivity.this, TedOpenRecentActivity.class);
                try {
                    startActivityForResult(open, REQUEST_OPEN);
                } catch (ActivityNotFoundException e) {
                    Crouton.showText(EToCMainActivity.this, R.string.toast_activity_open_recent,
                            Style.ALERT);
                }
            }
        };

        // promptSaveDirty();
        // added by nkl
        executeRunnableAndClean();
    }

    /**
     * Warns the user that the next back press will qui the application, or quit
     * if the warning has already been shown
     */
    protected void warnOrQuit() {
        if (mWarnedShouldQuit) {
            quit();
        } else {
            Crouton.showText(this, R.string.toast_warn_no_undo_will_quit, Style.INFO);
            mWarnedShouldQuit = true;
        }
    }

    /**
     * Quit the app (user pressed back)
     */
    protected void quit() {
        mRunnable = new Runnable() {

            public void run() {
                finish();
            }
        };

        // promptSaveDirty();
        // added by nkl
        executeRunnableAndClean();
    }

    @Override
    public void finish() {
        try {
            Crouton.clearCroutonsForActivity(this);
            savePreferencesToLocalData();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
        super.finish();
    }

    /**
     * General save command : check if a path exist for the current content,
     * then save it , else invoke the {@link EToCMainActivity#saveContentAs()} method
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
     * Opens the settings activity
     */
    protected void settingsActivity() {

        mRunnable = new Runnable() {

            public void run() {
                Intent settings = new Intent();
                settings.setClass(EToCMainActivity.this, TedSettingsActivity.class);
                try {
                    startActivity(settings);
                } catch (ActivityNotFoundException e) {
                    Crouton.showText(EToCMainActivity.this, R.string.toast_activity_settings,
                            Style.ALERT);
                }
            }
        };

        // promptSaveDirty();
        // added by nkl
        executeRunnableAndClean();
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

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbService.ACTION_DATA_RECEIVED);
        filter.addAction(EToCMainHandler.USB_DATA_READY);
        registerReceiver(mUsbReceiver, filter);
    }

    public void sendMessageWithUsbDataReceived(byte bytes[]) {
        Message message = mHandler.obtainMessage();
        message.obj = bytes;
        message.what = EToCMainHandler.MESSAGE_USB_DATA_RECEIVED;
        message.sendToTarget();
    }

    public void sendOpenChartDataToHandler(String value) {
        Message message = mHandler.obtainMessage();
        message.obj = value;
        message.what = EToCMainHandler.MESSAGE_OPEN_CHART;
        message.sendToTarget();
    }

    public void sendMessageWithUsbDataReady(String dataForSend) {
        Message message = mHandler.obtainMessage();
        message.obj = dataForSend;
        message.what = EToCMainHandler.MESSAGE_USB_DATA_READY;
        message.sendToTarget();
    }

    public void disconnectFromService() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mUsbServiceWritable != null) {
                    mUsbServiceWritable.disconnect();
                    mUsbServiceWritable = null;
                }
                unbindService(mServiceConnection);
                mServiceBounded = false;
            }
        });
    }

    public XYMultipleSeriesRenderer getRenderer() {
        return mRenderer;
    }

    public XYSeries getCurrentSeries() {
        return mCurrentSeries;
    }

    public SharedPreferences getPrefs() {
        return mPrefs;
    }

    public int getReadingCount() {
        return mReadingCount;
    }

    public int getCountMeasure() {
        return countMeasures;
    }

    public int getOldCountMeasure() {
        return oldCountMeasures;
    }

    public void execute(Runnable runnable) {
        mExecutor.execute(runnable);
    }

    public boolean isTimerRunning() {
        return mIsTimerRunning;
    }

    public void incCountMeasure() {
        countMeasures++;
    }

    public void repaintChartView() {
        mChartView.repaint();
    }

    public void setCurrentSeries(int index) {
        mCurrentSeries = mGraphSeriesDataset.getSeriesAt(index);
    }

    public void setChartIdx(int chart_idx) {
        this.mChartIndex = chart_idx;
    }

    public int getChartIdx() {
        return mChartIndex;
    }

    public void refreshOldCountMeasure() {
        oldCountMeasures = countMeasures;
    }

    public String getSubDirDate() {
        return mSubDirDate;
    }

    public void setSubDirDate(String sub_dir_date) {
        this.mSubDirDate = sub_dir_date;
    }

    public String getChartDate() {
        return mChartDate;
    }

    public void setChartDate(String chart_date) {
        this.mChartDate = chart_date;
    }

    public Map<Integer, String> getMapChartDate() {
        return mMapChartIndexToDate;
    }

    public XYSeries getChartSeries() {
        return mGraphSeries;
    }

    public GraphicalView getChartView() {
        return mChartView;
    }

    public void setUsbConnected(boolean isUsbConnected) {
        this.mIsUsbConnected = isUsbConnected;
    }

    public ScrollView getScrollView() {
        return mScrollView;
    }

    public TextView getTxtOutput() {
        return mTxtOutput;
    }

    private boolean isClicked = false;

    public void simulateClick() {
       /* String value = "@5," + (isClicked ? "1" : "0") + "(0,0,0,0),1000,234,25,25,25";
        sendMessageWithUsbDataReceived(value.getBytes());
        isClicked = !isClicked;*/
    }

    private void changeBackground(Button button, boolean isPressed) {
        if (isPressed) {
            if (Build.VERSION.SDK_INT >= 16) {
                button.setBackground(getResources().getDrawable(R.drawable
                        .temperature_button_drawable_pressed));
            } else {
                button.setBackgroundDrawable(getResources().getDrawable(R
                        .drawable.temperature_button_drawable_pressed));
            }
        } else {
            if (Build.VERSION.SDK_INT >= 16) {
                button.setBackground(getResources().getDrawable(R.drawable
                        .temperature_button_drawable_unpressed));
            } else {
                button.setBackgroundDrawable(getResources().getDrawable(R
                        .drawable
                        .temperature_button_drawable_unpressed));
            }
        }
    }

    public void refreshTextAccordToSensor(boolean isTemperature, String text) {
        if (isTemperature) {
            mTemperatureData = TemperatureData.parse(text);
            if (mTemperatureData.isCorrect()) {
                Runnable updateRunnable = new Runnable() {

                    @Override
                    public void run() {
                        //changeBackground(mTemperature, mTemperatureData.getHeaterOn() == 1);

                        mTemperature.setText("" + (mTemperatureData.getTemperature1() +
                                mTemperatureShift));
                    }
                };
                //mTemperature.post(updateRunnable);
                updateRunnable.run();
            } else {
                mTemperature.setText("" + mTemperatureData.getWrongPosition());
            }
        } else {
            mCo2.setText(text);
        }
    }

    public void setTimerRunning(boolean isTimerRunning) {
        this.mIsTimerRunning = isTimerRunning;
    }

    public void incReadingCount() {
        mReadingCount++;
    }

    public void refreshCurrentSeries() {
        mCurrentSeries = GraphPopulatorUtils.addNewSet(mRenderer, mGraphSeriesDataset);
    }

    public void invokeAutoCalculations() {
        getSupportFragmentManager().findFragmentById(R.id
                .bottom_fragment).getView().findViewById(R.id.calculate_ppm_auto).performClick();
    }

    public void clearData() {
        setSubDirDate(null);
        for (XYSeries series : mGraphSeriesDataset.getSeries()) {
            series.clear();
        }
        countMeasures = oldCountMeasures = 0;
        mMapChartIndexToDate.clear();
        GraphPopulatorUtils.clearYTextLabels(mRenderer);
        repaintChartView();
    }

    private Date mReportDate;

    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat
            ("MM.dd.yyyy HH:mm:ss");

    //TODO implement this for handle report changes


    @Override
    public Date currentDate() {
        return mReportDate;
    }

    @Override
    public String reportDateString() {
        mReportDate = new Date();

        return FORMATTER.format(mReportDate);
    }

    @Override
    public String sampleId() {
        return null;
    }

    @Override
    public String location() {
        return null;
    }

    @Override
    public int countMinutes() {
        return mPrefs.getInt(PrefConstants.DURATION, 0);
    }

    @Override
    public int volume() {
        return mPrefs.getInt(PrefConstants.VOLUME, 0);
    }

    @Override
    public String operator() {
        return null;
    }

    @Override
    public String dateString() {
        return FORMATTER.format(mReportDate);
    }

    @Override
    public void writeReport(String reportHtml, String fileName) {
        File file = new File(reportFolders(), fileName + ".html");
        file.getParentFile().mkdirs();
        try {
            file.createNewFile();
            TextFileUtils.writeTextFile(file.getAbsolutePath(), reportHtml);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String reportFolders() {
        return new File(Environment.getExternalStorageDirectory(), AppData
                .REPORT_FOLDER_NAME).getAbsolutePath();
    }
}