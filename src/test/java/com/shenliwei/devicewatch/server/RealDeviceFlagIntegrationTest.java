package com.shenliwei.devicewatch.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.monitor.DeviceView;
import com.shenliwei.devicewatch.monitor.MonitorClient;
import com.shenliwei.devicewatch.monitor.MonitorListener;
import com.shenliwei.devicewatch.persistence.Database;
import com.shenliwei.devicewatch.persistence.DemoCredentials;
import com.shenliwei.devicewatch.simulator.DeviceClient;
import com.shenliwei.devicewatch.simulator.SimulatorConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Full-socket test for the "real local host" flag: a device authenticating
 * with real=true is broadcast to monitors as the local machine's device.
 */
class RealDeviceFlagIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void realFlagIsBroadcastAndUpdated() throws Exception {
        Path databasePath = temporaryDirectory.resolve("real-flag.db");
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

        AtomicReference<DeviceView> latest = new AtomicReference<>();
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
                        @Override
                        public void onDeviceUpdate(DeviceView value) {
                            latest.set(value);
                        }
                    });
            monitor.connect();

            SimulatorConfig connectedConfig =
                    simulatorConfig.withServer("127.0.0.1", server.port());
            device = DeviceClient.connect(
                    connectedConfig, connectedConfig.devices().get(0), true);
            assertTrue(awaitCondition(
                    Duration.ofSeconds(3), () -> latest.get() != null && latest.get().real()));

            device.close();
            device = DeviceClient.connect(
                    connectedConfig, connectedConfig.devices().get(0), false);
            DeviceClient reconnected = device;
            assertTrue(awaitCondition(
                    Duration.ofSeconds(3),
                    () -> {
                        DeviceView view = latest.get();
                        return view != null && !view.real();
                    }));
            reconnected.sendHeartbeat(1);
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
