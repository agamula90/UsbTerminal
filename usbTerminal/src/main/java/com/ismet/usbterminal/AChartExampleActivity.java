package com.ismet.usbterminal;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.os.Bundle;
import android.os.Handler;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.ArrayList;
import java.util.List;

public class AChartExampleActivity extends Activity {

	private XYMultipleSeriesDataset dataset;

	private GraphicalView graphicalView;

	private double addX = 6;

	private double plus = 6;

	private double minus = 13;

	private Handler handler = new Handler();

	private Runnable updateRunnable = new Runnable() {

		@Override
		public void run() {
			dataset.getSeriesAt(0).add(addX, plus);
			dataset.getSeriesAt(1).add(addX, minus);
			addX++;
			plus++;
			minus--;
			graphicalView.repaint();
			if (addX < 20)
				handler.postDelayed(updateRunnable, 1000);
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		String[] titles = new String[]{"Blue", "Green"};
		List<double[]> x = new ArrayList<double[]>();
		for (int i = 0; i < titles.length; i++) {
			x.add(new double[]{1, 2, 3, 4, 5});
		}
		List<double[]> values = new ArrayList<double[]>();
		values.add(new double[]{1, 2, 3, 4, 5});
		values.add(new double[]{18, 17, 16, 15, 14});
		int[] colors = new int[]{Color.BLUE, Color.GREEN};
		PointStyle[] styles = new PointStyle[]{PointStyle.CIRCLE, PointStyle.DIAMOND};
		XYMultipleSeriesRenderer renderer = buildRenderer(colors, styles);
		int length = renderer.getSeriesRendererCount();
		for (int i = 0; i < length; i++) {
			((XYSeriesRenderer) renderer.getSeriesRendererAt(i)).setFillPoints(true);
		}
		setChartSettings(renderer, "Average temperature", "Horizontal axis", "Vertical axis", 0.5,
				12.5, -10, 40, Color.LTGRAY, Color.LTGRAY);
		renderer.setXLabels(12);
		renderer.setYLabels(10);
		renderer.setShowGrid(true);
		renderer.setXLabelsAlign(Align.RIGHT);
		renderer.setYLabelsAlign(Align.RIGHT);
		renderer.setZoomButtonsVisible(true);
		renderer.setPanLimits(new double[]{-10, 20, -10, 40});
		renderer.setZoomLimits(new double[]{-10, 20, -10, 40});

		dataset = buildDataset(titles, x, values);

		graphicalView = ChartFactory.getLineChartView(getApplicationContext(), dataset, renderer);

		setContentView(graphicalView);
		handler.postDelayed(updateRunnable, 1000);
	}

	private XYMultipleSeriesDataset buildDataset(String[] titles, List<double[]> xValues,
			List<double[]> yValues) {
		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
		addXYSeries(dataset, titles, xValues, yValues, 0);
		return dataset;
	}

	private void addXYSeries(XYMultipleSeriesDataset dataset, String[] titles, List<double[]>
			xValues, List<double[]> yValues, int scale) {
		int length = titles.length;
		for (int i = 0; i < length; i++) {
			XYSeries series = new XYSeries(titles[i], scale);
			double[] xV = xValues.get(i);
			double[] yV = yValues.get(i);
			int seriesLength = xV.length;
			for (int k = 0; k < seriesLength; k++) {
				series.add(xV[k], yV[k]);
			}
			dataset.addSeries(series);
		}
	}

	private XYMultipleSeriesRenderer buildRenderer(int[] colors, PointStyle[] styles) {
		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
		setRenderer(renderer, colors, styles);
		return renderer;
	}

	private void setRenderer(XYMultipleSeriesRenderer renderer, int[] colors, PointStyle[]
			styles) {
		renderer.setAxisTitleTextSize(16);
		renderer.setChartTitleTextSize(20);
		renderer.setLabelsTextSize(15);
		renderer.setLegendTextSize(15);
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

	private void setChartSettings(XYMultipleSeriesRenderer renderer, String title, String xTitle,
			String yTitle, double xMin, double xMax, double yMin, double yMax, int axesColor, int
			labelsColor) {
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
}
