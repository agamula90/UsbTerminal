package com.ismet.usbterminal.updated.data;

public class TemperatureData {

    private int mFirstDogValue = -1;
    private int mHeaterOn = -1;
    private int mBracketValue1 = -1, mBracketValue2 = -1, mBracketValue3 = -1, mBracketValue4 = -1;
    private int mCurrentTemperature = -1;
    private int mTemperature1 = -1, mTemperature2 = -1, mTemperature3 = -1, mTemperature4 = -1;
    private boolean mCorrect = false;

    private TemperatureData() {

    }

    public static TemperatureData parse(String value) {
        TemperatureData temperatureData = new TemperatureData();
        if (!value.startsWith("@")) {
            return temperatureData;
        }
        value = value.substring(1);
        String splitCommas[] = value.split(",");
        if (splitCommas.length != 10) {
            return temperatureData;
        }
        try {
            temperatureData.mFirstDogValue = Integer.parseInt(splitCommas[0]);
            String splitBracket[] = splitCommas[1].split("\\(");
            if (splitBracket.length != 2) {
                throw new NumberFormatException();
            }

            temperatureData.mHeaterOn = Integer.parseInt(splitBracket[0]);
            temperatureData.mBracketValue1 = Integer.parseInt(splitBracket[1]);

            temperatureData.mBracketValue2 = Integer.parseInt(splitCommas[2]);
            temperatureData.mBracketValue3 = Integer.parseInt(splitCommas[3]);

            if (!splitCommas[4].endsWith(")")) {
                throw new NumberFormatException();
            }

            temperatureData.mBracketValue4 = Integer.parseInt(splitCommas[4].substring
                    (0, splitCommas[4].length() - 1));

            temperatureData.mCurrentTemperature = Integer.parseInt(splitCommas[5]);
            temperatureData.mTemperature1 = Integer.parseInt(splitCommas[6]);
            temperatureData.mTemperature2 = Integer.parseInt(splitCommas[7]);
            temperatureData.mTemperature3 = Integer.parseInt(splitCommas[8]);
            temperatureData.mTemperature4 = Integer.parseInt(splitCommas[9]);
        } catch (NumberFormatException e) {
            return temperatureData;
        }
        temperatureData.mCorrect = true;
        return temperatureData;
    }

    public int getFirstDogValue() {
        return mFirstDogValue;
    }

    public int getHeaterOn() {
        return mHeaterOn;
    }

    public int getBracketValue1() {
        return mBracketValue1;
    }

    public int getBracketValue2() {
        return mBracketValue2;
    }

    public int getBracketValue3() {
        return mBracketValue3;
    }

    public int getBracketValue4() {
        return mBracketValue4;
    }

    public int getCurrentTemperature() {
        return mCurrentTemperature;
    }

    public int getTemperature1() {
        return mTemperature1;
    }

    public int getTemperature2() {
        return mTemperature2;
    }

    public int getTemperature3() {
        return mTemperature3;
    }

    public int getTemperature4() {
        return mTemperature4;
    }

    public boolean isCorrect() {
        return mCorrect;
    }
}
