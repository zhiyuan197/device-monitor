package com.shenliwei.devicewatch.monitor;

public record DeviceView(
        String deviceId,
        String displayName,
        String location,
        boolean enabled,
        boolean online,
        long lastSeenAt,
        long createdAt,
        Double cpuPercent,
        Double memoryPercent,
        Double temperatureCelsius,
        Double diskPercent,
        DeviceRulesView rules,
        long latencyMillis,
        double networkKbps,
        boolean real) {
}

