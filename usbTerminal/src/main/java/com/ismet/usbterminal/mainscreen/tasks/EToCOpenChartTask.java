package com.ismet.usbterminal.mainscreen.tasks;

import android.os.AsyncTask;
import android.widget.Toast;

import com.ismet.usbterminal.MainActivity;
import com.ismet.usbterminal.mainscreen.EToCMainActivity;
import com.proggroup.areasquarecalculator.utils.ToastUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class EToCOpenChartTask extends AsyncTask<String, String, String> {

	private final WeakReference<MainActivity> weakActivity;

	public EToCOpenChartTask(EToCMainActivity activity) {
		this.weakActivity = new WeakReference<>(null);
	}

    public EToCOpenChartTask(MainActivity activity) {
        this.weakActivity = new WeakReference<>(activity);
    }

	@Override
	protected String doInBackground(String... params) {
		String filePath = params[0];

		File file = new File(filePath);
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;

			int c = 1;
			while (br.readLine() != null) {
				c++;
			}
			br.close();

			if (filePath.contains("R1")) {
				c = 1;
			} else if (filePath.contains("R2")) {
				//
			} else if (filePath.contains("R3")) {
				c = 2 * c;
			}

			br = new BufferedReader(new FileReader(file));

			while ((line = br.readLine()) != null) {
				if (!line.equals("")) {
					String[] arr = line.split(",");
					double co2 = Double.parseDouble(arr[1]);

					String progressValue = c + "," + co2;
					c++;

					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch
						// block
						e.printStackTrace();
					}
					publishProgress(progressValue);

				}
			}
			br.close();

		} catch (IOException e) {
			// You'll need to add proper error handling
			// here
			e.printStackTrace();
		}

		return null;
	}

	@Override
	protected void onProgressUpdate(String... values) {
		super.onProgressUpdate(values);
		if(weakActivity.get() != null) {
			weakActivity.get().sendOpenChartDataToHandler(values[0]);
		}
	}

	@Override
	protected void onPostExecute(String s) {
		super.onPostExecute(s);
		if(weakActivity.get() != null) {
			Toast toast = Toast.makeText(weakActivity.get(), "File reading done", Toast
					.LENGTH_SHORT);
            ToastUtils.wrap(toast);
			toast.show();
		}
	}
}
