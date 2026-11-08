package com.shenliwei.devicewatch.monitor;

public record DeviceRulesView(
        double cpuTrigger,
        double cpuRecover,
        int cpuSamples,
        double memoryTrigger,
        double memoryRecover,
        int memorySamples,
        double temperatureTrigger,
        double temperatureRecover) {
}
