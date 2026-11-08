package com.shenliwei.devicewatch.persistence;

import com.shenliwei.devicewatch.domain.AlertRuleType;
import com.shenliwei.devicewatch.domain.AlertSeverity;

/**
 * An alert mutation persisted in the same transaction as a telemetry sample.
 */
public record AlertChange(
        AlertRuleType ruleType,
        AlertSeverity severity,
        Double triggerValue,
        Long alertIdToResolve,
        long eventAt) {

    public static AlertChange create(
            AlertRuleType ruleType, AlertSeverity severity, double triggerValue, long eventAt) {
        return new AlertChange(ruleType, severity, triggerValue, null, eventAt);
    }

    public static AlertChange resolve(AlertRuleType ruleType, long alertId, long eventAt) {
        return new AlertChange(ruleType, null, null, alertId, eventAt);
    }

    public boolean isCreate() {
        return alertIdToResolve == null;
    }
}

