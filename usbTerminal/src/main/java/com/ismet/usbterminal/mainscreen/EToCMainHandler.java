package com.ismet.usbterminal.mainscreen;

import android.os.Handler;
import android.os.Message;

import com.ismet.usbterminal.EToCApplication;
import com.ismet.usbterminal.MainActivity;
import com.ismet.usbterminal.data.PowerState;
import com.ismet.usbterminal.mainscreen.powercommands.PowerCommandsFactory;

import java.lang.ref.WeakReference;

import kotlin.Deprecated;

@Deprecated(message = "Use MainViewModel instead")
public class EToCMainHandler extends Handler {

	public static final String USB_DATA_READY = "com.ismet.usbservice.USB_DATA_READY";

	public static final String DATA_EXTRA = "data_extra";

    public static final String IS_TOAST = "is_toast";

    //TODO not used
	public static final int MESSAGE_USB_DATA_RECEIVED = 0;

    //TODO not used
	public static final int MESSAGE_USB_DATA_READY = 1;

    //TODO not used
	public static final int MESSAGE_OPEN_CHART = 2;

    //TODO not used
	public static final int MESSAGE_RESUME_AUTO_PULLING = 3;

    //TODO not used
	public static final int MESSAGE_INTERRUPT_ACTIONS = 4;

    //TODO not used
	public static final int MESSAGE_PAUSE_AUTO_PULLING = 5;

    //TODO not used
    public static final int MESSAGE_SIMULATE_RESPONSE = 8;

    //TODO not used
	public static final int MESSAGE_SHOW_TOAST = 9;

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
            switch (msg.what) {
				case MESSAGE_USB_DATA_RECEIVED:
                    //Use main activity instead
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
					activity.handleResponse( "");
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
						activity.handleResponse(sVal);
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
}
