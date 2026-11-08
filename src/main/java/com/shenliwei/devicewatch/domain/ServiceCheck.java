package com.shenliwei.devicewatch.domain;

public record ServiceCheck(
        long id,
        String name,
        String host,
        int port,
        int intervalMillis,
        int failThreshold,
        boolean enabled,
        long createdAt) {

    public ServiceCheck {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("service check name must not be blank");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("service check host must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("service check port must be 1-65535");
        }
        if (intervalMillis < 100) {
            throw new IllegalArgumentException("service check interval must be >= 100 ms");
        }
        if (failThreshold < 1) {
            throw new IllegalArgumentException("service check fail threshold must be >= 1");
        }
    }
}
