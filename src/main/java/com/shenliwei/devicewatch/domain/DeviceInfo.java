package com.shenliwei.devicewatch.domain;

/**
 * Full administrative description of a device, including its alert rules.
 */
public record DeviceInfo(
        String deviceId,
        String displayName,
        String location,
        boolean enabled,
        long createdAt,
        DeviceRules rules) {

    public DeviceInfo {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
    }
}
