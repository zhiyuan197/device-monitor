package com.shenliwei.devicewatch.monitor;

import com.shenliwei.devicewatch.protocol.MessageObserver;

/**
 * One observed protocol frame: direction, message type, byte size and time.
 */
public record MessageEvent(
        MessageObserver.Direction direction,
        String type,
        int bytes,
        long timestampMillis) {
}
