package com.shenliwei.devicewatch.monitor;

public record ServerStatusView(
        String host,
        int port,
        long startedAt,
        long uptimeMillis,
        int deviceSessions,
        int monitorSessions,
        int registeredDevices,
        long telemetryCount,
        long activeAlerts,
        String databasePath,
        long databaseSizeBytes,
        boolean tlsEnabled,
        int tlsPort,
        String tlsCertSubject,
        long tlsHandshakeFailures) {
}
