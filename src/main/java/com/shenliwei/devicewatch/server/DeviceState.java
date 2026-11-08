package com.shenliwei.devicewatch.server;

import com.shenliwei.devicewatch.alert.AlertEngine;
import com.shenliwei.devicewatch.domain.AlertRecord;
import com.shenliwei.devicewatch.domain.AlertRuleType;
import com.shenliwei.devicewatch.domain.DeviceInfo;
import com.shenliwei.devicewatch.domain.DeviceRules;
import com.shenliwei.devicewatch.domain.TelemetrySample;
import java.util.EnumMap;
import java.util.Map;

final class DeviceState {
    private final String deviceId;
    private final String displayName;
    private String location;
    private boolean enabled;
    private long createdAt;
    private DeviceRules rules;
    private boolean online;
    private boolean real;
    private long lastSeenAt;
    private Double cpuPercent;
    private Double memoryPercent;
    private Double temperatureCelsius;
    private Double diskPercent;
    private long latencyMillis;
    private double networkKbps;
    private long lastPayloadBytes;
    private long lastPayloadAt;
    private AlertEngine.CpuCounter cpuCounter = AlertEngine.CpuCounter.initial();
    private AlertEngine.CpuCounter memoryCounter = AlertEngine.CpuCounter.initial();
    private final Map<AlertRuleType, AlertRecord> activeAlerts =
            new EnumMap<>(AlertRuleType.class);

    DeviceState(String deviceId, String displayName) {
        this.deviceId = deviceId;
        this.displayName = displayName;
    }

    static DeviceState from(DeviceInfo info) {
        DeviceState state = new DeviceState(info.deviceId(), info.displayName());
        state.location = info.location() == null ? "" : info.location();
        state.enabled = info.enabled();
        state.createdAt = info.createdAt();
        state.rules = info.rules();
        return state;
    }

    String deviceId() {
        return deviceId;
    }

    String displayName() {
        return displayName;
    }

    String location() {
        return location;
    }

    void location(String value) {
        location = value == null ? "" : value;
    }

    boolean enabled() {
        return enabled;
    }

    void enabled(boolean value) {
        enabled = value;
    }

    long createdAt() {
        return createdAt;
    }

    DeviceRules rules() {
        return rules;
    }

    void rules(DeviceRules value) {
        rules = value;
    }

    boolean online() {
        return online;
    }

    boolean real() {
        return real;
    }

    void real(boolean value) {
        real = value;
    }

    void online(boolean value) {
        online = value;
    }

    long lastSeenAt() {
        return lastSeenAt;
    }

    void lastSeenAt(long value) {
        lastSeenAt = value;
    }

    Double cpuPercent() {
        return cpuPercent;
    }

    Double memoryPercent() {
        return memoryPercent;
    }

    Double temperatureCelsius() {
        return temperatureCelsius;
    }

    Double diskPercent() {
        return diskPercent;
    }

    long latencyMillis() {
        return latencyMillis;
    }

    double networkKbps() {
        return networkKbps;
    }

    AlertEngine.CpuCounter cpuCounter() {
        return cpuCounter;
    }

    void cpuCounter(AlertEngine.CpuCounter value) {
        cpuCounter = value;
    }

    AlertEngine.CpuCounter memoryCounter() {
        return memoryCounter;
    }

    void memoryCounter(AlertEngine.CpuCounter value) {
        memoryCounter = value;
    }

    void applySample(TelemetrySample sample) {
        lastSeenAt = sample.receivedAt();
        cpuPercent = sample.cpuPercent();
        memoryPercent = sample.memoryPercent();
        temperatureCelsius = sample.temperatureCelsius();
        diskPercent = sample.diskPercent();
        latencyMillis = Math.max(0, sample.receivedAt() - sample.collectedAt());
        online = true;
    }

    /**
     * Approximates per-device upload throughput from consecutive message
     * sizes. Envelope overhead is estimated; documented as approximate.
     */
    void recordPayload(long payloadBytes, long now) {
        if (lastPayloadAt > 0 && now > lastPayloadAt) {
            double seconds = (now - lastPayloadAt) / 1000.0;
            double bytes = Math.max(0, payloadBytes - lastPayloadBytes);
            networkKbps = bytes * 8.0 / 1000.0 / Math.max(0.001, seconds);
        }
        lastPayloadBytes = payloadBytes;
        lastPayloadAt = now;
    }

    void applyHistoricalSample(TelemetrySample sample) {
        lastSeenAt = sample.receivedAt();
        cpuPercent = sample.cpuPercent();
        memoryPercent = sample.memoryPercent();
        temperatureCelsius = sample.temperatureCelsius();
        diskPercent = sample.diskPercent();
    }

    AlertRecord activeAlert(AlertRuleType ruleType) {
        return activeAlerts.get(ruleType);
    }

    void putActiveAlert(AlertRecord alert) {
        activeAlerts.put(alert.ruleType(), alert);
    }

    AlertRecord removeActiveAlert(AlertRuleType ruleType) {
        return activeAlerts.remove(ruleType);
    }

    Map<AlertRuleType, AlertRecord> activeAlerts() {
        return Map.copyOf(activeAlerts);
    }
}

