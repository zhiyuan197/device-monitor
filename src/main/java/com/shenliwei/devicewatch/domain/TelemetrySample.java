package com.shenliwei.devicewatch.domain;

public record TelemetrySample(
        String deviceId,
        long collectedAt,
        long receivedAt,
        double cpuPercent,
        double memoryPercent,
        double temperatureCelsius,
        Double diskPercent) {

    public TelemetrySample {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        if (cpuPercent < 0 || cpuPercent > 100) {
            throw new IllegalArgumentException("cpuPercent must be between 0 and 100");
        }
        if (memoryPercent < 0 || memoryPercent > 100) {
            throw new IllegalArgumentException("memoryPercent must be between 0 and 100");
        }
        if (temperatureCelsius < -40 || temperatureCelsius > 125) {
            throw new IllegalArgumentException("temperature must be between -40 and 125");
        }
        if (diskPercent != null && (diskPercent < 0 || diskPercent > 100)) {
            throw new IllegalArgumentException("diskPercent must be between 0 and 100");
        }
    }
}

