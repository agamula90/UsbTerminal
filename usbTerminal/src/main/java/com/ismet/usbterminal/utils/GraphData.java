package com.ismet.usbterminal.utils;

import android.content.Intent;

import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;

public class GraphData {
	public final XYMultipleSeriesRenderer renderer;
	public final XYMultipleSeriesDataset seriesDataset;
	public final Intent intent;
	public final XYSeries xySeries;

	public GraphData(XYMultipleSeriesRenderer renderer, XYMultipleSeriesDataset seriesDataset,
			Intent intent, XYSeries xySeries) {
		this.renderer = renderer;
		this.seriesDataset = seriesDataset;
		this.intent = intent;
		this.xySeries = xySeries;
	}
}
