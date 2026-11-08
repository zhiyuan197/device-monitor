package com.shenliwei.devicewatch.simulator;

/**
 * Development probe: prints a real host sample to prove the REAL
 * simulator scenario reads live machine metrics.
 */
public final class HostMetricsProbe {
    private HostMetricsProbe() {
    }

    public static void main(String[] args) {
        HostMetrics metrics = HostMetrics.local();
        HostMetrics.Sample sample = metrics.read(System.currentTimeMillis());
        System.out.printf(
                "HOST SAMPLE cpu=%.1f%% memory=%.1f%% temperature=%.1f C%n",
                sample.cpuPercent(), sample.memoryPercent(), sample.temperatureCelsius());
    }
}
