package com.ismet.usbterminal.data;

public class TemperatureData {

    private int mFirstDogValue = -1;
    private int mHeaterOn = -1;
    private int mBracketValue1 = -1, mBracketValue2 = -1, mBracketValue3 = -1, mBracketValue4 = -1;
    private int mCurrentTemperature = -1;
    private int mTemperature1 = -1, mTemperature2 = -1, mTemperature3 = -1, mTemperature4 = -1;
    private boolean mCorrect = false;
	private int wrongPosition = -1;

    private TemperatureData() {

    }

    public static synchronized TemperatureData parse(String value) {
        TemperatureData temperatureData = new TemperatureData();
        if (!value.startsWith("@")) {
	        temperatureData.wrongPosition = 0;
            return temperatureData;
        }
        value = value.substring(1);
        String splitCommas[] = value.split(",");
        if (splitCommas.length != 10) {
	        temperatureData.wrongPosition = 1;
            return temperatureData;
        }
        try {
	        temperatureData.wrongPosition = 2;
            temperatureData.mFirstDogValue = Integer.parseInt(splitCommas[0]);
            String splitBracket[] = splitCommas[1].split("\\(");
            if (splitBracket.length != 2) {
	            temperatureData.wrongPosition = 3;
                throw new NumberFormatException();
            }

	        temperatureData.wrongPosition = 4;
            temperatureData.mHeaterOn = Integer.parseInt(splitBracket[0]);
	        temperatureData.wrongPosition = 5;
            temperatureData.mBracketValue1 = Integer.parseInt(splitBracket[1]);

	        temperatureData.wrongPosition = 6;
            temperatureData.mBracketValue2 = Integer.parseInt(splitCommas[2]);
	        temperatureData.wrongPosition = 7;
            temperatureData.mBracketValue3 = Integer.parseInt(splitCommas[3]);

            if (!splitCommas[4].endsWith(")")) {
	            temperatureData.wrongPosition = 8;
                throw new NumberFormatException();
            }

	        temperatureData.wrongPosition = 9;
            temperatureData.mBracketValue4 = Integer.parseInt(splitCommas[4].substring
                    (0, splitCommas[4].length() - 1));

	        temperatureData.wrongPosition = 9;
            temperatureData.mCurrentTemperature = Integer.parseInt(splitCommas[5]);
	        temperatureData.wrongPosition = 10;
            temperatureData.mTemperature1 = Integer.parseInt(splitCommas[6]);
	        temperatureData.wrongPosition = 11;
            temperatureData.mTemperature2 = Integer.parseInt(splitCommas[7]);
	        temperatureData.wrongPosition = 12;
            temperatureData.mTemperature3 = Integer.parseInt(splitCommas[8]);
	        temperatureData.wrongPosition = 13;
            //temperatureData.mTemperature4 = Integer.parseInt(splitCommas[9]);
        } catch (NumberFormatException e) {
            return temperatureData;
        }
	    temperatureData.wrongPosition = 0;
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

	public int getWrongPosition() {
		return wrongPosition;
	}
}
