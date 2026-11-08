package com.shenliwei.devicewatch.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthCheckTrackerTest {

    @Test
    void failuresBelowThresholdStaySilent() {
        assertEquals(HealthCheckTracker.Action.NONE,
                HealthCheckTracker.evaluate(0, false, false, 3));
        assertEquals(HealthCheckTracker.Action.NONE,
                HealthCheckTracker.evaluate(1, false, false, 3));
    }

    @Test
    void thresholdFailureTriggersAlertOnce() {
        assertEquals(HealthCheckTracker.Action.FAIL,
                HealthCheckTracker.evaluate(2, false, false, 3));
        assertEquals(HealthCheckTracker.Action.NONE,
                HealthCheckTracker.evaluate(3, true, false, 3));
    }

    @Test
    void successAfterFailureRecovers() {
        assertEquals(HealthCheckTracker.Action.RECOVER,
                HealthCheckTracker.evaluate(2, true, true, 3));
    }

    @Test
    void successWithoutFailureIsQuiet() {
        assertEquals(HealthCheckTracker.Action.NONE,
                HealthCheckTracker.evaluate(0, false, true, 3));
    }
}
