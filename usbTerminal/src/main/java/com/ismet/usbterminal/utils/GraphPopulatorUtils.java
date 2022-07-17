package com.ismet.usbterminal.utils;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

import com.ismet.usbterminalnew.R;
import com.ismet.usbterminal.mainscreen.EToCMainActivity;
import com.proggroup.areasquarecalculator.utils.AutoExpandKeyboardUtils;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.AbstractChart;
import org.achartengine.chart.CubicLineChart;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GraphPopulatorUtils {

	public static XYSeries addNewSet(XYMultipleSeriesRenderer renderer, XYMultipleSeriesDataset
			currentdataset) {
		String[] titles = new String[]{"Response %"};// "Crete Air Temperature",
		double[] xvArray = new double[1];
		double[] yvArray = new double[1];

		List<double[]> x = new ArrayList<>();
		for (int i = 0; i < titles.length; i++) {
			// x.add(new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
			// 12,13,14,15,16,17,18,19,20,21,22,23,24,25 });
			x.add(xvArray);
		}

		List<double[]> values = new ArrayList<>();
		// values.add(new double[] { 9, 10, 11, 15, 19, 23, 26, 25, 22, 18, 13,
		// 10, 9, 10, 11, 15, 19, 23, 26, 25, 22, 18, 13, 10, 9 });
		values.add(yvArray);

		renderer.addSeriesRenderer(getSeriesRenderer(Color.RED));
		XYSeries series = new XYSeries(titles[0]);
		currentdataset.addSeries(series);
		// mChartView.invalidate();
		// addXYSeries(currentdataset, titles, x, values, 0);

		//TODO set fields
		//currentSeries = series;
		return series;
	}

	private static XYSeriesRenderer getSeriesRenderer(final int color) {
		final XYSeriesRenderer r = new XYSeriesRenderer();
		r.setLineWidth(1.0f);
		r.setFillPoints(false);
		r.setDisplayChartValues(false);
		return r;
	}

	public static GraphData createXYChart(int mins, int secs, EToCMainActivity activity) {
		String[] titles = new String[]{"ppm", "ppm", "ppm"};// ,"","" };//

		double[] xvArray = new double[1];
		double[] yvArray = new double[1];

		List<double[]> x = new ArrayList<>();
		for (int i = 0; i < titles.length; i++) {
			x.add(xvArray);
		}

		List<double[]> values = new ArrayList<double[]>();

		values.add(yvArray);

		int[] colors = new int[]{Color.BLACK, Color.RED, Color.BLUE};
		PointStyle[] styles = new PointStyle[]{PointStyle.CIRCLE, PointStyle.CIRCLE, PointStyle
				.CIRCLE};
		XYMultipleSeriesRenderer renderer = buildRenderer(colors, styles);
		renderer.setPointSize(0f);
		int length = renderer.getSeriesRendererCount();
		for (int i = 0; i < length; i++) {
			XYSeriesRenderer r = (XYSeriesRenderer) renderer.getSeriesRendererAt(i);
			r.setLineWidth(1.5f);
			r.setFillPoints(false);
			r.setDisplayChartValues(false);
		}

		int[] xlables = new int[4];
		int m = 0;
		for (int i = 0; i < 4; i++) {
			xlables[i] = m;
			m = m + mins;
		}

		// String[] xlables={"0","5","10","15"};

		int j = 0;
		for (int i = 0; i < xlables.length; i++) {
			if (xlables[i] == 0) {
				renderer.addXTextLabel(j, "");
			} else {
				renderer.addXTextLabel(j, "" + xlables[i]);
			}

			j = j + ((mins * 60) / secs);
		}

		double maxX = 3 * ((mins * 60) / secs);

		setChartSettings(renderer, "", "minutes", "ppm", 0, maxX, 0, 10, Color.LTGRAY, Color
				.WHITE);

		renderer = initRendererXYLabels(renderer);

		XYMultipleSeriesDataset seriesDataset = buildDataSet(titles, x, values);

		String[] types = new String[]{CubicLineChart.TYPE, CubicLineChart.TYPE, CubicLineChart
				.TYPE};// };

		Intent intent = ChartFactory.getCombinedXYChartIntent(activity, seriesDataset, renderer,
				types, "Weather parameters");

		renderer.setScale(1);

		XYSeries currentSeries = seriesDataset.getSeriesAt(0);

		//TODO set fields
		// renderer - renderer
		// seriesDataset - currentdataset
		// xySeries - currentSeries

		//TODO can get chart from intent
		//AbstractChart mChart = (AbstractChart) intent.getExtras().get("chart");

		return new GraphData(renderer, seriesDataset, intent, currentSeries);
	}

	public static GraphicalView attachXYChartIntoLayout(EToCMainActivity activity,
			AbstractChart mChart) {
		final LinearLayout chartLayout = (LinearLayout) activity.findViewById(R.id.chart);
        final LinearLayout allChartsLayout = (LinearLayout) activity.findViewById(R.id
                 .all_charts_layout);

		LinearLayout topContainer = (LinearLayout) activity.findViewById(R.id.top_container);

		int minHeight = topContainer.getMinimumHeight();

		if (minHeight == 0) {
			View textBelow = activity.findViewById(R.id.scroll_below_text);

			AutoExpandKeyboardUtils.expand(activity, topContainer, activity.findViewById(R.id
					.bottom_fragment), activity.getToolbar() ,textBelow);
            allChartsLayout.getLayoutParams().height = topContainer.getMinimumHeight();
		}

		GraphicalView mChartView = new GraphicalView(activity, mChart);
		chartLayout.removeAllViews();
        chartLayout.addView(mChartView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams
				.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
		mChartView.repaint();

		// TODO set fields
		// mChartView - mChartView
		return mChartView;
	}

	public static Intent createTimeChart(EToCMainActivity activity) {
		final long HOUR = 3600 * 1000;

		final long DAY = HOUR * 24;

		final int HOURS = 24;

		String[] titles = new String[]{"Response %"};
		long now = Math.round(new Date().getTime() / DAY) * DAY;
		List<Date[]> x = new ArrayList<Date[]>();
		for (int i = 0; i < titles.length; i++) {
			Date[] dates = new Date[HOURS];
			for (int j = 0; j < HOURS; j++) {
				dates[j] = new Date(now - (HOURS - j) * HOUR);
			}
			x.add(dates);
		}
		List<double[]> values = new ArrayList<>();

		values.add(new double[]{21.2, 21.5, 21.7, 21.5, 21.4, 21.4, 21.3, 21.1, 20.6, 20.3, 20.2,
				19.9, 19.7, 19.6, 19.9, 20.3, 20.6, 20.9, 21.2, 21.6, 21.9, 22.1, 21.7, 21.5});

		int[] colors = new int[]{Color.BLACK};
		PointStyle[] styles = new PointStyle[]{PointStyle.CIRCLE};
		XYMultipleSeriesRenderer renderer = buildRenderer(colors, styles);
		int length = renderer.getSeriesRendererCount();
		renderer.setPointSize(0f);
		for (int i = 0; i < length; i++) {
			XYSeriesRenderer r = (XYSeriesRenderer) renderer.getSeriesRendererAt(i);
			r.setLineWidth(1.0f);
			r.setFillPoints(false);
			r.setDisplayChartValues(false);
		}

		setChartSettings(renderer, "", "Time", "Response %", x.get(0)[0].getTime(), x.get(0)[HOURS
				- 1].getTime(), -5, 30, Color.LTGRAY, Color.WHITE);

		renderer = initRendererTimeLabels(renderer);

		Intent intent = ChartFactory.getTimeChartIntent(activity, buildDateDataSet(titles, x, values),
				renderer, "h:mm a");

		//TODO can get chart from intent
		//AbstractChart mChart = (AbstractChart) intent.getExtras().get("chart");

		return intent;
	}

	public static void attachTimeChartIntoLayout(EToCMainActivity activity, AbstractChart mChart) {
		final LinearLayout view = (LinearLayout) activity.findViewById(R.id.chart);
		GraphicalView mChartView = new GraphicalView(activity, mChart);
		// mChartView.addZoomListener(mZoomListener, true, false);
		view.addView(mChartView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams
				.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
		// to reflect margin
		// mChartView.repaint();
	}

	private static XYMultipleSeriesRenderer initRendererXYLabels(XYMultipleSeriesRenderer renderer) {
		renderer.setXLabels(0);
		renderer.setYLabels(15);
		renderer.setLabelsTextSize(13);
		renderer.setShowGrid(true);
        renderer.setShowCustomTextGrid(true);
		renderer.setGridColor(Color.rgb(136, 136, 136));
		renderer.setBackgroundColor(Color.WHITE);
		renderer.setApplyBackgroundColor(true);
		renderer.setMargins(new int[]{0, 60, 0, 0});
		renderer.setXLabelsAlign(Paint.Align.RIGHT);
		renderer.setYLabelsAlign(Paint.Align.RIGHT);
		renderer.setYLabelsColor(0, Color.rgb(0, 171, 234));
		renderer.setYLabelsVerticalPadding(-15);
		renderer.setXLabelsPadding(-5f);
		renderer.setZoomButtonsVisible(false);
		// renderer.setPanLimits(new double[] { -10, 20, -10, 40 });
		// renderer.setZoomLimits(new double[] { -10, 20, -10, 40 });
		renderer.setZoomEnabled(false, false);
		renderer.setPanEnabled(false, false);
		renderer.setZoomButtonsVisible(false);
		renderer.setShowLegend(false);
        renderer.setShowLabels(true);
		// renderer.setScale(1);
		return renderer;
	}

	public static XYMultipleSeriesRenderer initRendererTimeLabels(XYMultipleSeriesRenderer renderer) {
		renderer.setXLabels(4);
		renderer.setYLabels(10);
		renderer.setShowGrid(true);
        renderer.setShowCustomTextGrid(true);
		renderer.setGridColor(Color.rgb(136, 136, 136));
		renderer.setBackgroundColor(Color.WHITE);
		renderer.setApplyBackgroundColor(true);
		renderer.setMargins(new int[]{0, 35, 0, 0});
		renderer.setXLabelsAlign(Paint.Align.CENTER);
		renderer.setYLabelsAlign(Paint.Align.RIGHT);
		renderer.setYLabelsColor(0, Color.rgb(0, 171, 234));
		renderer.setYLabelsVerticalPadding(-15);
		renderer.setXLabelsPadding(-5f);
		renderer.setZoomButtonsVisible(false);
		// renderer.setPanLimits(new double[] { -10, 20, -10, 40 });
		// renderer.setZoomLimits(new double[] { -10, 20, -10, 40 });
		renderer.setZoomEnabled(false, false);
		renderer.setPanEnabled(false, false);
		renderer.setZoomButtonsVisible(false);
        renderer.setShowLegend(false);
        renderer.setShowLabels(true);

        renderer.setScale(1);

		return renderer;
	}

	/**
	 * Builds an XY multiple time dataset using the provided values.
	 *
	 * @param titles  the series titles
	 * @param xValues the values for the X axis
	 * @param yValues the values for the Y axis
	 * @return the XY multiple time dataset
	 */
	private static XYMultipleSeriesDataset buildDateDataSet(String[] titles, List<Date[]> xValues,
			List<double[]> yValues) {
		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
		int length = titles.length;
		for (int i = 0; i < length; i++) {
			TimeSeries series = new TimeSeries(titles[i]);
			Date[] xV = xValues.get(i);
			double[] yV = yValues.get(i);
			int seriesLength = xV.length;
			for (int k = 0; k < seriesLength; k++) {
				series.add(xV[k], yV[k]);
			}
			dataset.addSeries(series);
		}
		return dataset;
	}

	/**
	 * Builds an XY multiple series renderer.
	 *
	 * @param colors the series rendering colors
	 * @param styles the series point styles
	 * @return the XY multiple series renderers
	 */
	private static XYMultipleSeriesRenderer buildRenderer(int[] colors, PointStyle[] styles) {
		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
		addXYSeriesRenderer(renderer, colors, styles);
		return renderer;
	}

	private static void addXYSeriesRenderer(XYMultipleSeriesRenderer renderer, int[] colors,
			PointStyle[] styles) {
		renderer.setLabelsTextSize(13);
		renderer.setPointSize(5f);
		renderer.setMargins(new int[]{20, 30, 15, 20});
		int length = colors.length;
		for (int i = 0; i < length; i++) {
			XYSeriesRenderer r = new XYSeriesRenderer();
			r.setColor(colors[i]);
			r.setPointStyle(styles[i]);
			renderer.addSeriesRenderer(r);
		}
	}

	/**
	 * Sets a few of the series renderer settings.
	 *
	 * @param renderer    the renderer to set the properties to
	 * @param title       the chart title
	 * @param xTitle      the title for the X axis
	 * @param yTitle      the title for the Y axis
	 * @param xMin        the minimum value on the X axis
	 * @param xMax        the maximum value on the X axis
	 * @param yMin        the minimum value on the Y axis
	 * @param yMax        the maximum value on the Y axis
	 * @param axesColor   the axes color
	 * @param labelsColor the labels color
	 */
	private static void setChartSettings(XYMultipleSeriesRenderer renderer, String title, String
			xTitle, String yTitle, double xMin, double xMax, double yMin, double yMax, int
			axesColor, int labelsColor) {
		renderer.setChartTitle(title);
		renderer.setXTitle(xTitle);
		renderer.setYTitle(yTitle);
		renderer.setXAxisMin(xMin);
		renderer.setXAxisMax(xMax);
		renderer.setYAxisMin(yMin);
		renderer.setYAxisMax(yMax);
		renderer.setAxesColor(axesColor);
		renderer.setLabelsColor(labelsColor);
	}

    public static void clearYTextLabels(XYMultipleSeriesRenderer renderer) {
        renderer.setYAxisMin(0);
        renderer.setYAxisMax(10);
    }

	/**
	 * Builds an XY multiple dataset using the provided values.
	 *
	 * @param titles  the series titles
	 * @param xValues the values for the X axis
	 * @param yValues the values for the Y axis
	 * @return the XY multiple dataset
	 */
	private static XYMultipleSeriesDataset buildDataSet(String[] titles, List<double[]> xValues,
			List<double[]> yValues) {
		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
		addXYSeries(dataset, titles, xValues, yValues, 0);
		return dataset;
	}

	private static void addXYSeries(XYMultipleSeriesDataset dataset, String[] titles, List<double[]>
			xValues, List<double[]> yValues, int scale) {
		int length = titles.length;
		for (int i = 0; i < length; i++) {
			XYSeries series = new XYSeries(titles[i], scale);
			dataset.addSeries(series);
		}
	}
}
