package com.shenliwei.devicewatch.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HostMetricsTest {

    @Test
    void hostLoadAndMemoryBecomePercentages() {
        HostMetrics.Sample sample =
                HostMetrics.sampleFrom(
                        0.42, 8_000_000_000L, 2_000_000_000L, 500_000_000_000L,
                        1_000_000_000_000L, 0L);
        assertEquals(42.0, sample.cpuPercent(), 0.001);
        assertEquals(75.0, sample.memoryPercent(), 0.001);
        assertEquals(50.0, sample.diskPercent(), 0.001);
    }

    @Test
    void unavailableLoadFallsBackToZero() {
        HostMetrics.Sample sample =
                HostMetrics.sampleFrom(-1.0, 8_000_000_000L, 8_000_000_000L, 0L, 0L, 0L);
        assertEquals(0.0, sample.cpuPercent(), 0.001);
        assertEquals(0.0, sample.memoryPercent(), 0.001);
        assertEquals(0.0, sample.diskPercent(), 0.001);
    }

    @Test
    void syntheticTemperatureIsDeterministicAndInRange() {
        HostMetrics.Sample first =
                HostMetrics.sampleFrom(
                        0.5, 8_000_000_000L, 2_000_000_000L, 500_000_000_000L,
                        1_000_000_000_000L, 10_000L);
        HostMetrics.Sample second =
                HostMetrics.sampleFrom(
                        0.5, 8_000_000_000L, 2_000_000_000L, 500_000_000_000L,
                        1_000_000_000_000L, 10_000L);
        assertEquals(first.temperatureCelsius(), second.temperatureCelsius());
        assertTrue(first.temperatureCelsius() >= -40);
        assertTrue(first.temperatureCelsius() <= 125);
    }
}
