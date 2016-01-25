package com.ismet.usbterminal.updated.data;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import static com.ismet.usbterminal.updated.data.PowerState.*;

@Retention(RetentionPolicy.SOURCE)
@IntDef({ON, OFF})
public @interface PowerState {
    int ON = 1;
    int OFF = 2;
}
