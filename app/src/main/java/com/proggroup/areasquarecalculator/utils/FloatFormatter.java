package com.proggroup.areasquarecalculator.utils;

import java.util.Locale;

public class FloatFormatter {
    /**
     * Standardized way to format float values.
     *
     * @param val Value for format.
     * @return String representation of float value.
     */
    public static String format(float val) {
        return String.format(Locale.US, "%.4f", val);
    }
}
