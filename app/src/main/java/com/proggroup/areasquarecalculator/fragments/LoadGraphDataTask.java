package com.proggroup.areasquarecalculator.fragments;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Pair;
import android.widget.Toast;

import com.proggroup.areasquarecalculator.UrlChangeable;
import com.proggroup.areasquarecalculator.utils.CalculatePpmUtils;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * This class return data from file: list of ppms as 1-st argument, and list of avg values as 2-nd
 */
public class LoadGraphDataTask extends AsyncTask<Void, Void, Pair<List<Float>, List<Float>>>
        implements UrlChangeable{

    private String dataPath;
    private final WeakReference<Activity> activityWeak;
    private final WeakReference<OnGraphDataLoadedCallback> onGraphDataLoadedCallbackWeak;

    public LoadGraphDataTask(Activity activity, String dataPath, OnGraphDataLoadedCallback
             onGraphDataLoadedCallback) {
        this.dataPath = dataPath;
        this.activityWeak = new WeakReference<>(activity);
        if(onGraphDataLoadedCallback != null) {
            this.onGraphDataLoadedCallbackWeak = new WeakReference<>(onGraphDataLoadedCallback);
        } else {
            this.onGraphDataLoadedCallbackWeak = null;
        }
    }

    /**
     * This field will be set when mes auto calculations accord to data must be doing.
     */
    private String mesFolder;

    public void setMesFolder(String mesFolder) {
        this.mesFolder = mesFolder;
    }

    @Override
    protected Pair<List<Float>, List<Float>> doInBackground(Void... params) {
        if(activityWeak.get() == null) {
            return null;
        }

        Pair<List<Float>, List<Float>> res = CalculatePpmUtils.parseGraphDataFromFile(dataPath,
                activityWeak.get());

        if (res == null) {
            return null;
        }

        return res;
    }

    @Override
    protected void onPostExecute(Pair<List<Float>, List<Float>> res) {
        super.onPostExecute(res);
        if (res == null) {
            if(activityWeak.get() != null) {
                Toast.makeText(activityWeak.get(), "You select wrong file", Toast
                        .LENGTH_LONG).show();
            }
            return;
        }

        if(onGraphDataLoadedCallbackWeak != null && onGraphDataLoadedCallbackWeak.get() != null) {
            onGraphDataLoadedCallbackWeak.get().onGraphDataLoaded(res.first, res.second,
                    mesFolder != null, mesFolder, getUrl());
        }
    }

    @Override
    public void setUrl(String url) {
        this.dataPath = url;
    }

    @Override
    public String getUrl() {
        return dataPath;
    }

    @Override
    public void execute() {
        super.execute();
    }

    public interface OnGraphDataLoadedCallback {
        void onGraphDataLoaded(List<Float> ppmValues, List<Float> avgSquareValues, boolean
                isAutoLoading, String mesFolder, String url);
    }
}
