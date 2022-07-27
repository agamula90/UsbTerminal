package com.ismet.usbterminal.utils;

import org.achartengine.chart.CombinedXYChart;
import org.achartengine.chart.CubicLineChart;
import org.achartengine.chart.XYChart;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;

public class GraphData {
	public final XYMultipleSeriesRenderer renderer;
	public final XYMultipleSeriesDataset seriesDataset;
	public final XYSeries xySeries;

	public GraphData(XYMultipleSeriesRenderer renderer, XYMultipleSeriesDataset seriesDataset, XYSeries xySeries) {
		this.renderer = renderer;
		this.seriesDataset = seriesDataset;
		this.xySeries = xySeries;
	}

    public XYChart createChart() {
        String[] types = new String[]{CubicLineChart.TYPE, CubicLineChart.TYPE, CubicLineChart
                .TYPE};
        return new CombinedXYChart(seriesDataset, renderer, types);
    }
}
