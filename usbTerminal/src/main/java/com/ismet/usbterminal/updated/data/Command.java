package com.ismet.usbterminal.updated.data;

public class Command {
    private final int commandType;
    private final String command;

    public Command(int commandType, String command) {
        this.command = command;
        this.commandType = commandType;
    }

    public int getCommandType() {
        return commandType;
    }

    public String getCommand() {
        return command;
    }
}
