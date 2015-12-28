package com.ismet.usbterminal.threads;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.os.Environment;


public class FileWriterThread implements Runnable {
    String co2 = "", chartidx = "", measure_idx = "", chart_date = "", chart_time = "";
    String filename = "", dirName = "", subDirname = "";

    //public FileWriterThread(String co2, String chartidx, String measure_idx, String chart_date,
    // String chart_time, String dirName){
    public FileWriterThread(String co2, String filename, String dirName, String subDirname) {
        this.co2 = co2;
        //this.chartidx = chartidx;
        //this.measure_idx = measure_idx;
        //this.chart_date = chart_date;
        //this.chart_time = chart_time;
        //this.filename = "m"+measure_idx+"_c"+chartidx+"_"+chart_date+".csv";
        //this.filename = "MES_"+chart_date+"_R"+chartidx+".csv";
        this.filename = filename;
        this.dirName = dirName;
        this.subDirname = subDirname;
    }

    @Override
    public void run() {
//		SimpleDateFormat formatter = new SimpleDateFormat(
//				"yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
//		SimpleDateFormat formatter = new SimpleDateFormat(
//				"HH:mm:ss.SSS", Locale.ENGLISH);
        SimpleDateFormat formatter = new SimpleDateFormat(
                "mm:ss.S", Locale.ENGLISH);
        Date currentTime = new Date();
        try {
            File dir = new File(
                    Environment
                            .getExternalStorageDirectory(),
                    dirName);//"AEToCLogs"
            if (!dir.exists()) {
                dir.mkdirs();
            }

            dir = new File(
                    Environment
                            .getExternalStorageDirectory() + "/" + dirName,
                    subDirname);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            //System.out.println("file != null");

            // create file
//			SimpleDateFormat formatter_date = new SimpleDateFormat(
//					"yyyy-MM-dd", Locale.ENGLISH);
//			SimpleDateFormat formatter_time = new SimpleDateFormat(
//					"HH:mm:ss", Locale.ENGLISH);			
//			
//			String current_date = formatter_date.format(currentTime);
//			String current_time = formatter_time.format(currentTime);
            //String filename = current_date+"_"+current_time+"_set_"+count_measure+".csv";

            File file;
            //file = new File(dir, "sensordata.csv");
//			if(filename.equals("")){
//				file = new File(dir, "sensordata.csv");
//			}else{
//				file = new File(dir, filename);
//			}

            file = new File(Environment
                    .getExternalStorageDirectory() + "/" + dirName + "/" + subDirname, filename);

            if (!file.exists()) {
                file.createNewFile();
            }

            FileOutputStream fos = new FileOutputStream(
                    file, true);
            String stime = formatter
                    .format(currentTime);
            String[] arr = stime.split("\\.");

            String strT = "";
            if (arr.length == 1) {
                strT = arr[0] + ".0";
            } else if (arr.length == 2) {
                strT = arr[0] + "." + arr[1].substring(0, 1);
            }

//			String ctime = stime.substring(0, (stime.length()-2));
//			ctime = ctime.replace(".", ".0");

            new PrintStream(fos).print(strT
                    + ","
                    + co2 + "\n");
            fos.close();
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
    }

}
