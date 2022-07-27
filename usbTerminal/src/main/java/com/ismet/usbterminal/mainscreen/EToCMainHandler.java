package com.ismet.usbterminal.mainscreen;

import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.ismet.usbterminal.EToCApplication;
import com.ismet.usbterminal.MainActivity;
import com.ismet.usbterminal.data.AppData;
import com.ismet.usbterminal.data.PowerCommand;
import com.ismet.usbterminal.data.PowerState;
import com.ismet.usbterminal.data.PrefConstants;
import com.ismet.usbterminal.data.TemperatureData;
import com.ismet.usbterminal.mainscreen.powercommands.PowerCommandsFactory;
import com.ismet.usbterminal.utils.FileWriteRunnable;
import com.ismet.usbterminal.utils.Utils;
import com.proggroup.areasquarecalculator.utils.ToastUtils;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;

import kotlin.Deprecated;

@Deprecated(message = "Use MainViewModel instead")
public class EToCMainHandler extends Handler {

	public static final String USB_DATA_READY = "com.ismet.usbservice.USB_DATA_READY";

	public static final String DATA_EXTRA = "data_extra";

    public static final String IS_TOAST = "is_toast";

	public static final int MESSAGE_USB_DATA_RECEIVED = 0;

	public static final int MESSAGE_USB_DATA_READY = 1;

	public static final int MESSAGE_OPEN_CHART = 2;

	public static final int MESSAGE_RESUME_AUTO_PULLING = 3;

	public static final int MESSAGE_INTERRUPT_ACTIONS = 4;

	public static final int MESSAGE_PAUSE_AUTO_PULLING = 5;

    public static final int MESSAGE_SIMULATE_RESPONSE = 8;

	public static final int MESSAGE_SHOW_TOAST = 9;

    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyyMMdd_HHmmss");

	private final WeakReference<MainActivity> weakActivity;

	public EToCMainHandler(MainActivity tedActivity) {
		super();
		this.weakActivity = new WeakReference<>(tedActivity);
	}

    public EToCMainHandler(EToCMainActivity tedActivity) {
        super();
        this.weakActivity = new WeakReference<>(null);
    }

	@Override
	public void handleMessage(Message msg) {
		super.handleMessage(msg);

		if (weakActivity.get() != null) {
			MainActivity activity = weakActivity.get();
			EToCApplication application = EToCApplication.getInstance();
			switch (msg.what) {
				case MESSAGE_USB_DATA_RECEIVED:

                    byte[] usbReadBytes = (byte[]) msg.obj;
					String data = "";
					final String responseForChecking;
					if (usbReadBytes.length == 7) {
						if ((String.format("%02X", usbReadBytes[0]).equals("FE")) && (String
								.format("%02X", usbReadBytes[1]).equals("44"))) {
							String strHex = "";
							for (byte b : usbReadBytes) {
								strHex = strHex + String.format("%02X-", b);
							}
							int end = strHex.length() - 1;
							data = strHex.substring(0, end);

							String strH = String.format("%02X%02X", usbReadBytes[3],
									usbReadBytes[4]);
							int co2 = Integer.parseInt(strH, 16);

                            activity.getRenderer();
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

                            data += "\nCO2: " + co2 + " ppm";

							activity.refreshTextAccordToSensor(false, co2 + "");

							responseForChecking = co2 + "";
						} else {
							data = new String(usbReadBytes);
							data = data.replace("\r", "");
							data = data.replace("\n", "");
							responseForChecking = new String(data);
						}
					} else {
						data = new String(usbReadBytes);
						data = data.replace("\r", "");
						data = data.replace("\n", "");
						activity.refreshTextAccordToSensor(true, data);

						responseForChecking = data;
					}

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
							activity.stopPullingForTemperature();
							powerCommandsFactory.moveStateToNext();
						}
					}

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
					activity.startSendingTemperatureOrCo2Requests();
					break;
				case MESSAGE_INTERRUPT_ACTIONS:
					handleResponse(weakActivity, "");
					break;
				case MESSAGE_PAUSE_AUTO_PULLING:
					activity.stopSendingTemperatureOrCo2Requests();
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
							activity.stopPullingForTemperature();
							powerCommandsFactory.moveStateToNext();
						}
					}
					break;
			}
		}
	}

	private void handleResponse(final WeakReference<MainActivity> activityWeakReference,
			String response) {

        if (activityWeakReference.get() != null) {
			final MainActivity activity = activityWeakReference.get();
			PowerCommandsFactory powerCommandsFactory = activity.getPowerCommandsFactory();
			final int powerState = powerCommandsFactory.currentPowerState();
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
								postDelayed(() -> {
                                    if (activityWeakReference.get() != null) {
                                        PowerCommandsFactory powerCommandsFactory1 =
                                                activityWeakReference.get()
                                                        .getPowerCommandsFactory();

                                        if (powerCommandsFactory1.currentPowerState() !=
                                                PowerState.ON) {
                                            powerCommandsFactory1.sendRequest
                                                    (activityWeakReference.get(),
                                                            EToCMainHandler.this,
                                                            activityWeakReference.get());

                                        }
                                    }
                                }, currentCommand.getDelay());
							}

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
                    break;
				case PowerState.OFF_INTERRUPTING:
					sendMessage(Message.obtain(this, MESSAGE_PAUSE_AUTO_PULLING));
					powerCommandsFactory.moveStateToNext();

					final long delayForPausing = powerCommandsFactory.currentCommand().getDelay();

					postDelayed(() -> {
                        if (activityWeakReference.get() != null) {
                            activityWeakReference.get().interruptActionsIfAny();

                            postDelayed(() -> {
                                if (activityWeakReference.get() != null) {
                                    PowerCommandsFactory powerCommandsFactory12 =
                                            activityWeakReference.get()
                                                    .getPowerCommandsFactory();

                                    if (powerCommandsFactory12.currentPowerState() !=
                                            PowerState.OFF) {
                                        powerCommandsFactory12.sendRequest
                                                (activityWeakReference.get(),
                                                        EToCMainHandler.this,
                                                        activityWeakReference.get());

                                    }
                                }
                            }, delayForPausing);
                        }
                    }, delayForPausing);

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

						postDelayed(() -> {
                            if (activityWeakReference.get() != null) {
                                PowerCommandsFactory powerCommandsFactory13 =
                                        activityWeakReference.get().getPowerCommandsFactory();

                                if (powerCommandsFactory13.currentPowerState() != PowerState
                                        .OFF) {
                                    powerCommandsFactory13.sendRequest(activityWeakReference.get
                                            (), EToCMainHandler.this, activityWeakReference
                                            .get());

                                }
                            }
                        }, powerCommandsFactory.currentCommand().getDelay());
                    }
					break;
				case PowerState.OFF_WAIT_FOR_COOLING:
					temperatureData = TemperatureData.parse(response);
					if (temperatureData.isCorrect()) {
						int curTemperature = temperatureData.getTemperature1();

						if (curTemperature <= EToCApplication.getInstance()
								.getBorderCoolingTemperature()) {
							activity.stopPullingForTemperature();
							powerCommandsFactory.moveStateToNext();

							postDelayed(() -> {
                                if (activityWeakReference.get() != null) {
                                    PowerCommandsFactory powerCommandsFactory14 =
                                            activityWeakReference.get()
                                                    .getPowerCommandsFactory();

                                    if (powerCommandsFactory14.currentPowerState() != PowerState
                                            .OFF) {
                                        powerCommandsFactory14.sendRequest(activityWeakReference
                                                .get(), EToCMainHandler.this,
                                                activityWeakReference.get());
                                    }
                                }
                            }, powerCommandsFactory.currentCommand().getDelay());
						}
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
							postDelayed(() -> {
                                if (activityWeakReference.get() != null) {
                                    PowerCommandsFactory powerCommandsFactory15 =
                                            activityWeakReference.get()
                                                    .getPowerCommandsFactory();
                                    if (powerCommandsFactory15.currentPowerState() != PowerState
                                            .OFF) {
                                        powerCommandsFactory15.sendRequest(activityWeakReference
                                                .get(), EToCMainHandler.this,
                                                activityWeakReference.get());

                                    }
                                }
                            }, powerCommandsFactory.currentCommand().getDelay());

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
	}
}
