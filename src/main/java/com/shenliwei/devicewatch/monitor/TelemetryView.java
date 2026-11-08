package com.shenliwei.devicewatch.monitor;

public record TelemetryView(
        String deviceId,
        long collectedAt,
        long receivedAt,
        double cpuPercent,
        double memoryPercent,
        double temperatureCelsius,
        Double diskPercent) {
}

