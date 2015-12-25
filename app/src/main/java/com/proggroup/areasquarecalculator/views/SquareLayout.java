package com.proggroup.areasquarecalculator.views;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class SquareLayout extends ViewGroup implements ViewGroup.OnHierarchyChangeListener{
    public SquareLayout(Context context) {
        this(context, null);
    }

    public SquareLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SquareLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
        setOnHierarchyChangeListener(this);
    }

    private void init() {
        layoutReady = false;
    }

    private boolean layoutReady;

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if(layoutReady) {
            getChildAt(0).layout(l, t, r, b);
        }
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
        if(layoutReady) {
            return;
        }
        super.addView(child, index, params);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(layoutReady) {
            getChildAt(0).draw(canvas);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if(layoutReady) {
            int min = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize
                    (heightMeasureSpec));

            View child = getChildAt(0);
            int childMeasureSpec = MeasureSpec.makeMeasureSpec(min, MeasureSpec.EXACTLY);

            child.measure(childMeasureSpec, childMeasureSpec);

            setMeasuredDimension(min, min);
            return;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void onChildViewAdded(View parent, View child) {
        layoutReady = true;
    }

    @Override
    public void onChildViewRemoved(View parent, View child) {
        layoutReady = false;
    }
}
