package fr.xgouchet.utils;

import android.app.Activity;
import android.os.Environment;

import java.io.File;

public class RootDirectoryHandleUtils {

	private RootDirectoryHandleUtils() {
	}

	public static boolean handleEnvironmentStorageDirectory(Activity activity, File
			folderForParentHandle, boolean isBackPressed) {

		final File fileForEqualsSearching;

		if (isBackPressed) {
			fileForEqualsSearching = Environment.getExternalStorageDirectory();
		} else {
			fileForEqualsSearching = Environment.getExternalStorageDirectory().getParentFile();
		}
		if (folderForParentHandle.equals(fileForEqualsSearching)) {
			activity.setResult(Activity.RESULT_CANCELED);
			activity.finish();
			return true;
		} else {
			return false;
		}
	}
}
