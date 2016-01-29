package com.ismet.usbterminal.updated.data;

public class PowerCommand {
    private final String command;
    private final long delay;

    public PowerCommand(String command, long delay) {
        this.command = command;
        this.delay = delay;
    }

    public String getCommand() {
        return command;
    }

    public long getDelay() {
        return delay;
    }
}
