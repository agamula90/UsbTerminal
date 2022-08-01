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
        CombinedXYChart.XYCombinedChartDef[] chartDefinitions = new CombinedXYChart.XYCombinedChartDef[] {
                new CombinedXYChart.XYCombinedChartDef(CubicLineChart.TYPE, 0),
                new CombinedXYChart.XYCombinedChartDef(CubicLineChart.TYPE, 1),
                new CombinedXYChart.XYCombinedChartDef(CubicLineChart.TYPE, 2)
        };
        return new CombinedXYChart(seriesDataset, renderer, chartDefinitions);
    }
}
