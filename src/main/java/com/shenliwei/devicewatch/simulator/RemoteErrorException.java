package com.shenliwei.devicewatch.simulator;

public final class RemoteErrorException extends Exception {
    private final String code;

    public RemoteErrorException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

