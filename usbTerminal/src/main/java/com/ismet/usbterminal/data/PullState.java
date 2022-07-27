package com.ismet.usbterminal.data;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import static com.ismet.usbterminal.data.PullState.*;

import kotlin.Deprecated;

@Retention(RetentionPolicy.SOURCE)
@IntDef({NONE, TEMPERATURE, CO2})
@Deprecated(message = "Use MainViewModel instead")
public @interface PullState {
    int NONE = 0;
    int TEMPERATURE = 1;
    int CO2 = 2;
}
