package com.ismet.usbterminal.utils;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.ismet.usbterminal.utils.FontTextSize.BIG_TEXT_SIZE;
import static com.ismet.usbterminal.utils.FontTextSize.HEADER_TITLE_SIZE;
import static com.ismet.usbterminal.utils.FontTextSize.MEDIUM_TEXT_SIZE;
import static com.ismet.usbterminal.utils.FontTextSize.NORMAL_TEXT_SIZE;

@Retention(RetentionPolicy.SOURCE)
@IntDef({HEADER_TITLE_SIZE, BIG_TEXT_SIZE, MEDIUM_TEXT_SIZE, NORMAL_TEXT_SIZE})
public @interface FontTextSize {
    int HEADER_TITLE_SIZE = 30;
    int BIG_TEXT_SIZE = 24;
    int MEDIUM_TEXT_SIZE = 18;
    int NORMAL_TEXT_SIZE = 14;
}
