package com.ismet.usbterminal.updated.data;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import static com.ismet.usbterminal.updated.data.PowerState.*;

@Retention(RetentionPolicy.SOURCE)
@IntDef({ON_STAGE1, ON_STAGE1_REPEAT, ON_STAGE2, ON_STAGE3, ON_STAGE4, ON, OFF_INTERRUPTING,
        OFF_STAGE1, OFF_WAIT_FOR_COOLING, OFF_FINISHING, OFF})
public @interface PowerState {
    int ON_STAGE1 = 1;
    int ON_STAGE1_REPEAT = 2;
    int ON_STAGE2 = 3;
    int ON_STAGE3 = 4;
    int ON_STAGE4 = 5;
    int ON = 6;
    int OFF_INTERRUPTING = 7;
    int OFF_STAGE1 = 8;
    int OFF_WAIT_FOR_COOLING = 9;
    int OFF_FINISHING = 10;
    int OFF = 11;
}
