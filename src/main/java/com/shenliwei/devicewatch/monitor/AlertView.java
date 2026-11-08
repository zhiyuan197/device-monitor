package com.shenliwei.devicewatch.monitor;

public record AlertView(
        long alertId,
        String deviceId,
        String ruleType,
        String severity,
        String status,
        Double triggerValue,
        long triggeredAt,
        Long resolvedAt,
        Long acknowledgedAt,
        String acknowledgedBy,
        String ackComment) {
}

