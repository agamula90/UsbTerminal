package com.proggroup.areasquarecalculator.api;

import android.view.View;
import android.widget.FrameLayout;

public interface UrlChangeable {

	String getUrl();

	void setUrl(String url);

	void execute();

	FrameLayout getFrameLayout();

	void setProgressBar(View progressBar);
}
