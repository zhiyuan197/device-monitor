package com.shenliwei.devicewatch.persistence;

import java.nio.file.Path;
import java.util.List;

public record DemoCredentials(
        String operatorUsername,
        String operatorPassword,
        List<DeviceSecret> devices,
        Path configurationFile) {

    public record DeviceSecret(String deviceId, String credential) {
    }
}

