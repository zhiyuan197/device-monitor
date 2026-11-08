package com.shenliwei.devicewatch.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SimulatedDeviceTest {

    @Test
    void collectedAtShiftsBackBySimulatedLatency() {
        assertEquals(1_000L, SimulatedDevice.collectedAt(1_035L, 35L));
        assertEquals(7_000L, SimulatedDevice.collectedAt(7_000L, 0L));
    }

    @Test
    void collectedAtNeverGoesNegative() {
        assertEquals(0L, SimulatedDevice.collectedAt(10L, 100L));
    }
}
