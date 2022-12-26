package com.proggroup.areasquarecalculator.adapters;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.os.AsyncTask;
import androidx.fragment.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.proggroup.areasquarecalculator.InterpolationCalculatorApp;
import com.proggroup.areasquarecalculator.R;
import com.proggroup.areasquarecalculator.data.AvgPoint;
import com.proggroup.areasquarecalculator.data.Constants;
import com.proggroup.areasquarecalculator.data.Project;
import com.proggroup.areasquarecalculator.db.AvgPointHelper;
import com.proggroup.areasquarecalculator.db.PointHelper;
import com.proggroup.areasquarecalculator.db.SquarePointHelper;
import com.proggroup.areasquarecalculator.utils.FloatFormatter;
import com.proggroup.areasquarecalculator.utils.IntentFolderWrapUtils;
import com.proggroup.areasquarecalculator.utils.ToastUtils;
import com.proggroup.CalculateExtensionsKt;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import fr.xgouchet.FileDialog;
import fr.xgouchet.SelectionMode;

public class CalculatePpmSimpleAdapter extends BaseAdapter {

	/*
	 * Field is for actual data.
	 * Can be loaded from csv file, or database if it's filled
	 */
	public static final int ITEM_ID_DATA = 0;

	/*
	 * Field is for header
	 * Loading from array resource.
	 */
	public static final int ITEM_ID_HEADER = 1;

	/*
	 * Field is for result of avg calculations of available data.
	 */
	public static final int ITEM_ID_CALC_AVG_RESULT = 2;

	/*
	 * Field is for input ppm of value
	 */
	public static final int ITEM_ID_KNOWN_PPM = 3;

	public static final int ITEM_ID_DELETE_ROW = 4;

	private final Fragment fragment;

	private final OnInfoFilledListener onInfoFilledListener;

	private SquarePointHelper squarePointHelper;

	private AvgPointHelper avgPointHelper;

	private PointHelper mPointHelper;

	private List<List<Float>> squareValues;

	private List<Float> avgValues;

	private List<Float> ppmValues;

	private List<List<String>> squareTexts;

	private List<String> avgTexts;

	private List<String> ppmTexts;

	private List<Long> avgPointIds;

	private int ppmIndex;

	public CalculatePpmSimpleAdapter(Fragment fragment, OnInfoFilledListener onInfoFilledListener,
			AvgPointHelper avgPointHelper, SquarePointHelper mSquarePointHelper, PointHelper
			mPointHelper, List<Long> avgPointIds) {
		this.fragment = fragment;
		this.onInfoFilledListener = onInfoFilledListener;
		this.avgPointHelper = avgPointHelper;

		ppmValues = new ArrayList<>(avgPointIds.size());
		ppmTexts = new ArrayList<>(avgPointIds.size());

		for (int i = 0; i < avgPointIds.size(); i++) {
			ppmValues.add(avgPointHelper.getPpmValue(avgPointIds.get(i)));

			int ppmValue = ppmValues.get(i).intValue();
			if (ppmValue == 0) {
				ppmTexts.add("");
			} else {
				ppmTexts.add("" + ppmValue);
			}
		}

		this.avgPointIds = avgPointIds;

		squarePointHelper = mSquarePointHelper;

		squareValues = new ArrayList<>(Project.TABLE_MAX_COLS_COUNT);
		squareTexts = new ArrayList<>(Project.TABLE_MAX_COLS_COUNT);

		for (int i = 0; i < avgPointIds.size(); i++) {
			List<Float> squares = new ArrayList<>(Project.TABLE_MAX_COLS_COUNT);
			List<String> texts = new ArrayList<>(Project.TABLE_MAX_COLS_COUNT);
			for (int j = 0; j < Project.TABLE_MAX_COLS_COUNT; j++) {
				squares.add(0f);
				texts.add("");
			}
			squareValues.add(squares);
			squareTexts.add(texts);
		}

		this.mPointHelper = mPointHelper;

		for (int i = 0; i < avgPointIds.size(); i++) {
			List<Long> squareIds = squarePointHelper.getSquarePointIds(avgPointIds.get(i));
			for (int j = 0; j < squareIds.size(); j++) {
				long squareId = squareIds.get(j);

				List<PointF> points = mPointHelper.getPoints(squareId);
				if (!points.isEmpty()) {
					squareValues.get(i).set(j, CalculateExtensionsKt.calculateSquare(points));
					squareTexts.get(i).set(j, squareValues.get(i).get(j) == 0 ? "" :
							FloatFormatter.format(squareValues.get(i).get(j)));
				}
			}
		}

		avgValues = new ArrayList<>(avgPointIds.size());
		avgTexts = new ArrayList<>(avgPointIds.size());

		for (int i = 0; i < avgPointIds.size(); i++) {
			List<Float> squares = squareValues.get(i);
			avgValues.add(new AvgPoint(remove0List(squares)).avg());
			Float res = avgValues.get(avgValues.size() - 1);
			avgTexts.add(res == 0 ? "" : FloatFormatter.format(res));
		}
		ppmIndex = -1;

		checkAvgValues();
	}

	public List<Float> getPpmValues() {
		return ppmValues;
	}

	public List<Long> getAvgPointIds() {
		return avgPointIds;
	}

	/**
	 * @return Square values, calculated from all loaded csv files.
	 */
	public List<List<Float>> getSquareValues() {
		return squareValues;
	}

	/**
	 * @return Average square values, calculated for each of rows.
	 */
	public List<Float> getAvgValues() {
		return avgValues;
	}

	/**
	 * Notify of adding new avgPoint to database.
	 *
	 * @param avgPointId Id of new avgPoint is added to database.
	 */
	public void notifyAvgPointAdded(long avgPointId) {
		ppmValues.add(0f);
		ppmTexts.add("");
		avgPointIds.add(avgPointId);
		avgTexts.add("");
		List<Float> points = new ArrayList<>(Project.TABLE_MAX_COLS_COUNT);
		for (int i = 0; i < Project.TABLE_MAX_COLS_COUNT; i++) {
			points.add(0f);
		}
		squareValues.add(points);

		List<String> texts = new ArrayList<>(Project.TABLE_MAX_COLS_COUNT);
		for (int i = 0; i < Project.TABLE_MAX_COLS_COUNT; i++) {
			texts.add("");
		}
		squareTexts.add(texts);
		avgValues.add(new AvgPoint(remove0List(points)).avg());
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return (avgValues.size() + 1) * (Project.TABLE_MAX_COLS_COUNT + 3);
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public Object getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return getItemViewType(position);
	}

	@Override
	public int getViewTypeCount() {
		return ITEM_ID_DELETE_ROW + 1;
	}

	@Override
	public int getItemViewType(int position) {
		if (position < Project.TABLE_MAX_COLS_COUNT + 3) {
			return ITEM_ID_HEADER;
		} else if (position % (Project.TABLE_MAX_COLS_COUNT + 3) == Project.TABLE_MAX_COLS_COUNT +
				1) {
			return ITEM_ID_CALC_AVG_RESULT;
		} else if (position % (Project.TABLE_MAX_COLS_COUNT + 3) == 0) {
			return ITEM_ID_KNOWN_PPM;
		} else if (position % (Project.TABLE_MAX_COLS_COUNT + 3) == Project.TABLE_MAX_COLS_COUNT +
				2) {
			return ITEM_ID_DELETE_ROW;
		} else {
			return ITEM_ID_DATA;
		}
	}

	@Override
	public View getView(final int position, View convertView, final ViewGroup parent) {
		int itemId = (int) getItemId(position);

		if (convertView == null || convertView.getTag() != Integer.valueOf(itemId)) {
			LayoutInflater inflater = (LayoutInflater) InterpolationCalculatorApp.getInstance()
					.getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			switch (itemId) {
				case ITEM_ID_HEADER:
					convertView = inflater.inflate(R.layout.layout_table_header, parent, false);
					break;
				case ITEM_ID_KNOWN_PPM:
					convertView = inflater.inflate(R.layout.layout_table_ppm_edit, parent, false);
					break;
				case ITEM_ID_CALC_AVG_RESULT:
					convertView = inflater.inflate(R.layout.layout_table_edit_text, parent, false);
					convertView.findViewById(R.id.edit).setEnabled(false);
					break;
				case ITEM_ID_DATA:
					convertView = inflater.inflate(R.layout.layout_table_item, parent, false);
					break;
				case ITEM_ID_DELETE_ROW:
					convertView = inflater.inflate(R.layout.layout_table_delete, parent, false);
					break;
				default:
					convertView = inflater.inflate(R.layout.layout_table_header, parent, false);
			}

			convertView.setTag(Integer.valueOf(itemId));
		}

		switch (itemId) {
			case ITEM_ID_HEADER:
				((TextView) convertView.findViewById(R.id.header_name)).setText(convertView
						.getResources().getStringArray(R.array.headers)[position]);
				break;
			case ITEM_ID_KNOWN_PPM:
				final int index = position / (Project.TABLE_MAX_COLS_COUNT + 3) - 1;

				EditText ppmText = (EditText) convertView.findViewById(R.id.edit);
				ppmText.setGravity(Gravity.NO_GRAVITY);

				if (ppmText.getTag() != null) {
					Integer tag = (Integer) ppmText.getTag();
					if (tag != index) {
						ppmText.removeTextChangedListener(new PpmWatcher(tag));
						ppmText.addTextChangedListener(new PpmWatcher(index));
						ppmText.setTag(index);
					}
				} else {
					ppmText.addTextChangedListener(new PpmWatcher(index));
					ppmText.setTag(index);
				}

				ppmText.setOnTouchListener(new View.OnTouchListener() {

					@Override
					public boolean onTouch(View v, MotionEvent event) {
						switch (event.getAction() & MotionEvent.ACTION_MASK) {
							case MotionEvent.ACTION_DOWN:
								ppmIndex = (Integer) v.getTag();
								break;
						}
						return false;
					}
				});


				ppmText.setText(ppmTexts.get(index));

				break;
			case ITEM_ID_CALC_AVG_RESULT:
				int index1 = position / (Project.TABLE_MAX_COLS_COUNT + 3) - 1;

				ppmText = (EditText) convertView.findViewById(R.id.edit);
				ppmText.setGravity(Gravity.CENTER);

				ppmText.setText(avgTexts.get(index1));
				break;
			case ITEM_ID_DATA:
				index1 = position / (Project.TABLE_MAX_COLS_COUNT + 3) - 1;

				int pointNumber = position % (Project.TABLE_MAX_COLS_COUNT + 3) - 1;

				TextView squareVal = (TextView) convertView.findViewById(R.id.square_value);


				squareVal.setText(squareTexts.get(index1).get(pointNumber));

				View csvView = convertView.findViewById(R.id.csv);

				csvView.setOnClickListener(new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						Integer tag = (Integer) v.getTag();

						int row = tag / Project.TABLE_MAX_COLS_COUNT;

						if (ppmValues.get(row) == 0) {
							Activity activity = fragment.getActivity();
							Toast toast = Toast.makeText(activity, activity.getString(R.string
									.input_ppm_first),
									Toast.LENGTH_LONG);
                            ToastUtils.wrap(toast);
							toast.show();
							return;
						}

						Intent intent = new Intent(fragment.getActivity().getBaseContext(), FileDialog.class);

						File mesFolder = Constants.BASE_DIRECTORY;

						intent.putExtra(FileDialog.START_PATH, mesFolder.getAbsolutePath());
						intent.putExtra(FileDialog.ROOT_PATH, mesFolder.getAbsolutePath());
						intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);

						IntentFolderWrapUtils.wrapFolderForDrawables(fragment.getActivity(),
								intent);

						intent.putExtra(FileDialog.FORMAT_FILTER, new String[]{"csv"});
						fragment.startActivityForResult(intent, tag);
					}
				});

				int tag = index1 * Project.TABLE_MAX_COLS_COUNT + pointNumber;

				csvView.setTag(tag);

				break;
			case ITEM_ID_DELETE_ROW:
				convertView.findViewById(R.id.delete_row).setOnClickListener(new View
						.OnClickListener() {

					@Override
					public void onClick(View v) {
						final int index1 = position / (Project.TABLE_MAX_COLS_COUNT + 3) - 1;

						GridView parentGrid = (GridView) parent;

						Activity activity = (Activity) parentGrid.getContext();

						InputMethodManager manager = (InputMethodManager) activity
								.getSystemService(Context.INPUT_METHOD_SERVICE);

						View view = activity.getCurrentFocus();

						if (view != parentGrid && gridContainsFocus(activity, view, parentGrid) &&
								manager.isActive()) {
							Toast toast = Toast.makeText(parent.getContext(), "Cann't delete row " +
                                    "when " +
                                    "grid " +
									"has focus", Toast.LENGTH_LONG);
                            ToastUtils.wrap(toast);
                            toast.show();
						} else {
							new AsyncTask() {

								@Override
								protected Object doInBackground(Object[] params) {
									avgPointHelper.deleteAvgPoint(avgPointIds.get(index1),
											squarePointHelper, mPointHelper);
									avgPointIds.remove(index1);
									avgTexts.remove(index1);
									avgValues.remove(index1);

									ppmValues.remove(index1);
									ppmTexts.remove(index1);

									squareValues.remove(index1);
									squareTexts.remove(index1);
									return null;
								}

								@Override
								protected void onPostExecute(Object o) {
									checkAvgValues();
									notifyDataSetChanged();
								}
							}.execute();
						}
					}
				});
				break;
		}

		return convertView;
	}

	private boolean gridContainsFocus(Activity activity, View focusedView, GridView parentView) {
		if (focusedView == parentView) {
			return true;
		} else if (focusedView == null || focusedView.getParent() == null || focusedView.getParent
				() == activity.getWindow().getDecorView()) {
			return false;
		} else {
			return gridContainsFocus(activity, (View) focusedView.getParent(), parentView);
		}
	}

	/**
	 * Calculate avg value for row index
	 *
	 * @param rowNumber row index
	 */
	public void calculateAvg(int rowNumber) {
		List<Float> values = squareValues.get(rowNumber);
		avgValues.set(rowNumber, new AvgPoint(remove0List(values)).avg());
		avgTexts.set(rowNumber, avgValues.get(rowNumber) == 0 ? "" : FloatFormatter.format
				(avgValues.get(rowNumber)));
		notifyDataSetChanged();
	}

	/**
	 * Remove 0 values from list.
	 */
	private List<Float> remove0List(List<Float> values) {
		List<Float> res = new ArrayList<>(values.size());
		for (float val : values) {
			if (val != 0f) {
				res.add(val);
			}
		}
		return res;
	}

	/**
	 * Update adapter values accord to actual data
	 *
	 * @param row    Row index
	 * @param column Column index
	 * @param path   Path to file csv is loaded from
	 */
	public boolean updateSquare(int row, int column, String path) {
		List<Float> squares = squareValues.get(row);
		File f = new File(path);

		float newSquare = CalculateExtensionsKt.calculateSquare(f);
		if (newSquare < 0f) {
			return false;
		}

		squares.set(column, CalculateExtensionsKt.calculateSquare(f));
		squareTexts.get(row).set(column, squareValues.get(row).get(column) == 0f ? "" :
				FloatFormatter.format(squares.get(column)));

		boolean inited = false;

		for (float square : squares) {
			if (square != 0f) {
				inited = true;
				break;
			}
		}

		if (inited) {
			List<Float> res = new ArrayList<>(squares.size());
			for (float val : squares) {
				if (val != 0f) {
					res.add(val);
				}
			}
			avgValues.set(row, new AvgPoint(res).avg());
			avgTexts.set(row, avgValues.get(row) == 0 ? "" : FloatFormatter.format(avgValues.get
					(row)));
		}

		long squareId = squarePointHelper.getSquarePointIds(avgPointIds.get(row)).get(column);

		List<PointF> points = CalculateExtensionsKt.readPoints(f);

		List<PointF> dbPoints = mPointHelper.getPoints(squareId);
		if (dbPoints.isEmpty()) {
			mPointHelper.addPoints(squareId, points);
		} else {
			mPointHelper.updatePoints(squareId, points);
		}
		return true;
	}

	/**
	 * Check if all values are filled, and invoke ready listener if it is.
	 */
	public void checkAvgValues() {
		for (float avgValue : avgValues) {
			if (avgValue == 0f) {
				return;
			}
		}

		onInfoFilledListener.onInfoFilled();
	}

	public interface OnInfoFilledListener {

		void onInfoFilled();
	}

	/**
	 * Watcher for ppm value changed.
	 */
	private class PpmWatcher implements TextWatcher {

		private final int index;

		private PpmWatcher(int index) {
			this.index = index;
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {

		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {

		}

		@Override
		public void afterTextChanged(Editable s) {
			if (!s.toString().isEmpty()) {
				ppmTexts.set(index, s.toString());
				ppmValues.set(index, (float) Integer.parseInt(ppmTexts.get(index)));
			} else {
				ppmTexts.set(index, "");
				ppmValues.set(index, 0f);
			}
		}

		@Override
		public boolean equals(Object o) {
			return o != null && o instanceof PpmWatcher && ((PpmWatcher) o).index == index;
		}
	}
}
