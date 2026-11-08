package com.shenliwei.devicewatch.domain;

public record OperatorCredential(String username, String passwordHash, boolean enabled) {
}

