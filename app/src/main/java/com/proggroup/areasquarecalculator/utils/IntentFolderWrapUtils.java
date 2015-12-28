package com.proggroup.areasquarecalculator.utils;

import android.app.Activity;
import android.content.Intent;

import com.lamerman.FileDialog;
import com.proggroup.areasquarecalculator.api.LibraryContentAttachable;

public class IntentFolderWrapUtils {
    private IntentFolderWrapUtils() {
    }

    public static void wrapFolderForDrawables(Activity activity, Intent intent) {
        if(activity instanceof LibraryContentAttachable) {
            LibraryContentAttachable attachable = (LibraryContentAttachable) activity;
            intent.putExtra(FileDialog.FOLDER_DRAWABLE_RESOURCE, attachable.getFolderDrawable());
            intent.putExtra(FileDialog.FILE_DRAWABLE_RESOURCE, attachable.getFileDrawable());
        }
    }
}
