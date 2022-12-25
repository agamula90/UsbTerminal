package com.proggroup.areasquarecalculator.fragments;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.proggroup.areasquarecalculator.BaseLoadTask;
import com.proggroup.areasquarecalculator.InterpolationCalculatorApp;
import com.proggroup.areasquarecalculator.R;
import com.proggroup.areasquarecalculator.adapters.CalculatePpmSimpleAdapter;
import com.proggroup.areasquarecalculator.api.LibraryContentAttachable;
import com.proggroup.areasquarecalculator.data.AvgPoint;
import com.proggroup.areasquarecalculator.data.Constants;
import com.proggroup.areasquarecalculator.data.PrefConstants;
import com.proggroup.areasquarecalculator.data.Project;
import com.proggroup.areasquarecalculator.db.AvgPointHelper;
import com.proggroup.areasquarecalculator.db.PointHelper;
import com.proggroup.areasquarecalculator.db.ProjectHelper;
import com.proggroup.areasquarecalculator.db.SQLiteHelper;
import com.proggroup.areasquarecalculator.db.SquarePointHelper;
import com.proggroup.areasquarecalculator.tasks.CreateCalibrationCurveForAutoTask;
import com.proggroup.areasquarecalculator.utils.CalculatePpmUtils;
import com.proggroup.areasquarecalculator.utils.FloatFormatter;
import com.proggroup.areasquarecalculator.utils.IntentFolderWrapUtils;
import com.proggroup.areasquarecalculator.utils.ToastUtils;
import com.proggroup.CalculateExtensionsKt;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import fr.xgouchet.FileDialog;
import fr.xgouchet.SelectionMode;

public class CalculatePpmSimpleFragment extends Fragment implements CalculatePpmSimpleAdapter
		.OnInfoFilledListener {

	public static final String FIRST_TEXT_TAG = "first_text";

	public static final String SECOND_TEXT_TAG = "second_text";

	public static final String THIRD_TEXT_TAG = "third_texxt";

	public static final String FOURTH_TEXT_TAG = "fourth_text";

	public static final String IS_SAVED = "is_saved";

	/**
	 * Request code for start load ppm curve file dialog.
	 */
	private static final int LOAD_PPM_AVG_VALUES_REQUEST_CODE = 103;

	/**
	 * Request code for start save ppm curve file dialog.
	 */
	private static final int SAVE_PPM_AVG_VALUES = 104;

	private static final int MES_SELECT_FOLDER = 105;

	private static Bundle sBundle = new Bundle();

	private GridView mGridView;

	private View calculatePpmLayout, calculatePpmLayoutLoaded;

	private TextView resultPpm, resultPpmLoaded;

	private EditText avgValue, avgValueLoaded;

	private CalculatePpmSimpleAdapter adapter;

	private View calculatePpmSimple, calculatePpmSimpleLoaded, calculatePpmAuto;

	private View graph, graph1;

	private CheckBox connect0;

	private Button btnAddRow;

	private View buttonsLayout;

	private View loadPpmCurve, savePpmCurve;

	private List<Float> ppmPoints, avgSquarePoints;

	private List<Float> ppmAuto, avgSquaresAuto;

	private AvgPoint mAutoAvgPoint;

	private LinearLayout avgPointsLayout;

	private View resetDatabase;

	private ProjectHelper mProjectHelper;

	private AvgPointHelper mAvgPointHelper;

	private SquarePointHelper mSquarePointHelper;

	private PointHelper mPointHelper;

	private boolean mCalculatePpmAvg;

	private String mUrlWhenAutoLoading;

	private View mClearRow1, mClearRow2;

	private View mesSelectFolder;

	private boolean mForceSearchOfMesFolder;

	private static File findNameFolder(File file, final String name) {
		File files[] = file.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				File fileName = new File(dir, filename);
				return fileName.isDirectory() || filename.contains(name);
			}
		});

		if (files != null) {
			for (File f : files) {
				if (f.isDirectory() && f.getName().contains(name)) {
					return f;
				}
			}

			for (File f : files) {
				if (f.isDirectory()) {
					File calFolder = findNameFolder(f, name);
					if (calFolder != null) {
						return calFolder;
					}
				}
			}
		}

		return null;
	}

	public static File findCalFolder(File file) {
		return findNameFolder(file, "CAL");
	}

	public static File findMesFile(File file) {
		return findNameFolder(file, "MES");
	}

	public static File findNewestFolder(File searchInFolder, String stringInFolderName) {
		File filesInside[] = searchInFolder.listFiles();

		File newestFile = null;

		for (int i = 0; i < filesInside.length; i++) {
			if(newestFile == null) {
				if(filesInside[i].isDirectory() && filesInside[i].getName().contains
						(stringInFolderName)) {
					newestFile = filesInside[i];
				}
			} else if(filesInside[i].isDirectory() && filesInside[i].getName().contains
					(stringInFolderName) && filesInside[i].lastModified() > newestFile
					.lastModified()) {
				newestFile = filesInside[i];
			}
		}

		return newestFile;
	}

	/**
	 * Search ppm value from square and saved data of ppmPoints and avgSquarePoints.
	 *
	 * @param square          Square, ppm for which is searching.
	 * @param ppmPoints       Ppm values, which will be used for approximation.
	 * @param avgSquarePoints Average square values, which will be used for approximation.
	 * @return Searched ppm value.
	 */
	public static float findPpmBySquare(float square, List<Float> ppmPoints, List<Float>
			avgSquarePoints) {
		for (int i = 0; i < avgSquarePoints.size() - 1; i++) {
			//check whether the point belongs to the line
			if (square >= avgSquarePoints.get(i) && square <= avgSquarePoints.get(i + 1)) {
				//getting x value
				float x1 = ppmPoints.get(i);
				float x2 = ppmPoints.get(i + 1);
				float y1 = avgSquarePoints.get(i);
				float y2 = avgSquarePoints.get(i + 1);

				return ((square - y1) * (x2 - x1)) / (y2 - y1) + x1;
			}
		}

		return -1;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
			savedInstanceState) {
		return inflater.inflate(R.layout.fragment_calculate_ppm, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mGridView = (GridView) view.findViewById(R.id.grid);

		graph = view.findViewById(R.id.graph);

		graph.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Activity activity = getActivity();

				List<Float> ppmPoints = new ArrayList<>();
				List<Float> avgSquarePoints = new ArrayList<>();
				boolean isAttached = attachAdapterToDatabase();

				if (isAttached) {
					fillPpmAndSquaresFromDatabase(ppmPoints, avgSquarePoints);

					ArrayList<String> ppmStrings = new ArrayList<>(ppmPoints.size());
					ArrayList<String> squareStrings = new ArrayList<>(avgSquarePoints.size());

					if (connect0.isChecked()) {
						ppmPoints.add(0, 0f);
						avgSquarePoints.add(0, 0f);
					}

					for (Float ppm : ppmPoints) {
						ppmStrings.add(ppm.intValue() + "");
					}
					for (Float square : avgSquarePoints) {
						squareStrings.add(FloatFormatter.format(square));
					}

					LibraryContentAttachable libraryContentAttachable = activity instanceof
							LibraryContentAttachable ? (LibraryContentAttachable) activity : null;

					if (libraryContentAttachable != null) {
						FragmentManager fragmentManager = libraryContentAttachable
								.getSupportFragmentManager();

						int fragmentContainerId = libraryContentAttachable
								.getFragmentContainerId();

						FragmentTransaction transaction = fragmentManager.beginTransaction();
						transaction.replace(fragmentContainerId, CurveFragment.newInstance
								(ppmStrings, squareStrings));
						transaction.addToBackStack(null);
						transaction.commit();
					}
				} else {
					Toast toast = Toast.makeText(getActivity(), getString(R.string.input_ppm_first), Toast
							.LENGTH_LONG);
                    ToastUtils.wrap(toast);
					toast.show();
				}
			}
		});

		graph1 = view.findViewById(R.id.graph1);

		graph1.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Activity activity = getActivity();

				List<Float> ppmPoints = new ArrayList<>();
				List<Float> avgSquarePoints = new ArrayList<>();
				ppmPoints.addAll(CalculatePpmSimpleFragment.this.ppmPoints);
				avgSquarePoints.addAll(CalculatePpmSimpleFragment.this.avgSquarePoints);

				ArrayList<String> ppmStrings = new ArrayList<>(ppmPoints.size());
				ArrayList<String> squareStrings = new ArrayList<>(avgSquarePoints.size());

				for (Float ppm : ppmPoints) {
					ppmStrings.add(ppm.intValue() + "");
				}
				for (Float square : avgSquarePoints) {
					squareStrings.add(FloatFormatter.format(square));
				}

				LibraryContentAttachable libraryContentAttachable = activity instanceof
						LibraryContentAttachable ? (LibraryContentAttachable) activity : null;

				if (libraryContentAttachable != null) {
					FragmentManager fragmentManager = libraryContentAttachable
							.getSupportFragmentManager();

					int fragmentContainerId = libraryContentAttachable.getFragmentContainerId();

					FragmentTransaction transaction = fragmentManager.beginTransaction();
					transaction.replace(fragmentContainerId, CurveFragment.newInstance(ppmStrings,
							squareStrings));
					transaction.addToBackStack(null);
					transaction.commit();
				}
			}
		});

		connect0 = (CheckBox) view.findViewById(R.id.save_0_ppm);

		calculatePpmLayout = view.findViewById(R.id.calculate_ppm_layout);

		calculatePpmLayoutLoaded = view.findViewById(R.id.calculate_ppm_layout_loaded);

		loadPpmCurve = view.findViewById(R.id.load_ppm_curve);

		resetDatabase = view.findViewById(R.id.simple_ppm_btn_reset);

		savePpmCurve = view.findViewById(R.id.save_ppm_curve);

		avgPointsLayout = (LinearLayout) view.findViewById(R.id.avg_points);

		avgPointsLayout.removeAllViews();

		File calFolder = findCalFolder(Constants.BASE_DIRECTORY);

		ppmPoints = new ArrayList<>();
		avgSquarePoints = new ArrayList<>();
		if (calFolder != null) {
			if (!sBundle.getBoolean(IS_SAVED, false)) {
				mCalculatePpmAvg = true;
				new CreateCalibrationCurveForAutoTask(new LoadPpmAvgValuesTask(null), getActivity
						(), true).execute(calFolder);
			}
		} else {
			Toast toast = Toast.makeText(getActivity(), "Please make CAL directory to find ppm", Toast
					.LENGTH_SHORT);
            ToastUtils.wrap(toast);
			toast.show();
		}

		TextView tv = new TextView(getActivity());
		tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
				.edit_text_size_default));
		tv.setText("");
		tv.setTextColor(Color.WHITE);
		avgPointsLayout.addView(tv);
		graph1.setVisibility(View.GONE);

		InterpolationCalculatorApp interpolationCalculatorApp = InterpolationCalculatorApp
				.getInstance();
		if (interpolationCalculatorApp.getPpmPoints() != null) {
			ppmPoints = interpolationCalculatorApp.getPpmPoints();
			avgSquarePoints = interpolationCalculatorApp.getAvgSquarePoints();
			fillAvgPointsLayout();
			graph1.setVisibility(View.VISIBLE);
		}

		calculatePpmSimpleLoaded = view.findViewById(R.id.calculate_ppm_loaded);

		calculatePpmSimpleLoaded.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (avgValueLoaded.getText().toString().isEmpty()) {
					Activity activity = getActivity();
					Toast toast = Toast.makeText(activity, activity.getString(R.string.input_avg_value), Toast
							.LENGTH_LONG);
                    ToastUtils.wrap(toast);
					toast.show();
					return;
				}
				float avgValueY = Float.parseFloat(avgValueLoaded.getText().toString());
				float value;
				try {
					List<Float> ppmPoints = new ArrayList<>();
					List<Float> avgSquarePoints = new ArrayList<>();
					//ppmPoints.add(0f);
					//avgSquarePoints.add(0f);
					ppmPoints.addAll(CalculatePpmSimpleFragment.this.ppmPoints);
					avgSquarePoints.addAll(CalculatePpmSimpleFragment.this.avgSquarePoints);
					value = findPpmBySquare(avgValueY, ppmPoints, avgSquarePoints);
				} catch (Exception e) {
					value = -1;
				}

				if (value == -1) {
					Activity activity = getActivity();
					Toast toast = Toast.makeText(activity, activity.getString(R.string.wrong_data), Toast
							.LENGTH_LONG);
                    ToastUtils.wrap(toast);
					toast.show();
				} else {
					resultPpmLoaded.setText(FloatFormatter.format(value));
				}
			}
		});

		calculatePpmAuto = view.findViewById(R.id.calculate_ppm_auto);

		calculatePpmAuto.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mAutoAvgPoint == null) {
					Toast toast = Toast.makeText(getActivity(), "Average point not filled", Toast
							.LENGTH_LONG);
                    ToastUtils.wrap(toast);
							toast.show();
					return;
				} else {
					avgValueLoaded.setText(FloatFormatter.format(mAutoAvgPoint.avg()));
					ppmAuto = new ArrayList<Float>(ppmPoints);
					avgSquaresAuto = new ArrayList<Float>(avgSquarePoints);
				}

				float avgValueY = Float.parseFloat(avgValueLoaded.getText().toString());
				float value;
				try {
					List<Float> ppmPoints = new ArrayList<>();
					List<Float> avgSquarePoints = new ArrayList<>();
					//ppmPoints.add(0f);
					//avgSquarePoints.add(0f);
					ppmPoints.addAll(CalculatePpmSimpleFragment.this.ppmAuto);
					avgSquarePoints.addAll(CalculatePpmSimpleFragment.this.avgSquaresAuto);
					value = findPpmBySquare(avgValueY, ppmPoints, avgSquarePoints);
				} catch (Exception e) {
					value = -1;
				}

				if (value == -1) {
					Activity activity = getActivity();
					Toast toast = Toast.makeText(activity, activity.getString(R.string.wrong_data), Toast
							.LENGTH_LONG);
                    ToastUtils.wrap(toast);
					toast.show();
				} else {
					resultPpmLoaded.setText(FloatFormatter.format(value));
				}
			}
		});

		calculatePpmSimple = view.findViewById(R.id.calculate_ppm);
		calculatePpmSimple.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (avgValue.getText().toString().isEmpty()) {
					Activity activity = getActivity();
					Toast toast = Toast.makeText(activity, activity.getString(R.string.input_avg_value), Toast
							.LENGTH_LONG);
                    ToastUtils.wrap(toast);
					toast.show();
					return;
				}
				float avgValueY = Float.parseFloat(avgValue.getText().toString());
				float value;
				try {
					List<Float> ppmPoints = new ArrayList<>();
					List<Float> avgSquarePoints = new ArrayList<>();
					boolean isAttached = attachAdapterToDatabase();

					if (isAttached) {
						fillPpmAndSquaresFromDatabase(ppmPoints, avgSquarePoints);

						if (connect0.isChecked()) {
							ppmPoints.add(0, 0f);
							avgSquarePoints.add(0, 0f);
						}

						value = findPpmBySquare(avgValueY, ppmPoints, avgSquarePoints);
					} else {
						throw new Exception();
					}
				} catch (Exception e) {
					value = -1;
				}

				if (value == -1) {
					Activity activity = getActivity();
					Toast toast = Toast.makeText(activity, activity.getString(R.string.wrong_data), Toast
							.LENGTH_LONG);
                    ToastUtils.wrap(toast);
					toast.show();
				} else {
					resultPpm.setText(FloatFormatter.format(value));
				}
			}
		});

		resetDatabase.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				SQLiteDatabase database = InterpolationCalculatorApp.getInstance().getSqLiteHelper
						().getWritableDatabase();
				mAvgPointHelper.clear();
				mSquarePointHelper.clear();
				new PointHelper(database).clear();
				mProjectHelper.clear();
				mProjectHelper.startInit(database);
				adapter = new CalculatePpmSimpleAdapter(CalculatePpmSimpleFragment.this,
						CalculatePpmSimpleFragment.this, mAvgPointHelper, mSquarePointHelper,
						mPointHelper, initAdapterDataAndHelpersFromDatabase(false));

				SharedPreferences prefs = InterpolationCalculatorApp.getInstance()
						.getSharedPreferences();
				prefs.edit().remove(PrefConstants.INFO_IS_READY).apply();

				initLayouts();

				mGridView.setAdapter(adapter);

				//ppmPoints.clear();
				//avgSquarePoints.clear();
				//fillAvgPointsLayout();
				//calculatePpmLayoutLoaded.setVisibility(View.GONE);
			}
		});

		resultPpm = (TextView) view.findViewById(R.id.result_ppm);

		resultPpmLoaded = (TextView) view.findViewById(R.id.result_ppm_loaded);

		avgValue = (EditText) view.findViewById(R.id.avg_value);

		avgValueLoaded = (EditText) view.findViewById(R.id.avg_value_loaded);

		buttonsLayout = view.findViewById(R.id.buttons_layout);

		initLayouts();

		btnAddRow = (Button) view.findViewById(R.id.simple_ppm_btn_addRow);

		List<Long> avgPointIds = initAdapterDataAndHelpersFromDatabase(true);

		adapter = new CalculatePpmSimpleAdapter(this, this, mAvgPointHelper, mSquarePointHelper,
				mPointHelper, avgPointIds);

		mGridView.setAdapter(adapter);

		btnAddRow.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				long id = mAvgPointHelper.addAvgPoint();
				for (int i = 0; i < Project.TABLE_MAX_COLS_COUNT; i++) {
					mSquarePointHelper.addSquarePointIdSimpleMeasure(id);
				}

				CalculatePpmSimpleAdapter adapter = ((CalculatePpmSimpleAdapter) mGridView
						.getAdapter());
				adapter.notifyAvgPointAdded(id);
				buttonsLayout.setVisibility(View.INVISIBLE);
			}
		});

		loadPpmCurve.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity().getBaseContext(), FileDialog.class);
				intent.putExtra(FileDialog.START_PATH, Constants.BASE_DIRECTORY.getAbsolutePath());
				intent.putExtra(FileDialog.ROOT_PATH, Constants.BASE_DIRECTORY.getAbsolutePath());
				intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);

				intent.putExtra(FileDialog.FORMAT_FILTER, new String[]{"csv"});

				IntentFolderWrapUtils.wrapFolderForDrawables(getActivity(), intent);

				startActivityForResult(intent, LOAD_PPM_AVG_VALUES_REQUEST_CODE);
			}
		});

		savePpmCurve.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity().getBaseContext(), FileDialog.class);
				intent.putExtra(FileDialog.START_PATH, Constants.BASE_DIRECTORY.getAbsolutePath());
				intent.putExtra(FileDialog.ROOT_PATH, Constants.BASE_DIRECTORY.getAbsolutePath());
				intent.putExtra(FileDialog.CAN_SELECT_DIR, true);

				startActivityForResult(intent, SAVE_PPM_AVG_VALUES);
			}
		});

		mClearRow1 = view.findViewById(R.id.clear_row1);
		mClearRow1.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				resultPpm.setText("");
				avgValue.setText("");
			}
		});

		mClearRow2 = view.findViewById(R.id.clear_row2);
		mClearRow2.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				resultPpmLoaded.setText("");
				avgValueLoaded.setText("");
			}
		});

		mesSelectFolder = view.findViewById(R.id.mes_select_folder);
		mesSelectFolder.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity().getBaseContext(), FileDialog.class);

				File extFile = Environment.getExternalStorageDirectory();

				intent.putExtra(FileDialog.START_PATH, extFile.getAbsolutePath());
				intent.putExtra(FileDialog.ROOT_PATH, extFile.getAbsolutePath());
				intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);

				intent.putExtra(FileDialog.MES_SELECTION_NAMES, new String[]{"CAL_FILES",
						"MES_Files"});
				intent.putExtra(FileDialog.CAN_SELECT_DIR, true);

				startActivityForResult(intent, MES_SELECT_FOLDER);
			}
		});

		if (sBundle != null && sBundle.getBoolean(IS_SAVED, false)) {
			savedInstanceState = sBundle;
			avgValue.setText(savedInstanceState.getString(FIRST_TEXT_TAG));
			resultPpm.setText(savedInstanceState.getString(SECOND_TEXT_TAG));
			avgValueLoaded.setText(savedInstanceState.getString(THIRD_TEXT_TAG));
			resultPpmLoaded.setText(savedInstanceState.getString(FOURTH_TEXT_TAG));
		}
	}

	private boolean attachAdapterToDatabase() {
		List<Float> ppmValues = adapter.getPpmValues();
		List<Long> avgPointIds = adapter.getAvgPointIds();

		for (int i = 0; i < avgPointIds.size(); i++) {
			if (ppmValues.get(i) == 0f) {
				return false;
			} else {
				mAvgPointHelper.updatePpm(avgPointIds.get(i), ppmValues.get(i));
			}
		}
		return true;
	}

	/**
	 * Init layout accord to data.
	 */
	private void initLayouts() {
		SharedPreferences prefs = InterpolationCalculatorApp.getInstance().getSharedPreferences();

		if (prefs.contains(PrefConstants.INFO_IS_READY)) {
			calculatePpmLayout.setVisibility(View.VISIBLE);
			savePpmCurve.setVisibility(View.VISIBLE);
			buttonsLayout.setVisibility(View.VISIBLE);
		} else {
			calculatePpmLayout.setVisibility(View.GONE);
			savePpmCurve.setVisibility(View.GONE);
			buttonsLayout.setVisibility(View.INVISIBLE);
		}
	}

	/**
	 * Init database if it's empty.
	 *
	 * @param isCreatingHelpers true if helpers are null and need to be created.
	 * @return List of id's average square points.
	 */
	private List<Long> initAdapterDataAndHelpersFromDatabase(boolean isCreatingHelpers) {
		SQLiteHelper helper = InterpolationCalculatorApp.getInstance().getSqLiteHelper();
		SQLiteDatabase mDatabase = helper.getWritableDatabase();

		if (isCreatingHelpers) {
			mProjectHelper = new ProjectHelper(mDatabase);
		}

		Project project = mProjectHelper.getProjects().get(0);

		if (isCreatingHelpers) {
			mAvgPointHelper = new AvgPointHelper(mDatabase, project);
		}

		boolean isFirstInit = mAvgPointHelper.getAvgPoints().isEmpty();

		if (isFirstInit) {
			for (int i = 0; i < Project.TABLE_MIN_ROWS_COUNT; i++) {
				mAvgPointHelper.addAvgPoint();
			}
		}
		List<Long> avgPointIds = mAvgPointHelper.getAvgPoints();

		if (isCreatingHelpers) {
			mSquarePointHelper = new SquarePointHelper(mDatabase);
		}

		if (isCreatingHelpers) {
			mPointHelper = new PointHelper(mDatabase);
		}

		if (isFirstInit) {
			for (long avgPoint : avgPointIds) {
				for (int i = 0; i < Project.TABLE_MAX_COLS_COUNT; i++) {
					mSquarePointHelper.addSquarePointIdSimpleMeasure(avgPoint);
				}
			}
		}
		return avgPointIds;
	}

	/**
	 * Fill ppmPoints and avgSquarePoints from database.
	 *
	 * @param ppmPoints       PpmPoints, that will be filled from database.
	 * @param avgSquarePoints Average square points, that will be filled from database
	 */
	private void fillPpmAndSquaresFromDatabase(List<Float> ppmPoints, List<Float>
			avgSquarePoints) {
		SQLiteHelper helper = InterpolationCalculatorApp.getInstance().getSqLiteHelper();
		SQLiteDatabase writeDb = helper.getWritableDatabase();
		Project project = new ProjectHelper(writeDb).getProjects().get(0);
		AvgPointHelper helper1 = new AvgPointHelper(writeDb, project);
		List<Long> avgids = helper1.getAvgPoints();

		CalculatePpmSimpleAdapter adapter = ((CalculatePpmSimpleAdapter) mGridView.getAdapter());

		List<Float> avgValues = adapter.getAvgValues();

		Map<Float, Float> linkedMap = new TreeMap<>();

		for (int i = 0; i < avgids.size(); i++) {
			long avgId = avgids.get(i);
			linkedMap.put(helper1.getPpmValue(avgId), avgValues.get(i));
		}

		for (Map.Entry<Float, Float> entry : linkedMap.entrySet()) {
			ppmPoints.add(entry.getKey());
			avgSquarePoints.add(entry.getValue());
		}
	}

	private boolean handleDirectoryMesSelected(List<File> files) {
		List<Float> correctSquares = new ArrayList<>(files.size());
		for (File file : files) {
			float square1 = CalculateExtensionsKt.calculateSquare(file);
			if (square1 > 0) {
				correctSquares.add(square1);
			}
		}
		if (correctSquares.isEmpty()) {
			return false;
		}

		mAutoAvgPoint = new AvgPoint(correctSquares);
		avgValueLoaded.setText(FloatFormatter.format(mAutoAvgPoint.avg()));
		return true;
	}

	private boolean handleCsvFileMesSelected(File csvFile) {
		final float square1 = CalculateExtensionsKt.calculateSquare(csvFile);
		if (square1 > 0) {
			mAutoAvgPoint = new AvgPoint(new ArrayList<Float>() {{
				add(square1);
			}});
			avgValueLoaded.setText(FloatFormatter.format(mAutoAvgPoint.avg()));
		}
		return square1 > 0;
	}

	private List<File> searchCsvFilesInside(final File file) {
		if (!file.isDirectory()) {
			if (!file.getAbsolutePath().endsWith(".csv")) {
				return null;
			} else {
				return new ArrayList<File>() {{
					add(file);
				}};
			}
		} else {
			List<File> result = new ArrayList<>();
			for (File localFile : file.listFiles()) {
				List<File> filesInside = searchCsvFilesInside(localFile);
				if (filesInside != null) {
					result.addAll(filesInside);
				}
			}
			return result;
		}
	}

	@Override
	public void onActivityResult(final int requestCode, int resultCode, final Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			switch (requestCode) {
				case LOAD_PPM_AVG_VALUES_REQUEST_CODE:
					new LoadPpmAvgValuesTask(data.getStringExtra(FileDialog.RESULT_PATH))
							.execute();
					break;
				case SAVE_PPM_AVG_VALUES:
					new AsyncTask<Void, Void, Void>() {

						@Override
						protected Void doInBackground(Void... params) {
							ppmPoints.clear();
							avgSquarePoints.clear();
							fillPpmAndSquaresFromDatabase(ppmPoints, avgSquarePoints);
							return null;
						}

						@Override
						protected void onPostExecute(Void aVoid) {
							Calendar calendar = Calendar.getInstance();

							final String timeName = "CAL_" + formatAddLeadingZero(calendar.get
									(Calendar.YEAR)) + formatAddLeadingZero(calendar.get(Calendar
									.MONTH)) + formatAddLeadingZero(calendar.get(Calendar
									.DAY_OF_MONTH)) + "_" + formatAddLeadingZero(calendar.get
									(Calendar.HOUR_OF_DAY)) +
									formatAddLeadingZero(calendar.get(Calendar.MINUTE)) +
									formatAddLeadingZero(calendar.get(Calendar.SECOND));

							AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

							View contentView = LayoutInflater.from(getActivity()).inflate(R.layout
									.save_additional_options_layout, null);

							final EditText editFileName = (EditText) contentView.findViewById(R.id
									.edit_file_name);

							builder.setView(contentView);
							builder.setCancelable(true);

							final AlertDialog dialog = builder.show();

							contentView.findViewById(R.id.save_curve).setOnClickListener(new View
									.OnClickListener() {

								@Override
								public void onClick(View v) {
									new AsyncTask<Void, Void, Boolean>() {

										private CalculatePpmSimpleAdapter adapter;

										private boolean isChecked;

										private String fileName;

										@Override
										protected void onPreExecute() {
											adapter = (CalculatePpmSimpleAdapter) mGridView
													.getAdapter();
											isChecked = connect0.isChecked();
											fileName = timeName + "_" +
													editFileName.getText().toString() +
													".csv";
										}

										@Override
										protected Boolean doInBackground(Void... params) {
											File pathFile = new File(data.getStringExtra
													(FileDialog.RESULT_PATH), fileName);

											pathFile.getParentFile().mkdirs();
											try {
												pathFile.createNewFile();
											} catch (IOException e) {
												e.printStackTrace();
											}

											return CalculatePpmUtils.saveAvgValuesToFile(adapter,
													7, pathFile.getAbsolutePath(), isChecked);
										}

										@Override
										protected void onPostExecute(Boolean res) {
											if (res) {
												Toast toast = Toast.makeText(getActivity(), "Save success as " +
														fileName, Toast.LENGTH_LONG);
                                                ToastUtils.wrap(toast);
												toast.show();
											} else {
												Toast toast = Toast.makeText(getActivity(), "Write " + "failed",
														Toast.LENGTH_LONG);
                                                ToastUtils.wrap(toast);
												toast.show();
											}
											dialog.dismiss();
										}
									}.execute();
								}
							});
						}
					}.execute();

					break;
				case MES_SELECT_FOLDER:
					mCalculatePpmAvg = true;
					mForceSearchOfMesFolder = true;
					LoadPpmAvgValuesTask task = new LoadPpmAvgValuesTask(mUrlWhenAutoLoading);
					task.setmMesFolder(data.getStringExtra(FileDialog.RESULT_PATH));
					task.execute();
					break;
				default:
					new AsyncTask<Void, Void, Boolean>() {

						private int row;

						@Override
						protected void onPreExecute() {
							row = requestCode / Project.TABLE_MAX_COLS_COUNT;
						}

						@Override
						protected Boolean doInBackground(Void... params) {
							int col = requestCode % Project.TABLE_MAX_COLS_COUNT;
							return adapter.updateSquare(row, col, data.getStringExtra(FileDialog
									.RESULT_PATH));
						}

						@Override
						protected void onPostExecute(Boolean aVoid) {
							getActivity().getCurrentFocus().clearFocus();
							if (!aVoid) {
								Toast toast = Toast.makeText(getActivity(), "You select wrong file", Toast
										.LENGTH_LONG);
                                ToastUtils.wrap(toast);
								toast.show();
							} else {
								adapter.checkAvgValues();
								adapter.calculateAvg(row);
							}
						}
					}.execute();
			}
		}
	}

	private String formatAddLeadingZero(int value) {
		return (value < 10 ? "0" : "") + value;
	}

	/**
	 * Fill layout with actual data.
	 */
	private void fillAvgPointsLayout() {
		avgPointsLayout.removeAllViews();

		for (int i = 0; i < ppmPoints.size(); i++) {
			TextView tv = new TextView(getActivity());
			tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
					.edit_text_size_default));
			tv.setText(ppmPoints.get(i).intValue() + " " + FloatFormatter.format(avgSquarePoints
					.get(i)) + "    ");
			tv.setTextColor(Color.WHITE);

			avgPointsLayout.addView(tv);
		}
		calculatePpmLayoutLoaded.setVisibility(View.VISIBLE);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		attachAdapterToDatabase();
		onSaveInstanceState(sBundle);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putBoolean(IS_SAVED, true);
		outState.putString(FIRST_TEXT_TAG, avgValue.getText().toString());
		outState.putString(SECOND_TEXT_TAG, resultPpm.getText().toString());
		outState.putString(THIRD_TEXT_TAG, avgValueLoaded.getText().toString());
		outState.putString(FOURTH_TEXT_TAG, resultPpmLoaded.getText().toString());
	}

	@Override
	public void onInfoFilled() {
		InterpolationCalculatorApp.getInstance().getSharedPreferences().edit().putBoolean
				(PrefConstants.INFO_IS_READY, true).apply();
		calculatePpmLayout.setVisibility(View.VISIBLE);
		buttonsLayout.setVisibility(View.VISIBLE);
		savePpmCurve.setVisibility(View.VISIBLE);
	}

	public class LoadPpmAvgValuesTask extends BaseLoadTask {

		private String mUrl;

		private String mMesFolder;

		public LoadPpmAvgValuesTask(String mUrl) {
			super(mUrl);
			this.mUrl = mUrl;
		}

		@Override
		public void setUrl(String mUrl) {
			super.setUrl(mUrl);
			this.mUrl = mUrl;
		}

		@Override
		public FrameLayout getFrameLayout() {
			return null;
		}

		@Override
		public void setProgressBar(View progressBar) {

		}

		public void setmMesFolder(String mMesFolder) {
			this.mMesFolder = mMesFolder;
		}

		protected Boolean doInBackground(Void[] params) {
			Pair<List<Float>, List<Float>> res = CalculatePpmUtils.parseAvgValuesFromFile(mUrl,
					getActivity());

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
				Toast toast = Toast.makeText(getActivity(), "You select wrong file", Toast
						.LENGTH_LONG);
                ToastUtils.wrap(toast);
				toast.show();
				return;
			}

			fillAvgPointsLayout();
			List<Float> ppmPoints = new ArrayList<>(CalculatePpmSimpleFragment
					.this.ppmPoints);
			List<Float> avgSquarePoints = new ArrayList<>(CalculatePpmSimpleFragment
					.this.avgSquarePoints);
			InterpolationCalculatorApp interpolationCalculatorApp = InterpolationCalculatorApp
					.getInstance();
			interpolationCalculatorApp.setAvgSquarePoints(avgSquarePoints);
			interpolationCalculatorApp.setPpmPoints(ppmPoints);
			graph1.setVisibility(View.VISIBLE);

			if (mCalculatePpmAvg) {
				mUrlWhenAutoLoading = mUrl;
				File mesFile = null;

				if (mMesFolder == null) {
					mesFile = findMesFile(Constants.BASE_DIRECTORY.getParentFile());
				}
				if (mMesFolder != null || (mesFile != null && findNewestFolder(mesFile, "MES") !=
						null)) {
					if (mMesFolder != null) {
						File mMesFolderFile = new File(mMesFolder);

						final boolean isCorrectFilesSelected;
						if (mMesFolderFile.isDirectory()) {
							isCorrectFilesSelected = handleDirectoryMesSelected
									(searchCsvFilesInside(mMesFolderFile));
						} else {
							isCorrectFilesSelected = handleCsvFileMesSelected(mMesFolderFile);
						}
						if (!isCorrectFilesSelected) {
							Toast toast = Toast.makeText(getActivity(), "Wrong files for calculating", Toast
									.LENGTH_LONG);
                            ToastUtils.wrap(toast);
							toast.show();
						}
						return;
					}

					mesFile = findNewestFolder(mesFile, "MES");
					File mesFiles[] = mesFile.listFiles();
					if (mesFiles == null && mesFile.getParentFile() != null) {
						mesFiles = mesFile.getParentFile().listFiles();
					} else if (mesFiles == null) {
                        Toast toast = Toast.makeText(getActivity(), "Wrong files for calculating", Toast
								.LENGTH_LONG);
                        ToastUtils.wrap(toast);
                        toast.show();
						return;
					}
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

					if (newestCalFile1 != null) {
						float square1 = CalculateExtensionsKt.calculateSquare(newestCalFile1);
						if (square1 == -1) {
                            Toast toast = Toast.makeText(getActivity(), "Wrong files for calculating", Toast
									.LENGTH_LONG);
                            ToastUtils.wrap(toast);
                            toast.show();
							return;
						} else {
							if (newestCalFile2 == null) {
								mAutoAvgPoint = new AvgPoint(Arrays.asList(new Float[]{square1}));
								calculatePpmAuto.performClick();
								mClearRow2.performClick();
								return;
							}
							float square2 = CalculateExtensionsKt.calculateSquare(newestCalFile2);
							if (square2 == -1) {
                                Toast toast = Toast.makeText(getActivity(), "Wrong files for calculating", Toast
										.LENGTH_LONG);
                                ToastUtils.wrap(toast);
                                toast.show();
								return;
							} else {
								if (newestCalFile3 == null) {
									mAutoAvgPoint = new AvgPoint(Arrays.asList(new
											Float[]{square1, square2}));
									calculatePpmAuto.performClick();
									mClearRow2.performClick();
									return;
								}
								float square3 = CalculateExtensionsKt.calculateSquare(newestCalFile3);
								if (square3 == -1) {
                                    Toast toast = Toast.makeText(getActivity(), "Wrong files for calculating",
											Toast.LENGTH_LONG);
                                    ToastUtils.wrap(toast);
                                    toast.show();
									return;
								} else {
									mAutoAvgPoint = new AvgPoint(Arrays.asList(new
											Float[]{square1, square2, square3}));
									calculatePpmAuto.performClick();
									mClearRow2.performClick();
								}
							}
						}
					}
				} else {
                    Toast toast = Toast.makeText(getActivity(), "Please make MES directory to find ppm", Toast
							.LENGTH_LONG);
                    ToastUtils.wrap(toast);
                    toast.show();
				}
				mCalculatePpmAvg = false;
			}
		}
	}
}
