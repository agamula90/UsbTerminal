package com.ismet.usbterminal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.proggroup.areasquarecalculator.InterpolationCalculatorApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MyApplication extends InterpolationCalculatorApp {

	public static String MyLock = "Lock";

	private static MyApplication instance;

	private static boolean activityVisible;

	public static boolean isActivityVisible() {
		return activityVisible;
	}

	public static void activityResumed() {
		activityVisible = true;
	}

	public static void activityPaused() {
		activityVisible = false;
	}

	public static MyApplication getInstance() {
		return instance;
	}

	public static boolean deleteDir(File dir) {
		if (dir != null && dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}

		return dir.delete();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
		Log.v("MyApplication", "onCreate triggered");

		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {

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
	                                /* + ex.getStackTrace().toString()+ "\r"+ */ +
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
					// intent.setClass(MyApplication.this,MainActivity.class);
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
		});
	}

	public void clearApplicationData() {
		File cache = getCacheDir();
		File appDir = new File(cache.getParent());
		if (appDir.exists()) {
			String[] children = appDir.list();
			for (String s : children) {
				if (!s.equals("lib")) {
					deleteDir(new File(appDir, s));
					Log.i("TAG", "**************** File /data/data/APP_PACKAGE/" + s + " DELETED " +
							"*******************");
				}
			}
		}
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
	}
}
