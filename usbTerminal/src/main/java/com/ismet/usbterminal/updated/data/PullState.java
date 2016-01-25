package com.ismet.usbterminal.updated.data;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import static com.ismet.usbterminal.updated.data.PullState.*;

@Retention(RetentionPolicy.SOURCE)
@IntDef({NONE, TEMPERATURE, CO2})
public @interface PullState {
    int NONE = 0;
    int TEMPERATURE = 1;
    int CO2 = 2;
}
