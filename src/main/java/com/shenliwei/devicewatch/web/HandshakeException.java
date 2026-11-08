package com.shenliwei.devicewatch.web;

import java.io.IOException;

/**
 * RFC 6455 upgrade failure carrying the HTTP status to return to the client.
 */
public final class HandshakeException extends IOException {
    private final int statusCode;
    private final String reason;

    public HandshakeException(int statusCode, String reason) {
        super(reason);
        this.statusCode = statusCode;
        this.reason = reason;
    }

    public int statusCode() {
        return statusCode;
    }

    public String reason() {
        return reason;
    }
}
