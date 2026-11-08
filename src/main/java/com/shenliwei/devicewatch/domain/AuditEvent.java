package com.shenliwei.devicewatch.domain;

/**
 * A recorded administrative or operational action.
 */
public record AuditEvent(
        long id,
        String actor,
        String action,
        String target,
        String detail,
        long createdAt) {
}
