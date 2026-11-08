package com.shenliwei.devicewatch.monitor;

/**
 * Server confirmation for an injected demo fault.
 */
public record DemoInjectionView(String fault, String deviceId, Long checkId) {
}
