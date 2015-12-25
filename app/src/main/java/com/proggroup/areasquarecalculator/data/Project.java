package com.proggroup.areasquarecalculator.data;

public class Project {
    public static final int TABLE_MAX_COLS_COUNT = 4;
    public static final int TABLE_MIN_ROWS_COUNT = 2;

    public static final String TABLE_NAME = "line_data";
    public static final String IS_SIMPLE_MEASURE = "_is_simple";
    public static final String ID = "project_id";

    private boolean isSimpleMeasure;
    private long id;

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setIsSimpleMeasure(boolean isSimpleMeasure) {
        this.isSimpleMeasure = isSimpleMeasure;
    }

    public boolean isSimpleMeasure() {
        return isSimpleMeasure;
    }
}
