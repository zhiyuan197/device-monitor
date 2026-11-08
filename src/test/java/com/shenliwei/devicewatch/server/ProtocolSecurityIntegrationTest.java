package com.shenliwei.devicewatch.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.persistence.Database;
import com.shenliwei.devicewatch.persistence.DemoCredentials;
import com.shenliwei.devicewatch.protocol.JsonLineChannel;
import com.shenliwei.devicewatch.protocol.ProtocolMessage;
import com.shenliwei.devicewatch.simulator.DeviceClient;
import com.shenliwei.devicewatch.simulator.SimulatorConfig;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtocolSecurityIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private Database database;
    private DemoCredentials credentials;
    private MonitorServer server;

    @BeforeEach
    void startServer() throws Exception {
        Path databasePath = temporaryDirectory.resolve("security.db");
        database = new Database(databasePath);
        database.initialize();
        credentials = database.seedDemoData(
                1, "operator", "operator-password".toCharArray(), null, "127.0.0.1", 5050);
        AppConfig config = new AppConfig(
                "127.0.0.1",
                0,
                3_000,
                65_536,
                500,
                2_000,
                80,
                75,
                3,
                75,
                70,
                85,
                78,
                3,
                databasePath);
        server = new MonitorServer(config, database);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void closesConnectionAfterFiveFailedAuthenticationAttempts() throws Exception {
        try (RawClient client = RawClient.connect(server.port())) {
            for (int attempt = 1; attempt <= 5; attempt++) {
                String requestId = "bad-auth-" + attempt;
                JsonObject payload = new JsonObject();
                payload.addProperty("role", "MONITOR");
                payload.addProperty("username", "operator");
                payload.addProperty("password", "wrong-password");
                client.channel.write(ProtocolMessage.request(
                        "AUTH", requestId, System.currentTimeMillis(), payload));
                JsonObject response = client.channel.read();
                assertNotNull(response);
                assertEquals("ERROR", response.get("type").getAsString());
                String expectedCode = attempt == 5
                        ? "AUTH_ATTEMPTS_EXCEEDED"
                        : "AUTH_FAILED";
                assertEquals(
                        expectedCode,
                        response.getAsJsonObject("payload").get("code").getAsString());
            }
            assertNull(client.channel.read());
        }
    }

    @Test
    void rejectsDeviceAttemptToRunMonitorQuery() throws Exception {
        DemoCredentials.DeviceSecret account = credentials.devices().get(0);
        try (RawClient client = RawClient.connect(server.port())) {
            authenticateDevice(client, account);
            String requestId = "forbidden-query";
            JsonObject payload = new JsonObject();
            payload.addProperty("deviceId", account.deviceId());
            payload.addProperty("fromMillis", 0);
            payload.addProperty("toMillis", System.currentTimeMillis());
            payload.addProperty("limit", 10);
            client.channel.write(ProtocolMessage.request(
                    "QUERY_TELEMETRY", requestId, System.currentTimeMillis(), payload));
            JsonObject response = client.channel.read();
            assertEquals("ERROR", response.get("type").getAsString());
            assertEquals(
                    "ROLE_FORBIDDEN",
                    response.getAsJsonObject("payload").get("code").getAsString());
        }
    }

    @Test
    void rejectsOversizedFrameAndClosesConnection() throws Exception {
        try (RawClient client = RawClient.connect(server.port())) {
            byte[] oversized = new byte[70_000];
            java.util.Arrays.fill(oversized, (byte) 'a');
            client.socket.getOutputStream().write(oversized);
            client.socket.getOutputStream().flush();
            JsonObject response = client.channel.read();
            assertNotNull(response);
            assertEquals(
                    "MESSAGE_TOO_LARGE",
                    response.getAsJsonObject("payload").get("code").getAsString());
            assertNull(client.channel.read());
        }
    }

    @Test
    void newerDeviceSessionReplacesPreviousSession() throws Exception {
        DemoCredentials.DeviceSecret account = credentials.devices().get(0);
        SimulatorConfig config = new SimulatorConfig(
                "127.0.0.1",
                server.port(),
                credentials.operatorUsername(),
                credentials.operatorPassword(),
                List.of(new SimulatorConfig.DeviceAccount(
                        account.deviceId(), account.credential())));
        try (DeviceClient first = DeviceClient.connect(
                        config, config.devices().get(0));
             DeviceClient second = DeviceClient.connect(
                     config, config.devices().get(0))) {
            assertThrows(Exception.class, () -> first.sendHeartbeat(1));
            second.sendHeartbeat(1);
        }
    }

    private static void authenticateDevice(
            RawClient client, DemoCredentials.DeviceSecret account) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("role", "DEVICE");
        payload.addProperty("deviceId", account.deviceId());
        payload.addProperty("credential", account.credential());
        String requestId = UUID.randomUUID().toString();
        client.channel.write(ProtocolMessage.request(
                "AUTH", requestId, System.currentTimeMillis(), payload));
        JsonObject response = client.channel.read();
        assertEquals("AUTH_OK", response.get("type").getAsString());
    }

    private static final class RawClient implements Closeable {
        private final Socket socket;
        private final JsonLineChannel channel;

        private RawClient(Socket socket, JsonLineChannel channel) {
            this.socket = socket;
            this.channel = channel;
        }

        static RawClient connect(int port) throws IOException {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            socket.setSoTimeout(3_000);
            Gson gson = new GsonBuilder().serializeNulls().create();
            return new RawClient(socket, new JsonLineChannel(socket, gson, 65_536));
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}

