package com.shenliwei.devicewatch.protocol;

public final class ProtocolException extends Exception {
    private final String code;
    private final boolean fatal;

    public ProtocolException(String code, String message, boolean fatal) {
        super(message);
        this.code = code;
        this.fatal = fatal;
    }

    public String code() {
        return code;
    }

    public boolean fatal() {
        return fatal;
    }

    public static ProtocolException validation(String message) {
        return new ProtocolException("VALIDATION_FAILED", message, false);
    }

    public static ProtocolException invalidJson(String message) {
        return new ProtocolException("INVALID_JSON", message, true);
    }

    public static ProtocolException tooLarge(int maxBytes) {
        return new ProtocolException(
                "MESSAGE_TOO_LARGE", "Frame exceeds " + maxBytes + " bytes", true);
    }
}

