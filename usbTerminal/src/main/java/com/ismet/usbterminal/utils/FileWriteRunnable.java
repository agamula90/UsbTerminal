package com.ismet.usbterminal.utils;

import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileWriteRunnable implements Runnable{

	private final String content;

	private final String filename;
	private final String dirName;
	private final String subDirname;

	public FileWriteRunnable(String content, String filename, String dirName, String subDirname) {
		this.content = content;
		this.filename = filename;
		this.dirName = dirName;
		this.subDirname = subDirname;
	}

	@Override
	public void run() {
		try {
			File dir = new File(Environment.getExternalStorageDirectory(), dirName);
			if (!dir.exists()) {
				dir.mkdirs();
			}

			dir = new File(dir, subDirname);
			if (!dir.exists()) {
				dir.mkdir();
			}

			SimpleDateFormat formatter = new SimpleDateFormat("mm:ss.S", Locale.ENGLISH);

			File file = new File(dir, filename);

			if (!file.exists()) {
				file.createNewFile();
			}

			String preFormattedTime = formatter.format(new Date());
			String[] arr = preFormattedTime.split("\\.");

			String formattedTime = "";
			if (arr.length == 1) {
				formattedTime = arr[0] + ".0";
			} else if (arr.length == 2) {
				formattedTime = arr[0] + "." + arr[1].substring(0, 1);
			}

			FileOutputStream fos = new FileOutputStream(file, true);
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
			writer.write(formattedTime + "," + content + "\n");
			writer.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
