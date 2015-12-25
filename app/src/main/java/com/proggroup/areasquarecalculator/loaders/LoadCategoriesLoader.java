package com.proggroup.areasquarecalculator.loaders;

import android.content.res.Resources;
import android.support.v4.content.AsyncTaskLoader;

import com.proggroup.areasquarecalculator.InterpolationCalculator;
import com.proggroup.areasquarecalculator.R;

import java.util.ArrayList;
import java.util.List;

public class LoadCategoriesLoader extends AsyncTaskLoader<List<String>> {

    private List<String> res;

    public LoadCategoriesLoader() {
        super(InterpolationCalculator.getInstance().getApplicationContext());
    }

    @Override
    public List<String> loadInBackground() {
        Resources resources = getContext().getResources();
        res = new ArrayList<>();
        res.add(resources.getString(R.string.test_calculate_square));
        res.add(resources.getString(R.string.test_measure_material));
        return res;
    }

    @Override
    public void deliverResult(List<String> res) {
        if (isReset()) {
            return;
        }

        if (isStarted()) {
            super.deliverResult(res);
        }
    }

    @Override
    protected void onStartLoading() {
        if (res != null) {
            deliverResult(res);
        }
        if (takeContentChanged() || res == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        res.clear();
        res = null;
    }
}
