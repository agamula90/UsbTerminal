package com.proggroup.areasquarecalculator.api;

import android.view.View;
import android.widget.FrameLayout;

public interface UrlChangeable {

    void setUrl(String url);

    String getUrl();

    void execute();

    FrameLayout getFrameLayout();

    void setProgressBar(View progressBar);
}
