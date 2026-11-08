package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.persistence.Database;
import com.shenliwei.devicewatch.persistence.DemoCredentials;
import com.shenliwei.devicewatch.server.MonitorServer;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReconnectIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private MonitorServer server;
    private MonitorClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void clientReconnectsAfterServerRestartOnTheSamePort() throws Exception {
        Path databasePath = temporaryDirectory.resolve("reconnect.db");
        Database database = new Database(databasePath);
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                1, "monitor", "operator-password".toCharArray(), null, "127.0.0.1", 5050);

        AppConfig config = new AppConfig(
                "127.0.0.1", 0, 2_000, 65_536, 200, 700,
                80, 75, 3, 75, 70, 85, 78, 3, databasePath);
        server = new MonitorServer(config, database);
        server.start();
        int serverPort = server.port();

        CountDownLatch firstConnected = new CountDownLatch(1);
        CountDownLatch disconnected = new CountDownLatch(1);
        CountDownLatch reconnected = new CountDownLatch(1);
        AtomicBoolean everDisconnected = new AtomicBoolean();
        client = new MonitorClient(
                "127.0.0.1",
                serverPort,
                credentials.operatorUsername(),
                credentials.operatorPassword().toCharArray(),
                new MonitorListener() {
                    @Override
                    public void onConnectionState(boolean connected, String detail) {
                        if (connected) {
                            if (firstConnected.getCount() > 0) {
                                firstConnected.countDown();
                            } else {
                                reconnected.countDown();
                            }
                        } else {
                            everDisconnected.set(true);
                            disconnected.countDown();
                        }
                    }
                });
        client.connect();
        assertTrue(firstConnected.await(5, TimeUnit.SECONDS), "initial connect");

        server.close();
        assertTrue(disconnected.await(10, TimeUnit.SECONDS), "disconnect detection");
        assertTrue(everDisconnected.get(), "disconnect callback fired");

        AppConfig restartedConfig = config.withPort(serverPort);
        server = new MonitorServer(restartedConfig, database);
        server.start();

        client.connect();
        assertTrue(reconnected.await(5, TimeUnit.SECONDS), "reconnect");
        ServerStatusView status = client.serverStatus().get(10, TimeUnit.SECONDS);
        assertTrue(status.registeredDevices() >= 1);
    }
}
