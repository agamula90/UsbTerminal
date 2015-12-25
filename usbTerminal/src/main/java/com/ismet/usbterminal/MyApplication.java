package com.ismet.usbterminal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.proggroup.areasquarecalculator.InterpolationCalculator;

public class MyApplication extends InterpolationCalculator {
	private static MyApplication instance;
	public static String MyLock = "Lock";

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	public static boolean isActivityVisible() {
		return activityVisible;
	}

	public static void activityResumed() {
		activityVisible = true;
	}

	public static void activityPaused() {
		activityVisible = false;
	}

	private static boolean activityVisible;

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
		Log.v("MyApplication", "onCreate triggered");

		/*Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			public void uncaughtException(Thread thread, Throwable ex) {

				SimpleDateFormat formatter = new SimpleDateFormat(
						"dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);
				Date currentTime = new Date();

				try {
					File dir = new File(Environment
							.getExternalStorageDirectory(), "CLogs");
					dir.mkdirs();
					System.out.println("file != null");

					File file = new File(dir, "CrashLogs.txt");
					FileOutputStream fos = new FileOutputStream(file, true);
					new PrintStream(fos)
							.print("=========================================================\r"
									+ formatter.format(currentTime)
									+ "\r"
									/* + ex.getStackTrace().toString()+ "\r"+ * /
									+ "=========================================================\r");
					ex.printStackTrace(new PrintStream(fos));
					fos.close();

					Uri uri = Uri.fromFile(file);
					// Toast.makeText(getActivity(), "exists",
					// Toast.LENGTH_LONG).show();
					Intent sendIntent = new Intent(Intent.ACTION_SEND);
					sendIntent.setType("application/pdf");
					sendIntent.putExtra(Intent.EXTRA_EMAIL,
							new String[] { "nikhil.talaviya@gmail.com" });
					sendIntent.putExtra(Intent.EXTRA_SUBJECT, "DCam LOGS");
					sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
					sendIntent.putExtra(Intent.EXTRA_TEXT, "DCam LOGS");
					// startActivity(sendIntent);

					// System.exit(2);

					// restart your app here like this
					// Intent intent = new Intent();
					// intent.setClass(MyApplication.this,MainActivity.class);
					//
					PendingIntent pendingIntent = PendingIntent.getActivity(
							getBaseContext(), 0, sendIntent,
							sendIntent.getFlags());
					//
					AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
					mgr.set(AlarmManager.RTC,
							System.currentTimeMillis() + 1000, pendingIntent);
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

	public static MyApplication getInstance() {
		return instance;
	}

	public void clearApplicationData() {
		File cache = getCacheDir();
		File appDir = new File(cache.getParent());
		if (appDir.exists()) {
			String[] children = appDir.list();
			for (String s : children) {
				if (!s.equals("lib")) {
					deleteDir(new File(appDir, s));
					Log.i("TAG",
							"**************** File /data/data/APP_PACKAGE/" + s
									+ " DELETED *******************");
				}
			}
		}
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
	public void onLowMemory() {
		super.onLowMemory();
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
	}
}
