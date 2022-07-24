package com.ismet.usbterminal.mainscreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import com.ismet.usbterminal.MainActivity;
import com.ismet.usbterminal.services.UsbService;
import com.proggroup.areasquarecalculator.utils.ToastUtils;

import java.lang.ref.WeakReference;

public class EToCMainUsbReceiver extends BroadcastReceiver {

    private final WeakReference<MainActivity> weakActivity;

    public EToCMainUsbReceiver(MainActivity activity) {
        this.weakActivity = new WeakReference<>(activity);
    }

    public EToCMainUsbReceiver(EToCMainActivity activity) {
        this.weakActivity = new WeakReference<>(null);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(UsbService.ACTION_DATA_RECEIVED)) {
            if (weakActivity.get() != null) {
                MainActivity activity = weakActivity.get();
                activity.sendMessageWithUsbDataReceived(intent.getByteArrayExtra(UsbService
                        .DATA_RECEIVED));
            }
            return;
        } else if(action.equals(EToCMainHandler.USB_DATA_READY)) {
            if (weakActivity.get() != null) {
                MainActivity activity = weakActivity.get();
                boolean isToast = intent.getBooleanExtra(EToCMainHandler.IS_TOAST, false);

                String data = intent.getStringExtra(EToCMainHandler.DATA_EXTRA);

                if(isToast) {
                    activity.sendMessageForToast(data);
                } else {
                    activity.sendMessageWithUsbDataReady(data);
                }
            }
            return;
        }

        final String toastMessage;
        final Boolean isUsbConnected;
        Boolean startService = null;
        boolean usbPermissionDiscarded = false;

        if (action.equals(UsbService.ACTION_USB_PERMISSION_GRANTED))
        // USB PERMISSION GRANTED
        {
            isUsbConnected = true;
            toastMessage = "USB Ready";
        } else if (action.equals(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED))
        // USB PERMISSION NOT GRANTED
        {
            isUsbConnected = false;
            toastMessage = "USB Permission not granted";
            usbPermissionDiscarded = true;
        } else if (action.equals(UsbService.ACTION_NO_USB))
        // NO USB CONNECTED
        {
            isUsbConnected = false;
            toastMessage = "No USB connected";
        } else if (action.equals(UsbService.ACTION_USB_DISCONNECTED)) // USB
        // DISCONNECTED
        {
            isUsbConnected = false;
            toastMessage = "USB disconnected";
            startService = false;
            // finish();
        } else if (action.equals(UsbService.ACTION_USB_NOT_SUPPORTED))
        // USB NOT SUPPORTED
        {
            isUsbConnected = false;
            toastMessage = "USB device not supported";
        } else if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED))
        // USB ATTACHED TO DEVICE
        {
            isUsbConnected = null;
            toastMessage = "USB Device Attached";
            startService = true;
        } else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED))
        // USB DETACHED TO DEVICE
        {
            isUsbConnected = false;
            startService = false;
            toastMessage = "USB Device Detached";
            // finish();
        } else {
            toastMessage = null;
            isUsbConnected = null;
        }

        handleUsbIntent(toastMessage, isUsbConnected, startService, usbPermissionDiscarded);
    }

    private void handleUsbIntent(String toastMessage, Boolean
            isUsbConnected, Boolean startService, boolean permissionDiscarded) {

        if (weakActivity.get() != null) {
            synchronized (weakActivity.get()) {
                MainActivity activity = weakActivity.get();

                if (isUsbConnected != null) {
                    activity.setUsbConnected(isUsbConnected);
                    activity.invalidateOptionsMenu();
                }

                if (toastMessage != null) {
                    Toast customToast = Toast.makeText(activity, toastMessage, Toast.LENGTH_LONG);
                    ToastUtils.wrap(customToast);
                    customToast.show();
                }

                if (startService != null) {
                    if (startService) {
                        activity.startService(new Intent(activity, UsbService.class));
                    } else {
                        activity.stopService(new Intent(activity, UsbService.class));
                    }
                }

                if (permissionDiscarded) {
                    activity.finish();
                }
            }
        }
    }
}
