package com.proggroup.areasquarecalculator.views;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.Utils;
import com.proggroup.areasquarecalculator.R;

public class ChartMarkerView extends MarkerView {

	private final TextView tvContent;
    private MPPointF offset;

	public ChartMarkerView(Context context, int layoutResource) {
		super(context, layoutResource);

		tvContent = findViewById(R.id.tvContent);
	}

	@Override
	public void refreshContent(Entry e, Highlight highlight) {
		if (e instanceof CandleEntry) {

			CandleEntry ce = (CandleEntry) e;

			tvContent.setText("" + Utils.formatNumber(ce.getHigh(), 0, true));
		} else {
            tvContent.setText("" + Utils.formatNumber(e.getY(), 0, true));
		}
	}

    @Override
    public MPPointF getOffsetForDrawingAtPoint(float posX, float posY) {
        return offset;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        offset = MPPointF.getInstance((left - right) / 2f, (top - bottom));
    }
}
