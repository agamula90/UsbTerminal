package com.ismet.usbterminal.utils;

import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

import com.ismet.usbterminal.MainActivity;
import com.ismet.usbterminalnew.R;
import com.proggroup.areasquarecalculator.utils.AutoExpandKeyboardUtils;

import org.achartengine.GraphicalView;
import org.achartengine.chart.AbstractChart;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

public class GraphPopulatorUtils {

    public static GraphData createXYChart(int mins, int secs) {
        String[] titles = new String[]{"ppm", "ppm", "ppm"};

        int[] colors = new int[]{Color.BLACK, Color.RED, Color.BLUE};
        XYMultipleSeriesRenderer renderer = buildRenderer(colors);
        renderer.setPointSize(0f);
        int length = renderer.getSeriesRendererCount();
        for (int i = 0; i < length; i++) {
            XYSeriesRenderer r = (XYSeriesRenderer) renderer.getSeriesRendererAt(i);
            r.setLineWidth(1.5f);
            r.setFillPoints(false);
            r.setDisplayChartValues(false);
        }

        int[] xLabels = new int[4];
        int m = 0;
        for (int i = 0; i < 4; i++) {
            xLabels[i] = m;
            m = m + mins;
        }

        int j = 0;
        for (int xLabel : xLabels) {
            if (xLabel == 0) {
                renderer.addXTextLabel(j, "");
            } else {
                renderer.addXTextLabel(j, "" + xLabel);
            }

            j = j + ((mins * 60) / secs);
        }

        double maxX = 3 * ((mins * 60) / secs);

        setChartSettings(renderer, maxX);

        initRendererXYLabels(renderer);

        XYMultipleSeriesDataset seriesDataset = buildDataSet(titles);

        renderer.setScale(1);

        XYSeries currentSeries = seriesDataset.getSeriesAt(0);

        return new GraphData(renderer, seriesDataset, currentSeries);
    }

    public static GraphicalView attachXYChartIntoLayout(MainActivity activity,
                                                        AbstractChart mChart) {
        final LinearLayout chartLayout = activity.findViewById(R.id.chart);
        final View allChartsLayout = activity.findViewById(R.id.all_charts_layout);

        LinearLayout topContainer = activity.findViewById(R.id.top_container);

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
        return mChartView;
    }

    private static void initRendererXYLabels(XYMultipleSeriesRenderer renderer) {
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
		renderer.setZoomEnabled(false, false);
		renderer.setPanEnabled(false, false);
		renderer.setZoomButtonsVisible(false);
		renderer.setShowLegend(false);
        renderer.setShowLabels(true);
	}

    /**
	 * Builds an XY multiple series renderer.
	 *
	 * @param colors the series rendering colors
	 * @return the XY multiple series renderers
	 */
	private static XYMultipleSeriesRenderer buildRenderer(int[] colors) {
		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
		addXYSeriesRenderer(renderer, colors);
		return renderer;
	}

	private static void addXYSeriesRenderer(XYMultipleSeriesRenderer renderer, int[] colors) {
        for (int color : colors) {
            XYSeriesRenderer r = new XYSeriesRenderer();
            r.setColor(color);
            r.setPointStyle(PointStyle.CIRCLE);
            renderer.addSeriesRenderer(r);
        }
	}

	/**
	 * Sets a few of the series renderer settings.
     * @param renderer    the renderer to set the properties to
     * @param xMax        the maximum value on the X axis
     */
	private static void setChartSettings(XYMultipleSeriesRenderer renderer, double xMax) {
		renderer.setChartTitle("");
		renderer.setXTitle("minutes");
		renderer.setYTitle("ppm");
		renderer.setXAxisMin(0);
		renderer.setXAxisMax(xMax);
		clearYTextLabels(renderer);
		renderer.setAxesColor(Color.LTGRAY);
		renderer.setLabelsColor(Color.WHITE);
	}

    public static void clearYTextLabels(XYMultipleSeriesRenderer renderer) {
        renderer.setYAxisMin(0);
        renderer.setYAxisMax(10);
    }

	/**
	 * Builds an XY multiple dataset using the provided values.
	 *
	 * @param titles  the series titles
	 * @return the XY multiple dataset
	 */
	private static XYMultipleSeriesDataset buildDataSet(String[] titles) {
		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
		addXYSeries(dataset, titles);
		return dataset;
	}

	private static void addXYSeries(XYMultipleSeriesDataset dataset, String[] titles) {
        for (String title : titles) {
            XYSeries series = new XYSeries(title, 0);
            dataset.addSeries(series);
        }
	}
}
