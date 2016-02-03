package com.ismet.usbterminal.updated.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

public class PowerCommand implements Parcelable{
    private final String command;
    private final long delay;
	private final String possibleResponses[];

    public PowerCommand(String command, long delay, String... possibleResponses) {
        this.command = command;
        this.delay = delay;
	    this.possibleResponses = possibleResponses;
    }

    public String getCommand() {
        return command;
    }

    public long getDelay() {
        return delay;
    }

	public boolean hasSelectableResponses() {
		return possibleResponses != null && possibleResponses.length > 0;
	}

	public boolean isResponseCorrect(String response) {
		return !(possibleResponses != null && possibleResponses.length > 0) || Arrays.asList
				(possibleResponses).contains(response);
	}

	public String[] getPossibleResponses() {
		return possibleResponses;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(command);
		dest.writeLong(delay);

		int countResponses = possibleResponses != null && possibleResponses.length > 0 ?
				possibleResponses.length : -1;

		dest.writeInt(countResponses);

		if(countResponses > 0) {
			dest.writeStringArray(possibleResponses);
		}
	}

	public static final Creator<PowerCommand> CREATOR = new Creator<PowerCommand>() {

		@Override
		public PowerCommand createFromParcel(Parcel source) {
			String command = source.readString();
			long delay = source.readLong();
			int countResponses = source.readInt();

			final PowerCommand powerCommand;

			if(countResponses > 0) {
				String values[] = new String[countResponses];
				source.readStringArray(values);
				powerCommand = new PowerCommand(command, delay, values);
			} else {
				powerCommand = new PowerCommand(command, delay);
			}

			return powerCommand;
		}

		@Override
		public PowerCommand[] newArray(int size) {
			return new PowerCommand[size];
		}
	};
}
