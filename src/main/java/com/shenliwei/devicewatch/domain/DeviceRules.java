package com.shenliwei.devicewatch.domain;

/**
 * Per-device alert thresholds. Any missing row falls back to the
 * server-wide defaults loaded from the application configuration.
 */
public record DeviceRules(
        double cpuTrigger,
        double cpuRecover,
        int cpuRequiredSamples,
        double memoryTrigger,
        double memoryRecover,
        int memoryRequiredSamples,
        double temperatureTrigger,
        double temperatureRecover) {

    public DeviceRules {
        if (cpuTrigger <= cpuRecover) {
            throw new IllegalArgumentException("cpuTrigger must exceed cpuRecover");
        }
        if (memoryTrigger <= memoryRecover) {
            throw new IllegalArgumentException("memoryTrigger must exceed memoryRecover");
        }
        if (temperatureTrigger <= temperatureRecover) {
            throw new IllegalArgumentException("temperatureTrigger must exceed temperatureRecover");
        }
        if (cpuRequiredSamples <= 0 || memoryRequiredSamples <= 0) {
            throw new IllegalArgumentException("required consecutive samples must be positive");
        }
    }
}
