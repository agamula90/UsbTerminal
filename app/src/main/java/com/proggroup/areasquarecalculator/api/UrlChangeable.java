package com.proggroup.areasquarecalculator.api;

import android.widget.FrameLayout;
import android.widget.ProgressBar;

public interface UrlChangeable {

    void setUrl(String url);

    String getUrl();

    void execute();

    FrameLayout getFrameLayout();

    void setProgressBar(ProgressBar progressBar);
}
