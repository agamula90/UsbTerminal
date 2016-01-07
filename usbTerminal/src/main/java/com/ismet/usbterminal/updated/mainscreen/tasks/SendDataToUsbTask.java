package com.ismet.usbterminal.updated.mainscreen.tasks;

import android.os.AsyncTask;
import android.os.Message;

import com.ismet.usbterminal.updated.mainscreen.EToCMainActivity;

import java.lang.ref.WeakReference;
import java.util.List;

public class SendDataToUsbTask extends AsyncTask<Long, Integer, String> {

	private final List<String> simpleCommands;

	private final List<String> loopCommands;

	private final WeakReference<EToCMainActivity> weakActivity;

	public SendDataToUsbTask(List<String> simpleCommands, List<String> loopCommands,
			EToCMainActivity activity) {
		this.simpleCommands = simpleCommands;
		this.loopCommands = loopCommands;
		this.weakActivity = new WeakReference<>(activity);
	}

	@Override
	protected String doInBackground(Long... params) {
		Long future = params[0];
		Long delay = params[1];


		if(weakActivity.get() != null) {
			boolean isauto = weakActivity.get().getPrefs().getBoolean("isauto", false);
			if (isauto) {
				for (int l = 0; l < 3; l++) {
					processChart(future, delay);
				}
			} else {
				processChart(future, delay);
			}
		}

		return null;
	}

	public void processChart(long future, long delay) {
		for (int i = 0; i < simpleCommands.size(); i++) {
			if (simpleCommands.get(i).contains("delay")) {
				int delayC = Integer.parseInt(simpleCommands.get(i).replace("delay", "").trim
						());
				try {
					Thread.sleep(delayC);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				Message msg = new Message();
				msg.what = 1;
				msg.obj = simpleCommands.get(i);
				mHandler.sendMessage(msg);
			}
		}

		isTimerRunning = true;
		//int i = 0;
		long len = future / delay;
		long count = 0;

		//boolean isauto = prefs.getBoolean("isauto", false);

		//			if(isauto){
		//				len = 3 * len;
		//			}

		while (count < len) {
			readingCount = readingCount + 1;


			String cmd = loopCommands.get(0);
			Message msg = new Message();
			msg.what = 1;
			msg.obj = cmd;
			mHandler.sendMessage(msg);
			//
			try {
				long half_delay = delay / 2;
				Thread.sleep(half_delay);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//
			cmd = loopCommands.get(1);
			msg = new Message();
			msg.what = 1;
			msg.obj = cmd;
			mHandler.sendMessage(msg);
			//
			try {
				long half_delay = delay / 2;
				Thread.sleep(half_delay);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			//				byte [] arr = new byte[]{(byte) 0xFE,0x44,0x11,0x22,0x33,0x44,
			// 0x55};
			//				Message msg = new Message();
			//				msg.what = 0;
			//				msg.obj = arr;
			//				EToCMainActivity.mHandler.sendMessage(msg);

			//future = future - delay;
			//				if(i == 0){
			//					i = 1;
			//				}else{
			//					i = 0;
			//				}

			//				try {
			//					Thread.sleep(delay);
			//				} catch (InterruptedException e) {
			//					// TODO Auto-generated catch block
			//					e.printStackTrace();
			//				}

			count++;
		}
	}
}
