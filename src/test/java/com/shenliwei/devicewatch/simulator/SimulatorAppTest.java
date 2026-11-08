package com.shenliwei.devicewatch.simulator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SimulatorAppTest {

    @Test
    void deviceSelectionWithinConfigIsAccepted() {
        assertDoesNotThrow(() -> SimulatorApp.validateSelection(3, 2, 5));
        assertDoesNotThrow(() -> SimulatorApp.validateSelection(0, 0, 5));
    }

    @Test
    void deviceSelectionBeyondConfigIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SimulatorApp.validateSelection(4, 2, 5));
        assertThrows(IllegalArgumentException.class,
                () -> SimulatorApp.validateSelection(-1, 0, 5));
        assertThrows(IllegalArgumentException.class,
                () -> SimulatorApp.validateSelection(1, -1, 5));
    }

    @Test
    void negativeSimulatedLatencyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SimulatorApp.validateLatency(-1));
        assertDoesNotThrow(() -> SimulatorApp.validateLatency(0));
        assertDoesNotThrow(() -> SimulatorApp.validateLatency(35));
    }
}
