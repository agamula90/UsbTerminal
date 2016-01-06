package com.proggroup.areasquarecalculator.utils;

import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.proggroup.areasquarecalculator.BaseLoadTask;
import com.proggroup.areasquarecalculator.R;
import com.proggroup.areasquarecalculator.data.AvgPoint;
import com.proggroup.areasquarecalculator.data.Constants;
import com.proggroup.areasquarecalculator.fragments.CalculatePpmSimpleFragment;
import com.proggroup.areasquarecalculator.tasks.CreateCalibrationCurveForAutoTask;
import com.proggroup.squarecalculations.CalculateUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AutoCalculations {

	private AutoCalculations() {
	}

	public static void calculateAuto(FrameLayout frameLayout, EditText editText) {
		calculateAuto(frameLayout, editText, true);
	}

	public static void calculateAuto(FrameLayout frameLayout, EditText editText, boolean
			is0Connect) {
		File calFolder = CalculatePpmSimpleFragment.findCalFolder(Constants.BASE_DIRECTORY);
		if (calFolder != null) {
			Context context = frameLayout.getContext();
			new CreateCalibrationCurveForAutoTask(new LoadPpmAvgValuesTask(null, frameLayout,
					context, editText), context, is0Connect).execute(calFolder);
		} else {
			Toast.makeText(frameLayout.getContext(), "Please make CAL directory to find ppm",
					Toast.LENGTH_SHORT).show();
		}
	}

	public static class LoadPpmAvgValuesTask extends BaseLoadTask {

		private final Context context;

		private final EditText editText;

		private final FrameLayout frameLayout;

		private String mUrl;

		private List<Float> ppmPoints = new ArrayList<>();

		private List<Float> avgSquarePoints = new ArrayList<>();

		private View progressBar;

		public LoadPpmAvgValuesTask(String mUrl, FrameLayout frameLayout, Context context,
				EditText editText) {
			super(mUrl);
			this.mUrl = mUrl;
			this.frameLayout = frameLayout;
			this.context = context;
			this.editText = editText;
		}

		@Override
		public FrameLayout getFrameLayout() {
			return frameLayout;
		}

		@Override
		public void setProgressBar(View progressBar) {
			this.progressBar = progressBar;
		}

		@Override
		public void setUrl(String mUrl) {
			super.setUrl(mUrl);
			this.mUrl = mUrl;
		}

		protected Boolean doInBackground(Void[] params) {
			Pair<List<Float>, List<Float>> res = CalculatePpmUtils.parseAvgValuesFromFile(mUrl,
					context);

			if (res == null) {
				return false;
			}

			ppmPoints.clear();
			ppmPoints.addAll(res.first);
			avgSquarePoints.clear();
			avgSquarePoints.addAll(res.second);
			return true;
		}

		@Override
		protected void onPostExecute(Boolean aVoid) {
			if (!aVoid) {
				Toast.makeText(context, "You select wrong file", Toast.LENGTH_LONG).show();
				return;
			}

			CreateCalibrationCurveForAutoTask.detachProgress(progressBar);

			File mesFile = CalculatePpmSimpleFragment.findMesFile(Constants.BASE_DIRECTORY
					.getParentFile());
			if (mesFile != null && CalculatePpmSimpleFragment.findMesFile(mesFile) != null) {
				mesFile = CalculatePpmSimpleFragment.findMesFile(mesFile);
				File mesFiles[] = mesFile.listFiles();
				File newestCalFile1 = null, newestCalFile2 = null, newestCalFile3 = null;
				for (File f : mesFiles) {
					if (!f.isDirectory()) {
						if (newestCalFile1 == null) {
							newestCalFile1 = f;
						} else if (newestCalFile2 == null) {
							if (newestCalFile1.lastModified() > f.lastModified()) {
								newestCalFile2 = newestCalFile1;
								newestCalFile1 = f;
							} else {
								newestCalFile2 = f;
							}
						} else if (newestCalFile3 == null) {
							if (newestCalFile2.lastModified() < f.lastModified()) {
								newestCalFile3 = f;
							} else if (newestCalFile1.lastModified() > f.lastModified()) {
								newestCalFile3 = newestCalFile2;
								newestCalFile2 = newestCalFile1;
								newestCalFile1 = f;
							} else {
								newestCalFile3 = newestCalFile2;
								newestCalFile2 = f;
							}
						} else if (newestCalFile3.lastModified() > f.lastModified()) {
							if (newestCalFile2.lastModified() > f.lastModified()) {
								newestCalFile3 = f;
							} else if (newestCalFile1.lastModified() > f.lastModified()) {
								newestCalFile3 = newestCalFile2;
								newestCalFile2 = f;
							} else {
								newestCalFile3 = newestCalFile2;
								newestCalFile2 = newestCalFile1;
								newestCalFile1 = f;
							}
						}
					}
				}

				if (newestCalFile1 != null && newestCalFile2 != null && newestCalFile3 != null) {
					float square1 = CalculateUtils.calculateSquare(newestCalFile1);
					if (square1 == -1) {
						Toast.makeText(context, "Wrong files for calculating", Toast.LENGTH_LONG)
								.show();
						return;
					} else {
						float square2 = CalculateUtils.calculateSquare(newestCalFile2);
						if (square2 == -1) {
							Toast.makeText(context, "Wrong files for calculating", Toast
									.LENGTH_LONG).show();
							return;
						} else {
							float square3 = CalculateUtils.calculateSquare(newestCalFile3);
							if (square3 == -1) {
								Toast.makeText(context, "Wrong files for calculating", Toast
										.LENGTH_LONG).show();
								return;
							} else {
								AvgPoint mAutoAvgPoint = new AvgPoint(Arrays.asList(new
										Float[]{square1, square2, square3}));
								float avgValueY = mAutoAvgPoint.avg();
								float value;
								try {

									value = CalculatePpmSimpleFragment.findPpmBySquare(avgValueY,
											ppmPoints, avgSquarePoints);
								} catch (Exception e) {
									value = -1;
								}

								if (value == -1) {
									Context activity = frameLayout.getContext();
									Toast.makeText(activity, activity.getString(R.string
											.wrong_data), Toast.LENGTH_LONG).show();
								} else {
									editText.setText(FloatFormatter.format(value));
								}
							}
						}
					}
				}
			} else {
				Toast.makeText(context, "Please make MES directory to find ppm", Toast
						.LENGTH_LONG).show();
			}
		}
	}
}
