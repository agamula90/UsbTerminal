package com.ismet.usbterminal.updated.mainscreen;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.ismet.usbterminal.updated.EToCApplication;
import com.ismet.usbterminal.updated.data.AppData;
import com.ismet.usbterminal.updated.data.PowerCommand;
import com.ismet.usbterminal.updated.data.PowerState;
import com.ismet.usbterminal.updated.data.PrefConstants;
import com.ismet.usbterminal.updated.data.PullState;
import com.ismet.usbterminal.updated.data.TemperatureData;
import com.ismet.usbterminal.updated.mainscreen.powercommands.PowerCommandsFactory;
import com.ismet.usbterminal.updated.services.PullStateManagingService;
import com.ismet.usbterminal.utils.FileWriteRunnable;
import com.ismet.usbterminal.utils.Utils;
import com.proggroup.areasquarecalculator.utils.ToastUtils;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;

public class EToCMainHandler extends Handler {

	public static final String USB_DATA_READY = "com.ismet.usbservice.USB_DATA_READY";

	public static final String UN_SCHEDULING = "com.ismet.usbservice.UN_SCHEDULING";

	public static final String DATA_EXTRA = "data_extra";

    public static final String IS_TOAST = "is_toast";

	public static final int MESSAGE_USB_DATA_RECEIVED = 0;

	public static final int MESSAGE_USB_DATA_READY = 1;

	public static final int MESSAGE_OPEN_CHART = 2;

	public static final int MESSAGE_RESUME_AUTO_PULLING = 3;

	public static final int MESSAGE_INTERRUPT_ACTIONS = 4;

	public static final int MESSAGE_PAUSE_AUTO_PULLING = 5;

	public static final int MESSAGE_STOP_PULLING_FOR_TEMPERATURE = 6;

	public static final int MESSAGE_CHECK_TEMPERATURE = 7;

	public static final int MESSAGE_SIMULATE_RESPONSE = 8;

	public static final int MESSAGE_SHOW_TOAST = 9;

	private static final long DELAY_BEFORE_START_AUTO_PULLING = 500;

	private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyyMMdd_HHmmss");

	private final WeakReference<EToCMainActivity> weakActivity;

	private
	@PullState
	int mTempState;

	public EToCMainHandler(EToCMainActivity tedActivity) {
		super();
		this.weakActivity = new WeakReference<>(tedActivity);
	}

	@Override
	public void handleMessage(Message msg) {
		super.handleMessage(msg);

		if (weakActivity.get() != null) {
			EToCMainActivity activity = weakActivity.get();
			EToCApplication application = EToCApplication.getInstance();
			switch (msg.what) {
				case MESSAGE_USB_DATA_RECEIVED:
					int pullState = application.getPullState();

					byte[] usbReadBytes = (byte[]) msg.obj;
					String data = "";
					final String responseForChecking;
					if (usbReadBytes.length == 7) {
						if ((String.format("%02X", usbReadBytes[0]).equals("FE")) && (String
								.format("%02X", usbReadBytes[1]).equals("44"))) {

                            /*if(pullState != PullState.NONE) {
                                application.unScheduleTasks();
                            }*/

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
								int delay_v = activity.getPrefs().getInt(PrefConstants.DELAY, 2);
								int duration_v = activity.getPrefs().getInt(PrefConstants
										.DURATION, 3);
								int rCount1 = (int) ((duration_v * 60) / delay_v);
								int rCount2 = (int) (duration_v * 60);
								boolean isauto = activity.getPrefs().getBoolean(PrefConstants
										.IS_AUTO, false);
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

								Date currentDate = new Date();

								if (activity.getCountMeasure() != activity.getOldCountMeasure()) {
									activity.setChartDate(FORMATTER.format(currentDate));

									activity.refreshOldCountMeasure();
									activity.getMapChartDate().put(activity.getChartIdx(),
											activity.getChartDate());
								}

								if (activity.getSubDirDate() == null) {
									activity.setSubDirDate(FORMATTER.format(currentDate));
								}

								if (activity.isTimerRunning()) {
									activity.getCurrentSeries().add(activity.getReadingCount(),
											co2);
									activity.repaintChartView();
									int ppm = activity.getPrefs().getInt(PrefConstants.KPPM, -1);
									int volumeValue = activity.getPrefs().getInt(PrefConstants
											.VOLUME, -1);

									final String ppmPrefix;
									final String volume = "_" + (volumeValue == -1 ? "" : "" +
											volumeValue);

									if (ppm == -1) {
										ppmPrefix = "_";
									} else {
										ppmPrefix = "_" + ppm;
									}

									String str_uc = activity.getPrefs().getString(PrefConstants
											.USER_COMMENT, "");

									final String fileName;
									final String dirName;
									final String subDirName;

									if (ppmPrefix.equals("_")) {
										dirName = AppData.MES_FOLDER_NAME;

										fileName = "MES_" + activity.getChartDate() +
												volume + "_R" + activity.getChartIdx() + "" +
												".csv";
										subDirName = "MES_" + activity.getSubDirDate() + "_" +
												str_uc;

									} else {
										dirName = AppData.CAL_FOLDER_NAME;

										fileName = "CAL_" + activity.getChartDate() +
												volume + ppmPrefix + "_R" + activity.getChartIdx()
												+ ".csv";
										subDirName = "CAL_" + activity.getSubDirDate() + "_" +
												str_uc;
									}

									activity.execute(new FileWriteRunnable("" + co2, fileName,
											dirName, subDirName));
								}

								if (co2 == 10000) {
									Toast toast = Toast.makeText(activity, "Dilute sample", Toast
											.LENGTH_LONG);
									ToastUtils.wrap(toast);
									toast.show();
								}
							}

							data += "\nCO2: " + co2 + " ppm";

							activity.refreshTextAccordToSensor(false, co2 + "");

							responseForChecking = co2 + "";

                            /*if(pullState != PullState.NONE && !application.isPullingStopped()) {
	                            mTempState = PullState.TEMPERATURE;
	                            application.setPullState(PullState.NONE);
	                            Message message = obtainMessage(MESSAGE_RESUME_AUTO_PULLING);
                                sendMessageDelayed(message, PullStateManagingService
                                         .DELAY_ON_CHANGE_REQUEST);
                            }*/
						} else {
							data = new String(usbReadBytes);
							data = data.replace("\r", "");
							data = data.replace("\n", "");
							responseForChecking = new String(data);
						}
					} else {
					    /*if(pullState != PullState.NONE) {
				            application.unScheduleTasks();
                        }*/
						data = new String(usbReadBytes);
						data = data.replace("\r", "");
						data = data.replace("\n", "");
						activity.refreshTextAccordToSensor(true, data);

						responseForChecking = new String(data);

                        /*if(pullState != PullState.NONE && !application.isPullingStopped()) {
	                        mTempState = PullState.CO2;
	                        application.setPullState(PullState.NONE);
	                        Message message = obtainMessage(MESSAGE_RESUME_AUTO_PULLING);
                            sendMessageDelayed(message, PullStateManagingService
                                     .DELAY_ON_CHANGE_REQUEST);
                        }*/
					}

					//if(pullState == PullState.NONE) {
					Utils.appendText(activity.getTxtOutput(), "Rx: " + data);
					activity.getScrollView().smoothScrollTo(0, 0);

					if (activity.isPowerPressed()) {
						handleResponse(weakActivity, responseForChecking);
					} else {
						PowerCommandsFactory powerCommandsFactory = activity
								.getPowerCommandsFactory();
						final int powerState = powerCommandsFactory.currentPowerState();
						if (powerState == PowerState.PRE_LOOPING) {
							EToCApplication.getInstance().setPreLooping(false);
							sendMessage(obtainMessage(MESSAGE_STOP_PULLING_FOR_TEMPERATURE));
							powerCommandsFactory.moveStateToNext();
						}
					}
					//}

					break;
				case MESSAGE_USB_DATA_READY:
					String command = (String) msg.obj;
					activity.sendCommand(command);
					break;
                case MESSAGE_SHOW_TOAST:
                    activity.showToastMessage(msg.obj.toString());
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
				case MESSAGE_RESUME_AUTO_PULLING:
					//application.setPullState(mTempState);
					//mTempState = PullState.NONE;

					activity.startService(PullStateManagingService.intentForService(activity,
							true));
					break;
				case MESSAGE_INTERRUPT_ACTIONS:
					handleResponse(weakActivity, "");
					break;
				case MESSAGE_PAUSE_AUTO_PULLING:
					activity.startService(PullStateManagingService.intentForService(activity,
							false));
					break;
				case MESSAGE_STOP_PULLING_FOR_TEMPERATURE:
					Intent i = PullStateManagingService.intentForService(activity, false);
					i.setAction(PullStateManagingService.WAIT_FOR_COOLING_ACTION);
					activity.startService(i);
					if (activity.getPowerCommandsFactory().getCoolingDialog() != null) {
						activity.getPowerCommandsFactory().getCoolingDialog().dismiss();
					}
					break;
				case MESSAGE_SIMULATE_RESPONSE:
					String sVal = "";
					if (msg.obj != null) {
						sVal = msg.obj.toString();
					}

					if (activity.isPowerPressed()) {
						handleResponse(weakActivity, sVal);
					} else {
						PowerCommandsFactory powerCommandsFactory = activity
								.getPowerCommandsFactory();
						final int powerState = powerCommandsFactory.currentPowerState();
						if (powerState == PowerState.PRE_LOOPING) {
							EToCApplication.getInstance().setPreLooping(false);
							sendMessage(obtainMessage(MESSAGE_STOP_PULLING_FOR_TEMPERATURE));
							powerCommandsFactory.moveStateToNext();
						}
					}
					break;
			}
		}
	}

	private void handleResponse(final WeakReference<EToCMainActivity> activityWeakReference,
			String response) {

		boolean correctResponse = false;

		int curPowerState = 0;

		if (activityWeakReference.get() != null) {
			final EToCMainActivity activity = activityWeakReference.get();
			PowerCommandsFactory powerCommandsFactory = activity.getPowerCommandsFactory();
			final int powerState = powerCommandsFactory.currentPowerState();
			curPowerState = powerState;
			switch (powerState) {
				case PowerState.ON_STAGE1:
				case PowerState.ON_STAGE1_REPEAT:
				case PowerState.ON_STAGE3A:
                case PowerState.ON_STAGE3B:
				case PowerState.ON_STAGE2B:
				case PowerState.ON_STAGE2:
				case PowerState.ON_STAGE3:
				case PowerState.ON_STAGE4:
				case PowerState.ON_RUNNING:

					PowerCommand currentCommand = powerCommandsFactory.currentCommand();
					powerCommandsFactory.moveStateToNext();

					if (currentCommand.hasSelectableResponses()) {
						if (currentCommand.isResponseCorrect(response)) {

							if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
								postDelayed(new Runnable() {

									@Override
									public void run() {
										if (activityWeakReference.get() != null) {
											PowerCommandsFactory powerCommandsFactory =
													activityWeakReference.get()
															.getPowerCommandsFactory();

											if (powerCommandsFactory.currentPowerState() !=
													PowerState.ON) {
												powerCommandsFactory.sendRequest
														(activityWeakReference.get(),
																EToCMainHandler.this,
																activityWeakReference.get());

											}
										}
									}
								}, currentCommand.getDelay());
							}

							correctResponse = true;
						} else {
							StringBuilder responseBuilder = new StringBuilder();
							for (String possibleResponse : currentCommand.getPossibleResponses()) {
								responseBuilder.append("\"" + possibleResponse + "\" or ");
							}
							responseBuilder.delete(responseBuilder.length() - 4, responseBuilder
									.length());

							Toast toast = Toast.makeText(activity, "Wrong response: Got - \"" +
									response + "\"" + ".Expected - " + responseBuilder.toString(),
									Toast.LENGTH_LONG);
							ToastUtils.wrap(toast);
							toast.show();
							return;
						}
					} else if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
						powerCommandsFactory.sendRequest(activity, this, activity);
					}

					if (powerCommandsFactory.currentPowerState() == PowerState.ON) {
						activity.initPowerAccordToItState();
						sendMessage(Message.obtain(this, MESSAGE_RESUME_AUTO_PULLING));
						return;
					}
					correctResponse = true;
					break;
				case PowerState.OFF_INTERRUPTING:
					sendMessage(Message.obtain(this, MESSAGE_PAUSE_AUTO_PULLING));
					powerCommandsFactory.moveStateToNext();

					final long delayForPausing = powerCommandsFactory.currentCommand().getDelay();

					postDelayed(new Runnable() {

						@Override
						public void run() {
							if (activityWeakReference.get() != null) {
								activityWeakReference.get().interruptActionsIfAny();

								postDelayed(new Runnable() {

									@Override
									public void run() {
										if (activityWeakReference.get() != null) {
											PowerCommandsFactory powerCommandsFactory =
													activityWeakReference.get()
															.getPowerCommandsFactory();

											if (powerCommandsFactory.currentPowerState() !=
													PowerState.OFF) {
												powerCommandsFactory.sendRequest
														(activityWeakReference.get(),
																EToCMainHandler.this,
																activityWeakReference.get());

											}
										}
									}
								}, delayForPausing);
							}
						}
					}, delayForPausing);

					correctResponse = true;
					break;
				case PowerState.OFF_STAGE1:
					TemperatureData temperatureData = TemperatureData.parse(response);
					if (temperatureData.isCorrect()) {
						int curTemperature = temperatureData.getTemperature1();

						if (curTemperature <= EToCApplication.getInstance()
								.getBorderCoolingTemperature()) {
							powerCommandsFactory.moveStateToNext();
						}
						powerCommandsFactory.moveStateToNext();

						postDelayed(new Runnable() {

							@Override
							public void run() {
								if (activityWeakReference.get() != null) {
									PowerCommandsFactory powerCommandsFactory =
											activityWeakReference.get().getPowerCommandsFactory();

									if (powerCommandsFactory.currentPowerState() != PowerState
											.OFF) {
										powerCommandsFactory.sendRequest(activityWeakReference.get
												(), EToCMainHandler.this, activityWeakReference
												.get());

									}
								}
							}
						}, powerCommandsFactory.currentCommand().getDelay());
						correctResponse = true;
					}
					break;
				case PowerState.OFF_WAIT_FOR_COOLING:
					temperatureData = TemperatureData.parse(response);
					if (temperatureData.isCorrect()) {
						int curTemperature = temperatureData.getTemperature1();

						if (curTemperature <= EToCApplication.getInstance()
								.getBorderCoolingTemperature()) {
							sendMessage(Message.obtain(this,
									MESSAGE_STOP_PULLING_FOR_TEMPERATURE));
							powerCommandsFactory.moveStateToNext();

							postDelayed(new Runnable() {

								@Override
								public void run() {
									if (activityWeakReference.get() != null) {
										PowerCommandsFactory powerCommandsFactory =
												activityWeakReference.get()
														.getPowerCommandsFactory();

										if (powerCommandsFactory.currentPowerState() != PowerState
												.OFF) {
											powerCommandsFactory.sendRequest(activityWeakReference
													.get(), EToCMainHandler.this,
													activityWeakReference.get());
										}
									}
								}
							}, powerCommandsFactory.currentCommand().getDelay());
						}
						correctResponse = true;
					}
					break;
				case PowerState.OFF_RUNNING:
				case PowerState.OFF_FINISHING:
					powerCommandsFactory.moveStateToNext();
					if (powerCommandsFactory.currentPowerState() == PowerState.OFF) {
						activity.initPowerAccordToItState();
						return;
					}

					currentCommand = powerCommandsFactory.currentCommand();

					if (currentCommand.hasSelectableResponses()) {
						if (currentCommand.isResponseCorrect(response)) {
							postDelayed(new Runnable() {

								@Override
								public void run() {
									if (activityWeakReference.get() != null) {
										PowerCommandsFactory powerCommandsFactory =
												activityWeakReference.get()
														.getPowerCommandsFactory();
										if (powerCommandsFactory.currentPowerState() != PowerState
												.OFF) {
											powerCommandsFactory.sendRequest(activityWeakReference
													.get(), EToCMainHandler.this,
													activityWeakReference.get());

										}
									}
								}
							}, powerCommandsFactory.currentCommand().getDelay());

							correctResponse = true;
						} else {
							StringBuilder responseBuilder = new StringBuilder();
							for (String possibleResponse : currentCommand.getPossibleResponses()) {
								responseBuilder.append("\"" + possibleResponse + "\" or ");
							}
							responseBuilder.delete(responseBuilder.length() - 4, responseBuilder
									.length());

							Toast toast = Toast.makeText(activity, "Wrong response: Got - \"" +
									response + "\"" + ".Expected - " + responseBuilder.toString(),
									Toast.LENGTH_LONG);
							ToastUtils.wrap(toast);
							toast.show();
							return;
						}
					}
					break;
			}
		}

		/*if (!correctResponse && activityWeakReference.get() != null) {
			Toast.makeText(activityWeakReference.get(), "Wrong response parsing. Message:[" +
					response + "]. PowerState - " + curPowerState, Toast.LENGTH_LONG).show();
		}*/
	}
}
