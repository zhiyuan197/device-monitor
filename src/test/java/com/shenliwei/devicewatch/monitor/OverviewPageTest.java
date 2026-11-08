package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class OverviewPageTest {

    @Test
    void averageLatencyUsesLatestKnownValuesAndIgnoresMissingData() {
        DeviceView first = device("DEV-001", 120);
        DeviceView second = device("DEV-002", 240);
        DeviceView offline = device("DEV-003", 0);
        assertEquals(180, OverviewPage.averageLatencyMillis(List.of(first, second)));
        assertEquals(0, OverviewPage.averageLatencyMillis(List.of(offline)));
        assertEquals(0, OverviewPage.averageLatencyMillis(List.of()));
    }

    private static DeviceView device(String id, long latencyMillis) {
        return new DeviceView(
                id, id, "", true, true, 0, 0,
                null, null, null, null, new DeviceRulesView(80, 75, 3, 85, 78, 3, 75, 70),
                latencyMillis, 0, false);
    }
}
