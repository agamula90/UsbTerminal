package com.proggroup.areasquarecalculator.data;

import java.util.List;

public class AvgPoint {
    private final List<Float> values;
    private Float cachedAvg;

    public AvgPoint(List<Float> values) {
        this.values = values;
        cachedAvg = null;
    }

    /**
     *
     * @return Average value of all input fields.
     */
    public float avg() {
        if (cachedAvg == null) {
            float res = 0f;
            if (values == null || values.isEmpty()) {
                return res;
            }
            for (float square : values) {
                res += square;
            }
            res /= values.size();
            cachedAvg = res;
        }
        return cachedAvg;
    }
}
