package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.persistence.Database;
import com.shenliwei.devicewatch.persistence.DemoCredentials;
import com.shenliwei.devicewatch.protocol.MessageObserver;
import com.shenliwei.devicewatch.server.MonitorServer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MonitorClientObserverTest {

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
    void recordsSentAndReceivedMessagesWithTypesAndSizes() throws Exception {
        Path databasePath = temporaryDirectory.resolve("observer.db");
        Database database = new Database(databasePath);
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                1, "monitor", "operator-password".toCharArray(), null, "127.0.0.1", 5050);
        AppConfig config = new AppConfig(
                "127.0.0.1", 0, 2_000, 65_536, 200, 700,
                80, 75, 3, 75, 70, 85, 78, 3, databasePath);
        server = new MonitorServer(config, database);
        server.start();

        List<MessageEvent> events = new ArrayList<>();
        client = new MonitorClient(
                "127.0.0.1",
                server.port(),
                credentials.operatorUsername(),
                credentials.operatorPassword().toCharArray(),
                new MonitorListener() {
                },
                false,
                null,
                "",
                (direction, type, bytes, timestamp) ->
                        events.add(new MessageEvent(direction, type, bytes, timestamp)));
        client.connect();
        client.serverStatus().get(10, TimeUnit.SECONDS);
        client.close();
        client = null;

        assertTrue(events.stream().anyMatch(event ->
                event.direction() == MessageObserver.Direction.SENT
                        && "AUTH".equals(event.type())
                        && event.bytes() > 0));
        assertTrue(events.stream().anyMatch(event ->
                event.direction() == MessageObserver.Direction.RECEIVED
                        && "SERVER_STATUS".equals(event.type())));
        assertTrue(events.stream().anyMatch(event ->
                event.direction() == MessageObserver.Direction.SENT
                        && "SERVER_STATUS".equals(event.type())));
        assertEquals(0, events.stream()
                .filter(event -> event.bytes() <= 0)
                .count());
    }
}
