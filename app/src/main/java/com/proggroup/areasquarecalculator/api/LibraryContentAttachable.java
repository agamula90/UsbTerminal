package com.proggroup.areasquarecalculator.api;

import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.widget.FrameLayout;

public interface LibraryContentAttachable {
    FragmentManager getSupportFragmentManager();
    int getFragmentContainerId();
    DrawerLayout getDrawerLayout();
    int getToolbarId();
    int getLeftDrawerFragmentId();
    int getFolderDrawable();
    int getFileDrawable();
    FrameLayout getFrameLayout();
}
