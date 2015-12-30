package com.proggroup.areasquarecalculator;

import android.os.AsyncTask;

import com.proggroup.areasquarecalculator.api.UrlChangeable;

public abstract class BaseLoadTask extends AsyncTask<Void, Void, Boolean> implements UrlChangeable {
    private String mUrl;

    public BaseLoadTask(String mUrl) {
        this.mUrl = mUrl;
    }

    @Override
    public void setUrl(String mUrl) {
        this.mUrl = mUrl;
    }

    @Override
    public String getUrl() {
        return mUrl;
    }

    @Override
    public void execute() {
        super.execute();
    }
}
