package com.ismet.usbterminal.main.data;

import java.util.List;

public class ReportData {
    private float ppm;

    private String measurementFolder;
    private List<String> measurementFiles;
    private List<Float> measurementAverages;
    private String calibrationCurveFolder;
    private List<Float> ppmData;
    private List<Float> avgData;
    private int countMeasurements;

    public void setPpm(float ppm) {
        this.ppm = ppm;
    }

    public float getPpm() {
        return ppm;
    }

    public void setMeasurementFolder(String measurementFolder) {
        this.measurementFolder = measurementFolder;
    }

    public String getMeasurementFolder() {
        return measurementFolder;
    }

    public void setMeasurementFiles(List<String> measurementFiles) {
        this.measurementFiles = measurementFiles;
    }

    public List<String> getMeasurementFiles() {
        return measurementFiles;
    }

    public List<Float> getMeasurementAverages() {
        return measurementAverages;
    }

    public void setMeasurementAverages(List<Float> measurementAverages) {
        this.measurementAverages = measurementAverages;
    }

    public void setCalibrationCurveFolder(String calibrationCurveFolder) {
        this.calibrationCurveFolder = calibrationCurveFolder;
    }

    public String getCalibrationCurveFolder() {
        return calibrationCurveFolder;
    }

    public void setPpmData(List<Float> ppmData) {
        this.ppmData = ppmData;
    }

    public List<Float> getPpmData() {
        return ppmData;
    }

    public void setAvgData(List<Float> avgData) {
        this.avgData = avgData;
    }

    public List<Float> getAvgData() {
        return avgData;
    }

    public void setCountMeasurements(int countMeasurements) {
        this.countMeasurements = countMeasurements;
    }

    public int getCountMeasurements() {
        return countMeasurements;
    }
}
