package fr.xgouchet.androidlib.ui.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.io.File;

public interface ThumbnailProvider {

	Drawable getThumbnailForFile(Context context, File file);
}
