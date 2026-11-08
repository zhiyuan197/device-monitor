package com.shenliwei.devicewatch.domain;

public record DeviceCredential(
        String deviceId, String displayName, String credentialHash, boolean enabled) {
}

