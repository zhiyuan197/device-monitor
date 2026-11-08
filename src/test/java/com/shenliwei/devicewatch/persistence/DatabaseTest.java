package com.shenliwei.devicewatch.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shenliwei.devicewatch.domain.AlertRecord;
import com.shenliwei.devicewatch.domain.AlertRuleType;
import com.shenliwei.devicewatch.domain.AlertStatus;
import com.shenliwei.devicewatch.domain.TelemetrySample;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesSeedsAndAuthenticatesDemoAccounts() throws Exception {
        Path databasePath = temporaryDirectory.resolve("test.db");
        Path credentialsPath = temporaryDirectory.resolve("demo.properties");
        Database database = new Database(databasePath);
        database.initialize();

        DemoCredentials credentials = database.seedDemoData(
                2, "operator", "operator-password".toCharArray(), credentialsPath,
                "127.0.0.1", 5050);

        assertEquals(2, credentials.devices().size());
        assertTrue(Files.exists(credentialsPath));
        assertTrue(database.authenticateOperator("operator", "operator-password".toCharArray()));
        assertFalse(database.authenticateOperator("operator", "wrong".toCharArray()));

        DemoCredentials.DeviceSecret firstDevice = credentials.devices().get(0);
        assertTrue(database.authenticateDevice(
                firstDevice.deviceId(), firstDevice.credential().toCharArray()));
        assertFalse(database.authenticateDevice(
                firstDevice.deviceId(), "wrong".toCharArray()));
        assertEquals(2, database.listDevices().size());
    }

    @Test
    void storesTelemetryAndDeduplicatesActiveAlerts() throws Exception {
        Path databasePath = temporaryDirectory.resolve("alerts.db");
        Database database = new Database(databasePath);
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                1, "operator", "operator-password".toCharArray(), null, "127.0.0.1", 5050);
        String deviceId = credentials.devices().get(0).deviceId();
        long now = System.currentTimeMillis();

        database.insertTelemetry(
                new TelemetrySample(deviceId, now - 5, now, 45, 60, 52, null));
        List<TelemetrySample> history = database.queryTelemetry(deviceId, 0, now + 1, 100);

        assertEquals(1, history.size());
        assertEquals(45, history.get(0).cpuPercent());

        AlertRecord first =
                database.createAlert(deviceId, AlertRuleType.CPU, 85.0, now);
        AlertRecord duplicate =
                database.createAlert(deviceId, AlertRuleType.CPU, 86.0, now + 1);
        assertEquals(first.alertId(), duplicate.alertId());
        assertEquals(1, database.activeAlerts().size());

        AlertRecord acknowledged = database.acknowledgeAlert(first.alertId(), now + 5);
        assertEquals(AlertStatus.ACTIVE, acknowledged.status());
        assertNotNull(acknowledged.acknowledgedAt());

        AlertRecord resolved = database.resolveAlert(first.alertId(), now + 10);
        assertEquals(AlertStatus.RESOLVED, resolved.status());
        assertNotNull(resolved.resolvedAt());
        assertNotNull(resolved.acknowledgedAt());
        assertTrue(database.activeAlerts().isEmpty());
    }

    @Test
    void upgradesLegacyAlertTableWithoutLosingExistingRows() throws Exception {
        Path databasePath = temporaryDirectory.resolve("legacy.db");
        long triggeredAt = System.currentTimeMillis() - 5_000;
        try (Connection connection =
                     DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE devices (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        device_id TEXT NOT NULL UNIQUE,
                        display_name TEXT NOT NULL,
                        credential_hash TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    INSERT INTO devices(
                        device_id, display_name, credential_hash, enabled, created_at
                    ) VALUES ('DEV-001', '旧设备', 'unused', 1, 1)
                    """);
            statement.execute(
                    """
                    CREATE TABLE alerts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        device_id TEXT NOT NULL,
                        rule_type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        trigger_value REAL,
                        triggered_at INTEGER NOT NULL,
                        resolved_at INTEGER
                    )
                    """);
            statement.execute(
                    """
                    INSERT INTO alerts(
                        device_id, rule_type, status, trigger_value, triggered_at, resolved_at
                    ) VALUES ('DEV-001', 'CPU', 'RESOLVED', 85.0, %d, %d)
                    """.formatted(triggeredAt, triggeredAt + 1_000));
        }

        Database database = new Database(databasePath);
        database.initialize();

        List<AlertRecord> alerts = database.queryAlerts(
                Optional.of("DEV-001"),
                Optional.of(AlertRuleType.CPU),
                Optional.empty(),
                0,
                System.currentTimeMillis(),
                10);

        assertEquals(1, alerts.size());
        assertEquals(triggeredAt, alerts.get(0).triggeredAt());
        assertTrue(alerts.get(0).acknowledgedAt() == null);
    }
}

