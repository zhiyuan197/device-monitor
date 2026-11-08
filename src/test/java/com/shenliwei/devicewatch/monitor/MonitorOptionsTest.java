package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MonitorOptionsTest {

    @Test
    void demoFlagsAreParsedIndependently() {
        MonitorOptions options = MonitorOptions.parse(new String[] {
            "--demo", "--demo-config", "config/demo.properties", "--demo-stop-on-exit"
        });
        assertTrue(options.demoMode());
        assertTrue(options.stopDemoOnExit());
        assertFalse(MonitorOptions.parse(new String[] {"--demo"}).stopDemoOnExit());
        assertFalse(MonitorOptions.parse(new String[0]).demoMode());
    }

    @Test
    void unknownOptionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MonitorOptions.parse(new String[] {"--bogus"}));
    }

    @Test
    void configOptionWithoutValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MonitorOptions.parse(new String[] {"--config"}));
    }
}
