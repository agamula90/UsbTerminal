package com.proggroup.areasquarecalculator.fragments;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.renderer.XAxisRenderer;
import com.github.mikephil.charting.renderer.XAxisRendererRadarChart;
import com.proggroup.areasquarecalculator.R;
import com.proggroup.areasquarecalculator.utils.FloatFormatter;
import com.proggroup.areasquarecalculator.views.ChartMarkerView;

import java.util.ArrayList;
import java.util.List;

public class CurveFragment extends Fragment {

	private static final String PPMS_TAG = "ppms";

	private static final String SQUARES_TAG = "squares";

	private GridView mDisplayGrid;

	private LineChart mLineChart;

	private ArrayList<String> ppmStrings, squareStrings;

	private SparseArray<Float> squares;

	public CurveFragment() {
	}

	public static CurveFragment newInstance(ArrayList<String> ppmStrings, ArrayList<String>
			squareStrings) {
		CurveFragment fragment = new CurveFragment();
		Bundle args = new Bundle();
		args.putStringArrayList(PPMS_TAG, ppmStrings);
		args.putStringArrayList(SQUARES_TAG, squareStrings);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
			savedInstanceState) {
		return inflater.inflate(R.layout.fragment_chart, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mDisplayGrid = (GridView) view.findViewById(R.id.display_values);
		mLineChart = (LineChart) view.findViewById(R.id.chart);

		Bundle args = getArguments();
		ppmStrings = args.getStringArrayList(PPMS_TAG);
		squareStrings = args.getStringArrayList(SQUARES_TAG);

		int countPoints = squareStrings.size();

		squares = new SparseArray<>(countPoints);

		for (int i = 0; i < countPoints; i++) {
			squares.put(Integer.parseInt(ppmStrings.get(i)), Float.parseFloat(squareStrings.get
					(i)));
		}

		initLine(mLineChart, getActivity(), squares);
		initGrid();
	}

	private void initGrid() {
		mDisplayGrid.setAdapter(new BaseAdapter() {

			static final int header_top = 0;

			static final int text_number = 2;

			static final int text_ppm = 3;

			static final int text_value = 4;

			@Override
			public int getCount() {
				return (squares.size() + 1) * 3;
			}

			@Override
			public Object getItem(int position) {
				return null;
			}

			@Override
			public long getItemId(int position) {
				if (position < 3) {
					return header_top;
				}
				switch (position % 3) {
					case 0:
						return text_number;
					case 1:
						return text_ppm;
					case 2:
						return text_value;
					default:
						return header_top;
				}
			}

			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				int itemId = (int) getItemId(position);

				if (convertView == null) {
					convertView = inflateView(itemId, parent);
				} else {
					int tag = (int) convertView.getTag();
					if (tag == header_top) {
						if (itemId != tag) {
							convertView = inflateView(itemId, parent);
						}
					} else if (itemId == header_top) {
						convertView = inflateView(itemId, parent);
					}
				}
				convertView.getLayoutParams().height = (int) convertView.getResources()
						.getDimension(R.dimen.grid_view_curve_height);

				convertView.setTag(itemId);

				TextView textView = (TextView) convertView.findViewById(R.id.edit);

				if (itemId != header_top) {
					textView.setEnabled(false);
					textView.setGravity(Gravity.CENTER);
					textView.setTextColor(Color.BLACK);
					if (itemId == text_number) {
						((View) textView.getParent()).setBackgroundColor(getResources().getColor(R
								.color.edit_disabled));
					} else {
						((View) textView.getParent()).setBackgroundColor(getResources().getColor(R
								.color.grid_item_bkg_color));
					}
				} else {
					TextView tv = (TextView) convertView.findViewById(R.id.header_name);
					tv.setBackgroundColor(getResources().getColor(R.color.edit_disabled));
				}

				switch (itemId) {
					case header_top:
						((TextView) convertView.findViewById(R.id.header_name)).setText
								(getResources().getStringArray(R.array.head1)[position]);
						break;
					case text_number:
						textView.setText(position / 3 + "");
						break;
					case text_ppm:
						textView.setText(squares.keyAt(position / 3 - 1) + "");
						break;
					case text_value:
						textView.setText(FloatFormatter.format(squares.valueAt(position / 3 - 1)));
						break;
				}

				return convertView;
			}

			private View inflateView(int itemId, ViewGroup parent) {
				View convertView;
				switch (itemId) {
					case header_top:
						convertView = LayoutInflater.from(getActivity()).inflate(R.layout
								.layout_table_header, parent, false);
						break;
					default:
						convertView = LayoutInflater.from(getActivity()).inflate(R.layout
								.layout_table_edit_text, parent, false);
				}
				return convertView;
			}
		});
	}

	public static void initLine(LineChart lineChart, Activity activity, SparseArray<Float>
			 squares) {
        //0, 50, 50, 0
		lineChart.setExtraOffsets(30, 40, 60, 10);
		lineChart.setDrawGridBackground(false);
		lineChart.setDoubleTapToZoomEnabled(false);
		lineChart.getDescription().setEnabled(false);
		lineChart.setTouchEnabled(true);
		lineChart.setDragEnabled(true);
		lineChart.setScaleEnabled(true);
		lineChart.setPinchZoom(true);

        int xMin = squares.keyAt(0);
        int xMax = squares.keyAt(squares.size() - 1);
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(12f);
        xAxis.setEnabled(true);
        xAxis.setAxisMinimum(xMin);
        xAxis.setAxisMaximum(xMax);
        xAxis.setLabelCount(squares.size() + 1, true);
		ChartMarkerView mv = new ChartMarkerView(activity, R.layout.chart_marer_view);
		lineChart.setMarker(mv);

        List<Entry> chartEntries = new ArrayList();
        for (int i = 0; i < squares.size(); i++) {
            float square = squares.valueAt(i);
            if (square >= 0) {
                chartEntries.add(new Entry(square, squares.keyAt(i) - xMin));
            }
        }

        LineDataSet set = new LineDataSet(chartEntries, null/*"Square values"*/);
        set.setHighLightColor(Color.GREEN);
        set.setColor(Color.BLACK);
        set.setCircleColor(Color.BLACK);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setDrawCircleHole(false);
        set.setValueTextSize(12f);
        set.setFillAlpha(65);
        set.setFillColor(Color.BLACK);
        set.setDrawFilled(false);
        set.setDrawCircleHole(true);
        lineChart.setData(new LineData(set));

		YAxis leftAxis = lineChart.getAxisLeft();
		leftAxis.setEnabled(true);
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawLabels(false);
        leftAxis.setDrawTopYLabelEntry(true);
		leftAxis.setAxisMaximum(squares.valueAt(squares.size() - 1) + 100);
		leftAxis.setAxisMaximum(squares.valueAt(0) - 100);
        //TODO this conflicts with setaxisminimum call, maybe we'll want set draw sero line call
		//leftAxis.setStartAtZero(true);
		leftAxis.setDrawLimitLinesBehindData(true);
        lineChart.getAxisRight().setEnabled(false);

		lineChart.invalidate();
		Legend l = lineChart.getLegend();
		l.setForm(Legend.LegendForm.LINE);
        l.setEnabled(false);
	}
}
