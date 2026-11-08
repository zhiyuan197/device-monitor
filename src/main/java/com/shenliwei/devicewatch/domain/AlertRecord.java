package com.shenliwei.devicewatch.domain;

public record AlertRecord(
        long alertId,
        String deviceId,
        AlertRuleType ruleType,
        AlertSeverity severity,
        AlertStatus status,
        Double triggerValue,
        long triggeredAt,
        Long resolvedAt,
        Long acknowledgedAt,
        String acknowledgedBy,
        String ackComment) {

    public AlertRecord {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
    }
}

