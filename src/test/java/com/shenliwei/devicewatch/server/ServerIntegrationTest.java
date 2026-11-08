package com.shenliwei.devicewatch.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.domain.AlertRecord;
import com.shenliwei.devicewatch.domain.AlertRuleType;
import com.shenliwei.devicewatch.monitor.MonitorClient;
import com.shenliwei.devicewatch.monitor.MonitorListener;
import com.shenliwei.devicewatch.persistence.Database;
import com.shenliwei.devicewatch.persistence.DemoCredentials;
import com.shenliwei.devicewatch.simulator.DeviceClient;
import com.shenliwei.devicewatch.simulator.SimulatorConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void authenticatesProcessesTelemetryAlertsAndOfflineRecovery() throws Exception {
        Path databasePath = temporaryDirectory.resolve("integration.db");
        Database database = new Database(databasePath);
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                1, "monitor", "operator-password".toCharArray(), null, "127.0.0.1", 5050);
        DemoCredentials.DeviceSecret deviceAccount = credentials.devices().get(0);
        SimulatorConfig simulatorConfig = new SimulatorConfig(
                "127.0.0.1",
                5050,
                credentials.operatorUsername(),
                credentials.operatorPassword(),
                List.of(new SimulatorConfig.DeviceAccount(
                        deviceAccount.deviceId(), deviceAccount.credential())));

        AppConfig config = new AppConfig(
                "127.0.0.1",
                0,
                2_000,
                65_536,
                200,
                700,
                80,
                75,
                3,
                75,
                70,
                85,
                78,
                3,
                databasePath);

        MonitorServer server = new MonitorServer(config, database);
        CountDownLatch snapshotReady = new CountDownLatch(1);
        MonitorClient monitor = null;
        DeviceClient device = null;
        try {
            server.start();
            monitor = new MonitorClient(
                    "127.0.0.1",
                    server.port(),
                    credentials.operatorUsername(),
                    credentials.operatorPassword().toCharArray(),
                    new MonitorListener() {
                        @Override
                        public void onDeviceSnapshot(
                                List<com.shenliwei.devicewatch.monitor.DeviceView> devices) {
                            snapshotReady.countDown();
                        }
                    });
            monitor.connect();
            assertTrue(snapshotReady.await(2, TimeUnit.SECONDS));

            SimulatorConfig connectedConfig = simulatorConfig.withServer("127.0.0.1", server.port());
            device = DeviceClient.connect(connectedConfig, connectedConfig.devices().get(0));
            long sequence = 1;
            device.sendTelemetry(sequence++, System.currentTimeMillis(), 40, 50, 50);
            device.sendTelemetry(sequence++, System.currentTimeMillis(), 85, 50, 50);
            device.sendTelemetry(sequence++, System.currentTimeMillis(), 86, 50, 50);
            device.sendTelemetry(sequence++, System.currentTimeMillis(), 87, 50, 50);

            assertTrue(awaitCondition(
                    Duration.ofSeconds(2),
                    () -> hasActiveAlert(database, deviceAccount.deviceId(), AlertRuleType.CPU)));

            device.sendTelemetry(sequence++, System.currentTimeMillis(), 70, 50, 50);
            device.sendTelemetry(sequence++, System.currentTimeMillis(), 70, 50, 50);
            device.sendTelemetry(sequence++, System.currentTimeMillis(), 70, 50, 50);
            assertTrue(awaitCondition(
                    Duration.ofSeconds(2),
                    () -> !hasActiveAlert(database, deviceAccount.deviceId(), AlertRuleType.CPU)));

            long now = System.currentTimeMillis();
            var telemetryHistory =
                    monitor.queryTelemetry(deviceAccount.deviceId(), now - 60_000, now + 1_000, 100)
                            .get(2, TimeUnit.SECONDS);
            assertEquals(7, telemetryHistory.size());
            var alertHistory = monitor.queryAlerts(
                            deviceAccount.deviceId(), "CPU", null, now - 60_000, now + 1_000, 100)
                    .get(2, TimeUnit.SECONDS);
            assertEquals(1, alertHistory.size());
            assertEquals("RESOLVED", alertHistory.get(0).status());

            device.sendTelemetry(sequence++, System.currentTimeMillis(), 50, 50, 76);
            assertTrue(awaitCondition(
                    Duration.ofSeconds(2),
                    () -> hasActiveAlert(
                            database, deviceAccount.deviceId(), AlertRuleType.TEMPERATURE)));
            AlertRecord temperatureAlert = database.findActiveAlert(
                    deviceAccount.deviceId(), AlertRuleType.TEMPERATURE).orElseThrow();
            var acknowledgedTemperature =
                    monitor.acknowledgeAlert(temperatureAlert.alertId()).get(2, TimeUnit.SECONDS);
            assertTrue(acknowledgedTemperature.acknowledgedAt() != null);
            assertEquals("ACTIVE", acknowledgedTemperature.status());

            device.sendTelemetry(sequence++, System.currentTimeMillis(), 50, 50, 68);
            assertTrue(awaitCondition(
                    Duration.ofSeconds(2),
                    () -> !hasActiveAlert(
                            database, deviceAccount.deviceId(), AlertRuleType.TEMPERATURE)));

            device.close();
            device = null;
            assertTrue(awaitCondition(
                    Duration.ofSeconds(3),
                    () -> hasActiveAlert(
                            database, deviceAccount.deviceId(), AlertRuleType.OFFLINE)));

            device = DeviceClient.connect(connectedConfig, connectedConfig.devices().get(0));
            DeviceClient reconnected = device;
            assertTrue(awaitCondition(
                    Duration.ofSeconds(2),
                    () -> !hasActiveAlert(
                            database, deviceAccount.deviceId(), AlertRuleType.OFFLINE)));
            reconnected.sendHeartbeat(999);
        } finally {
            if (device != null) {
                device.close();
            }
            if (monitor != null) {
                monitor.close();
            }
            server.close();
        }
    }

    @Test
    void telemetryLatencyIsBroadcastToMonitors() throws Exception {
        Path databasePath = temporaryDirectory.resolve("latency.db");
        Database database = new Database(databasePath);
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                1, "monitor", "operator-password".toCharArray(), null, "127.0.0.1", 5050);
        DemoCredentials.DeviceSecret deviceAccount = credentials.devices().get(0);
        SimulatorConfig simulatorConfig = new SimulatorConfig(
                "127.0.0.1",
                5050,
                credentials.operatorUsername(),
                credentials.operatorPassword(),
                List.of(new SimulatorConfig.DeviceAccount(
                        deviceAccount.deviceId(), deviceAccount.credential())));

        AppConfig config = new AppConfig(
                "127.0.0.1",
                0,
                2_000,
                65_536,
                200,
                700,
                80,
                75,
                3,
                75,
                70,
                85,
                78,
                3,
                databasePath);

        MonitorServer server = new MonitorServer(config, database);
        java.util.concurrent.atomic.AtomicReference<com.shenliwei.devicewatch.monitor.DeviceView>
                latest = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch updateReady = new CountDownLatch(1);
        MonitorClient monitor = null;
        DeviceClient device = null;
        try {
            server.start();
            monitor = new MonitorClient(
                    "127.0.0.1",
                    server.port(),
                    credentials.operatorUsername(),
                    credentials.operatorPassword().toCharArray(),
                    new MonitorListener() {
                        @Override
                        public void onDeviceUpdate(
                                com.shenliwei.devicewatch.monitor.DeviceView device) {
                            if (device.latencyMillis() > 0) {
                                latest.set(device);
                                updateReady.countDown();
                            }
                        }
                    });
            monitor.connect();

            SimulatorConfig connectedConfig = simulatorConfig.withServer("127.0.0.1", server.port());
            device = DeviceClient.connect(connectedConfig, connectedConfig.devices().get(0));
            device.sendTelemetry(
                    1, System.currentTimeMillis() - 250, 30, 40, 50, 33.0);
            assertTrue(updateReady.await(3, TimeUnit.SECONDS));
            long latency = latest.get().latencyMillis();
            assertTrue(latency >= 200, "latency " + latency + " should be at least 200 ms");
            assertTrue(latency < 5_000, "latency " + latency + " should stay under 5 s");
            assertEquals(33.0, latest.get().diskPercent(), 0.001);
        } finally {
            if (device != null) {
                device.close();
            }
            if (monitor != null) {
                monitor.close();
            }
            server.close();
        }
    }

    @Test
    void serviceHealthCheckAlertsOnFailureAndRecovers() throws Exception {
        Path databasePath = temporaryDirectory.resolve("health-check.db");
        Database database = new Database(databasePath);
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                1, "monitor", "operator-password".toCharArray(), null, "127.0.0.1", 5050);
        AppConfig config = new AppConfig(
                "127.0.0.1", 0, 2_000, 65_536, 200, 700,
                80, 75, 3, 75, 70, 85, 78, 3, databasePath);

        int servicePort;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            servicePort = probe.getLocalPort();
        }

        MonitorServer server = new MonitorServer(config, database);
        MonitorClient monitor = null;
        try {
            server.start();
            monitor = new MonitorClient(
                    "127.0.0.1", server.port(),
                    credentials.operatorUsername(),
                    credentials.operatorPassword().toCharArray(),
                    new MonitorListener() {
                    });
            monitor.connect();
            monitor.registerServiceCheck(
                    "测试服务", "127.0.0.1", servicePort, 100, 2).get(2, TimeUnit.SECONDS);

            assertTrue(awaitCondition(
                    Duration.ofSeconds(4),
                    () -> hasActiveAlert(database, "测试服务", AlertRuleType.SERVICE_DOWN)));

            try (java.net.ServerSocket recovered = new java.net.ServerSocket(servicePort)) {
                assertTrue(awaitCondition(
                        Duration.ofSeconds(4),
                        () -> !hasActiveAlert(database, "测试服务", AlertRuleType.SERVICE_DOWN)));
            }
        } finally {
            if (monitor != null) {
                monitor.close();
            }
            server.close();
        }
    }

    private static boolean hasActiveAlert(
            Database database, String deviceId, AlertRuleType ruleType) {
        try {
            return database.findActiveAlert(deviceId, ruleType).isPresent();
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean awaitCondition(Duration timeout, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }
}

