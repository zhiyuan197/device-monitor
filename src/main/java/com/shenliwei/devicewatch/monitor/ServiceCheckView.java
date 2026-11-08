package com.shenliwei.devicewatch.monitor;

public record ServiceCheckView(
        long checkId,
        String name,
        String host,
        int port,
        int intervalMillis,
        int failThreshold,
        boolean enabled,
        boolean online,
        int consecutiveFailures,
        long lastCheckedAt,
        long lastDurationMillis,
        double availabilityPercent) {
}
