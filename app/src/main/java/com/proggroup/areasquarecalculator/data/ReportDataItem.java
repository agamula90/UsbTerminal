package com.proggroup.areasquarecalculator.data;

import android.graphics.Color;

public class ReportDataItem {

    private int mForegroundColor = Color.TRANSPARENT;
    private boolean mIsBold;
    private @FontTextSize int mFontSize;
    private String mText;
    private boolean mAutoAddBreak = true;

    public ReportDataItem(@FontTextSize int fontSize, String text) {
        this.mFontSize = fontSize;
        this.mText = text;
    }

    public ReportDataItem(@FontTextSize int fontSize, String text, boolean isBold) {
        this.mFontSize = fontSize;
        this.mText = text;
        this.mIsBold = isBold;
    }

    public ReportDataItem(@FontTextSize int fontSize, String text, int foregroundColor, boolean
            isBold) {
        this.mFontSize = fontSize;
        this.mText = text;
        this.mIsBold = isBold;
        this.mForegroundColor = foregroundColor;
    }

    public void setAutoAddBreak(boolean autoAddBreak) {
        this.mAutoAddBreak = autoAddBreak;
    }

    public @FontTextSize int getFontSize() {
        return mFontSize;
    }

    public int getForegroundColor() {
        return mForegroundColor;
    }

    public String getText() {
        return mText;
    }

    public boolean isBold() {
        return mIsBold;
    }

    public boolean isAutoAddBreak() {
        return mAutoAddBreak;
    }
}
