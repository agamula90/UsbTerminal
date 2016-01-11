package com.proggroup.areasquarecalculator.views;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class SquareLayout extends FrameLayout {

    public SquareLayout(Context context) {
        this(context, null);
    }

    public SquareLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SquareLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        int squareLen = width;
        if(squareLen == 0) {
            squareLen = height;
        } else if (height < squareLen) {
            squareLen = height;
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(squareLen, MeasureSpec.EXACTLY), MeasureSpec
                .makeMeasureSpec(squareLen, MeasureSpec.EXACTLY));
    }
}
