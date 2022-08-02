package com.ismet.usbterminal.data

enum class PowerState {
    ON_STAGE1,// = 1
    ON_STAGE1_REPEAT,// = 2
    ON_STAGE2B,// = 13
    ON_STAGE2,// = 3
    ON_STAGE3,// = 4
    ON_STAGE3A,// = 17
    ON_STAGE3B,// = 18
    ON_STAGE4,// = 5
    ON_RUNNING,// = 14
    ON,// = 6
    OFF_INTERRUPTING,// = 7
    OFF_STAGE1,// = 8
    OFF_WAIT_FOR_COOLING,// = 9
    OFF_FINISHING,// = 10
    OFF_RUNNING,// = 15
    INITIAL,// = 16
    OFF;// = 11
}