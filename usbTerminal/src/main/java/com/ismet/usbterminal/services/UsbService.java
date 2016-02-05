package com.ismet.usbterminal.services;


import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.IBinder;

import com.felhr.usbserial.CDCSerialDevice;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.ismet.usbterminal.UsbServiceWritable;

import java.util.HashMap;
import java.util.Map;

/**
 * It's service will be binding into to write data.
 */
public class UsbService extends Service implements UsbServiceWritable {

	public static final String ACTION_USB_READY = "com.ismet.usbservice.USB_READY";

	public static final String ACTION_USB_NOT_SUPPORTED = "com.ismet.usbservice.USB_NOT_SUPPORTED";

	public static final String ACTION_NO_USB = "com.ismet.usbservice.NO_USB";

	public static final String ACTION_USB_PERMISSION_GRANTED = "com.ismet.usbservice" + "" +
			".USB_PERMISSION_GRANTED";

	public static final String ACTION_USB_PERMISSION_NOT_GRANTED = "com.ismet.usbservice" + "" +
			".USB_PERMISSION_NOT_GRANTED";

	public static final String ACTION_USB_DISCONNECTED = "com.ismet.usbservice.USB_DISCONNECTED";

	public static final String ACTION_CDC_DRIVER_NOT_WORKING = "com.ismet.usbservice" + "" +
			".ACTION_CDC_DRIVER_NOT_WORKING";

	public static final String ACTION_USB_DEVICE_NOT_WORKING = "com.ismet.usbservice" + "" +
			".ACTION_USB_DEVICE_NOT_WORKING";

	public static final String ACTION_DATA_RECEIVED = "com.ismet.usbservice.ACTION_DATA_RECEIVED";

	public static final String DATA_RECEIVED = "data_from_usb";

	private static final String ACTION_USB_PERMISSION = "com.ismet.usbservise.USB_PERMISSION";

	private static final int BAUD_RATE = 9600; // BaudRate. Change this value if you need

	private UsbSerialDevice mSerialPort;

	private Context mContext;

	private UsbManager mUsbManager;

	private UsbDevice mDevice;

	private UsbDeviceConnection mConnection;

	private boolean mSerialPortConnected;

	/*
	 * Different notifications from OS will be received here (USB attached, detached, permission
	 * responses...)
	 * About BroadcastReceiver: http://developer.android
	 * .com/reference/android/content/BroadcastReceiver.html
	 */
	private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent incomeIntent) {

			String action = incomeIntent.getAction();

			if (action.equals(ACTION_USB_PERMISSION)) {
				boolean granted = incomeIntent.getExtras().getBoolean(UsbManager
						.EXTRA_PERMISSION_GRANTED);

				if (granted)
				// User accepted our USB mConnection. Try to open the device as a serial port
				{
					Intent intent = new Intent(ACTION_USB_PERMISSION_GRANTED);
					context.sendBroadcast(intent);

					mConnection = mUsbManager.openDevice(mDevice);
					mSerialPortConnected = true;
					new ConnectionThread().start();
				} else // User not accepted our USB mConnection. Send an Intent to the Main Activity
				{
					Intent intent = new Intent(ACTION_USB_PERMISSION_NOT_GRANTED);
					context.sendBroadcast(intent);
				}
			} else if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
				if (!mSerialPortConnected) {
					findSerialPortDevice();
				}
			} else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
				// Usb device was disconnected. send an intent to the Main Activity
				disconnect();

                Intent intent = new Intent(ACTION_USB_DISCONNECTED);
                context.sendBroadcast(intent);
            }
		}
	};

	/*
	 *  Data received from serial port will be received here. Just populate onReceivedData with
	 *  your code
	 *  In this particular example. byte stream is converted to String and send to UI thread to
	 *  be treated there.
	 */
	private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback
			() {

		@Override
		public void onReceivedData(byte[] arr) {
			Intent dataReadFromUsb = new Intent(ACTION_DATA_RECEIVED);
			dataReadFromUsb.putExtra(DATA_RECEIVED, arr);

			getApplicationContext().sendBroadcast(dataReadFromUsb);
		}
	};

	/*
	 * onCreate will be executed when service is started. It configures an IntentFilter to listen
	  * for
	 * incoming Intents (USB ATTACHED, USB DETACHED...) and it tries to open a serial port.
	 */
	@Override
	public void onCreate() {
		this.mContext = this;
		mSerialPortConnected = false;
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(usbReceiver, filter);
	}

	/* MUST READ about services
	 * http://developer.android.com/guide/components/services.html
	 * http://developer.android.com/guide/components/bound-services.html
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return new UsbBinder(this);
	}

	public static class UsbBinder extends Binder {
		private final UsbService usbService;

		public UsbBinder(UsbService usbService) {
			this.usbService = usbService;
		}

		public UsbServiceWritable getApi() {
			return usbService;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		disconnect();

		unregisterReceiver(usbReceiver);
	}

	private void findSerialPortDevice() {
		// This snippet will try to open the first encountered usb device connected, excluding
		// usb root hubs
		HashMap<String, UsbDevice> usbDevices = mUsbManager.getDeviceList();
		if (!usbDevices.isEmpty()) {
			boolean keep = false;
			for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
				mDevice = entry.getValue();
				int deviceVID = mDevice.getVendorId();
				int devicePID = mDevice.getProductId();

				if ((deviceVID == 0x1a86) && (devicePID == 0x7523)) {
					keep = true;
					requestUserPermission();
					break;
				}

				//				if(deviceVID != 0x1d6b || (devicePID != 0x0001 || devicePID !=
				// 0x0002 || devicePID
				// != 0x0003))
				//				{
				//					// There is a device connected to our Android device. Try to
				// open it as a
				// Serial Port.
				//					requestUserPermission();
				//					keep = true;
				//				}

				//				if(deviceVID != 0x1d6b || (devicePID != 0x0001 || devicePID !=
				// 0x0002 || devicePID
				// != 0x0003))
				//				{
				//					// There is a device connected to our Android device. Try to
				// open it as a
				// Serial Port.
				//					requestUserPermission();
				//					keep = false;
				//				}else
				//				{
				//					mConnection = null;
				//					device = null;
				//				}
				//
				//				if(!keep)
				//					break;
			}
			if (!keep) {
				// There is no USB devices connected (but usb host were listed). Send an intent
				// to MainActivity.
				Intent intent = new Intent(ACTION_NO_USB);
				sendBroadcast(intent);
			}
		} else {
			// There is no USB devices connected. Send an intent to MainActivity
			Intent intent = new Intent(ACTION_NO_USB);
			sendBroadcast(intent);
		}
	}

	/*
	 * Request user permission. The response will be received in the BroadcastReceiver
	 */
	private void requestUserPermission() {
		PendingIntent mPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent
				(ACTION_USB_PERMISSION), 0);
		mUsbManager.requestPermission(mDevice, mPendingIntent);
	}

	@Override
	public void writeToUsb(byte[] bytes) {
		if(mSerialPort != null) {
			mSerialPort.write(bytes);
		}
	}

	@Override
	public UsbDevice searchForUsbDevice() {
		mDevice = null;
		findSerialPortDevice();
		return mDevice;
	}

	@Override
	public void disconnect() {
        mSerialPortConnected = false;
        if (mSerialPort != null) {
            try {
                mSerialPort.close();
                mSerialPort = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (mConnection != null) {
            mConnection.close();
            mConnection = null;
        }
	}

	/*
         * A simple thread to open a serial port.
         * Although it should be a fast operation. moving usb operations away from UI thread is a
         * good thing.
         */
	private class ConnectionThread extends Thread {

		@Override
		public void run() {
			mSerialPort = UsbSerialDevice.createUsbSerialDevice(mDevice, mConnection);
			if (mSerialPort != null) {
				if (mSerialPort.open()) {
					mSerialPort.setBaudRate(BAUD_RATE);
					mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
					mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
					mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
					mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
					mSerialPort.read(mCallback);

					// Everything went as expected. Send an intent to MainActivity
					Intent intent = new Intent(ACTION_USB_READY);
					mContext.sendBroadcast(intent);
				} else {
					// Serial port could not be opened, maybe an I/O error or if CDC driver was
					// chosen, it does not really fit
					// Send an Intent to Main Activity
					if (mSerialPort instanceof CDCSerialDevice) {
						Intent intent = new Intent(ACTION_CDC_DRIVER_NOT_WORKING);
						mContext.sendBroadcast(intent);
					} else {
						Intent intent = new Intent(ACTION_USB_DEVICE_NOT_WORKING);
						mContext.sendBroadcast(intent);
					}
					mSerialPort.close();
					mSerialPort = null;
				}
			} else {
				// No driver for given device, even generic CDC driver could not be loaded
				Intent intent = new Intent(ACTION_USB_NOT_SUPPORTED);
				mContext.sendBroadcast(intent);
			}
		}
	}

}
