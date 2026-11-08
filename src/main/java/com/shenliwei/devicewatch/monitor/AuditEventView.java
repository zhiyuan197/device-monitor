package com.shenliwei.devicewatch.monitor;

public record AuditEventView(
        long id,
        String actor,
        String action,
        String target,
        String detail,
        long createdAt) {
}
