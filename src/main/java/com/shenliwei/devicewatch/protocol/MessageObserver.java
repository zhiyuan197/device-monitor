package com.shenliwei.devicewatch.protocol;

/**
 * Optional observer of the protocol frames flowing through a client
 * connection. Used by the message inspector page.
 */
@FunctionalInterface
public interface MessageObserver {

    enum Direction {
        SENT,
        RECEIVED
    }

    void onMessage(Direction direction, String type, int bytes, long timestampMillis);
}
