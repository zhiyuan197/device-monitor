package com.shenliwei.devicewatch.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.domain.AlertRuleType;
import com.shenliwei.devicewatch.monitor.DemoFault;
import com.shenliwei.devicewatch.monitor.DemoInjectionView;
import com.shenliwei.devicewatch.monitor.MonitorClient;
import com.shenliwei.devicewatch.monitor.MonitorListener;
import com.shenliwei.devicewatch.monitor.ServiceCheckView;
import com.shenliwei.devicewatch.persistence.Database;
import com.shenliwei.devicewatch.persistence.DemoCredentials;
import com.shenliwei.devicewatch.simulator.DeviceClient;
import com.shenliwei.devicewatch.simulator.SimulatorConfig;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Full-socket integration test for the demo fault injection feature:
 * the injected faults must flow through the real alert engine, audit path
 * and broadcast pipeline, and recover automatically.
 */
class DemoInjectIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void cpuSpikeOfflineAndServiceFailAllTriggerAndRecover() throws Exception {
        Path databasePath = temporaryDirectory.resolve("inject.db");
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

        try (ServerSocket serviceSocket = new ServerSocket(0)) {
            MonitorServer server = new MonitorServer(config, database);
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
                        });
                monitor.connect();

                SimulatorConfig connectedConfig =
                        simulatorConfig.withServer("127.0.0.1", server.port());
                device = DeviceClient.connect(
                        connectedConfig, connectedConfig.devices().get(0));
                long sequence = 1;
                device.sendTelemetry(sequence++, System.currentTimeMillis(), 40, 50, 50);

                DemoInjectionView cpu = monitor.demoInject(
                                DemoFault.CPU_SPIKE, deviceAccount.deviceId(), null)
                        .get(3, TimeUnit.SECONDS);
                assertEquals("CPU_SPIKE", cpu.fault());
                assertTrue(awaitCondition(
                        Duration.ofSeconds(3),
                        () -> hasActiveAlert(
                                database, deviceAccount.deviceId(), AlertRuleType.CPU)));

                device.sendTelemetry(sequence++, System.currentTimeMillis(), 70, 50, 50);
                device.sendTelemetry(sequence++, System.currentTimeMillis(), 70, 50, 50);
                device.sendTelemetry(sequence++, System.currentTimeMillis(), 70, 50, 50);
                assertTrue(awaitCondition(
                        Duration.ofSeconds(3),
                        () -> !hasActiveAlert(
                                database, deviceAccount.deviceId(), AlertRuleType.CPU)));

                DemoInjectionView offline = monitor.demoInject(
                                DemoFault.OFFLINE, deviceAccount.deviceId(), null)
                        .get(3, TimeUnit.SECONDS);
                assertEquals("OFFLINE", offline.fault());
                assertTrue(awaitCondition(
                        Duration.ofSeconds(3),
                        () -> hasActiveAlert(
                                database, deviceAccount.deviceId(), AlertRuleType.OFFLINE)));

                device.close();
                device = null;
                device = DeviceClient.connect(
                        connectedConfig, connectedConfig.devices().get(0));
                DeviceClient reconnected = device;
                assertTrue(awaitCondition(
                        Duration.ofSeconds(3),
                        () -> !hasActiveAlert(
                                database, deviceAccount.deviceId(), AlertRuleType.OFFLINE)));
                reconnected.sendTelemetry(
                        sequence++, System.currentTimeMillis(), 40, 50, 50);

                ServiceCheckView check = monitor.registerServiceCheck(
                                "演示服务",
                                "127.0.0.1",
                                serviceSocket.getLocalPort(),
                                100,
                                1)
                        .get(3, TimeUnit.SECONDS);
                DemoInjectionView service = monitor.demoInject(
                                DemoFault.SERVICE_FAIL, null, check.checkId())
                        .get(3, TimeUnit.SECONDS);
                assertEquals("SERVICE_FAIL", service.fault());
                assertTrue(awaitCondition(
                        Duration.ofSeconds(3),
                        () -> hasActiveAlert(
                                database, "演示服务", AlertRuleType.SERVICE_DOWN)));
                assertTrue(awaitCondition(
                        Duration.ofSeconds(5),
                        () -> !hasActiveAlert(
                                database, "演示服务", AlertRuleType.SERVICE_DOWN)));
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
