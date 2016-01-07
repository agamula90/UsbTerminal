package com.ismet.usbterminal.updated.mainscreen;

import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.ismet.usbterminal.utils.FileWriteRunnable;
import com.ismet.usbterminal.utils.Utils;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;

public class EToCMainHandler extends Handler {

	public static final int MESSAGE_USB_DATA_RECEIVED = 0;

	public static final int MESSAGE_USB_DATA_READY = 1;

	public static final int MESSAGE_OPEN_CHART = 2;

	private final WeakReference<EToCMainActivity> weakActivity;

	public EToCMainHandler(EToCMainActivity tedActivity) {
		super();
		this.weakActivity = new WeakReference<>(tedActivity);
	}

	@Override
	public void handleMessage(Message msg) {
		super.handleMessage(msg);

		if (weakActivity.get() != null) {
			synchronized (weakActivity.get()) {
				EToCMainActivity activity = weakActivity.get();
				switch (msg.what) {
					case MESSAGE_USB_DATA_RECEIVED:
						// String data = (String) msg.obj;
						// data = data.replace("\r", "");
						// data = data.replace("\n", "");

						// byte [] arrT1 = data.getBytes();
						byte[] usbReadBytes = (byte[]) msg.obj;
						// String strH="";
						String data = "";
						if (usbReadBytes.length == 7) {
							if ((String.format("%02X", usbReadBytes[0]).equals("FE")) && (String
									.format("%02X", usbReadBytes[1]).equals("44"))) {
								// SENSOR Response
								String strHex = "";
								for (byte b : usbReadBytes) {
									strHex = strHex + String.format("%02X-", b);
								}
								int end = strHex.length() - 1;
								data = strHex.substring(0, end);

								String strH = String.format("%02X%02X", usbReadBytes[3],
										usbReadBytes[4]);
								int co2 = Integer.parseInt(strH, 16);

								if (activity.getRenderer() != null) {
									int yMax = (int) activity.getRenderer().getYAxisMax();
									if (co2 >= yMax) {
										if (activity.getCurrentSeries().getItemCount() == 0) {
											int vmax = 3 * co2;
											activity.getRenderer().setYAxisMax(vmax);
										} else {
											int vmax = (int) (co2 + (co2 * 15) / 100f);
											activity.getRenderer().setYAxisMax(vmax);
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
									//activity.refreshCurrentSeries();
									// if(idx_count<=1){
									// // Toast.makeText(EToCMainActivity.this,
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
									//activity.refreshCurrentSeries();
									// idx_count++;
									// }
									// }
									// }

									// XYSeries currentSeries =
									// currentdataset.getSeriesAt(0);

									// file writing
									// Toast.makeText(EToCMainActivity.this, filename,
									// Toast.LENGTH_SHORT).show();

									// auto
									int delay_v = activity.getPrefs().getInt("delay", 2);
									int duration_v = activity.getPrefs().getInt("duration", 3);
									int rCount1 = (int) ((duration_v * 60) / delay_v);
									int rCount2 = (int) (duration_v * 60);
									boolean isauto = activity.getPrefs().getBoolean("isauto",
											false);
									if (isauto) {
										if (activity.getReadingCount() == rCount1) {
											activity.incCountMeasure();
											activity.setChartIdx(2);
											activity.setCurrentSeries(1);
										} else if (activity.getReadingCount() == rCount2) {
											activity.incCountMeasure();
											activity.setChartIdx(3);
											activity.setCurrentSeries(2);
										}
									}

									if (activity.getCountMeasure() != activity.getOldCountMeasure()) {
										Date currentTime = new Date();
										SimpleDateFormat formatter_date = new SimpleDateFormat
												("yyyyMMdd_HHmmss");
										activity.setChartDate(formatter_date.format(currentTime));

										if (activity.getCountMeasure() == 1) {
											activity.setSubDirDate(formatter_date.format
													(currentTime));
										}
										activity.refreshOldCountMeasure();
										activity.getMapChartDate().put(activity.getChartIdx(),
												activity.getChartDate());
									}

									if (activity.isTimerRunning()) {
										activity.getCurrentSeries().add(activity.getReadingCount()
												, co2);
										activity.repaintChartView();
										int kppm = activity.getPrefs().getInt("kppm", -1);
										int volume = activity.getPrefs().getInt("volume", -1);

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

										String str_uc = activity.getPrefs().getString("uc", "");
										if (strppm.equals("_")) {
											String filename1 = "";
											String dirname1 = "AEToC_MES_Files";
											String subDirname1 = "";

											filename1 = "MES_" + activity.getChartDate() +
													strvolume + "_R" + activity.getChartIdx() + "" +
													".csv";
											//dirname1 = "AEToC_MES_Files";
											subDirname1 = "MES_" + activity.getSubDirDate() + "_" +
													str_uc;//+"_"+strppm;

											activity.execute(new FileWriteRunnable("" + co2,
													filename1, dirname1, subDirname1));
										} else {
											String filename2 = "";
											String dirname2 = "AEToC_CAL_Files";
											String subDirname2 = "";

											filename2 = "CAL_" + activity.getChartDate() +
													strvolume + strppm + "_R" + activity
													.getChartIdx() + ".csv";
											//dirname2 = "AEToC_MES_Files";
											subDirname2 = "CAL_" + activity.getSubDirDate() + "_" +
													str_uc;//strppm;//;

											activity.execute(new FileWriteRunnable("" +
													co2, filename2, dirname2, subDirname2));
										}

									}

									if (co2 == 10000) {
										Toast.makeText(activity, "Dilute sample", Toast
												.LENGTH_LONG).show();
									}
								}

								data += "\nCO2: " + co2 + " ppm";

							} else {
								data = new String(usbReadBytes);
							}
						} else {
							data = new String(usbReadBytes);

						}

						Utils.appendText(activity.getTxtOutput(), "Rx: " + data);
						activity.getScrollView().smoothScrollTo(0, 0);
						break;
					case MESSAGE_USB_DATA_READY:
						String command = (String) msg.obj;
						activity.sendCommand(command);
						break;
					case MESSAGE_OPEN_CHART:
						String strMsg = (String) msg.obj;
						String[] arr = strMsg.split(",");
						int c = Integer.parseInt(arr[0]);
						double co2 = Double.parseDouble(arr[1]);

						int yMax = (int) activity.getRenderer().getYAxisMax();
						if (co2 >= yMax) {
							// int vmax = (int) (co2 + (co2*15)/100f);
							// int vmax_extra = (int) Math.ceil(1.5 *
							// (co2/20)) ;
							// int vmax = co2 + vmax_extra;
							if (activity.getChartSeries().getItemCount() == 0) {
								int vmax = (int) (3 * co2);
								activity.getRenderer().setYAxisMax(vmax);
							} else {
								int vmax = (int) (co2 + (co2 * 15) / 100f);
								activity.getRenderer().setYAxisMax(vmax);
							}
						}

						activity.getChartSeries().add(c, co2);
						activity.getChartView().repaint();
						break;
				}
			}
		}
	}
}
