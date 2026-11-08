package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReconnectSchedulerTest {

    @Test
    void delayDoublesAndCapsAtThirtySeconds() {
        assertEquals(1_000L, ReconnectScheduler.delayMillis(1));
        assertEquals(2_000L, ReconnectScheduler.delayMillis(2));
        assertEquals(4_000L, ReconnectScheduler.delayMillis(3));
        assertEquals(8_000L, ReconnectScheduler.delayMillis(4));
        assertEquals(16_000L, ReconnectScheduler.delayMillis(5));
        assertEquals(30_000L, ReconnectScheduler.delayMillis(6));
        assertEquals(30_000L, ReconnectScheduler.delayMillis(100));
    }
}
