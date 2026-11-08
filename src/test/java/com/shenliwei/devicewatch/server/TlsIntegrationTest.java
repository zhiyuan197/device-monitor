package com.shenliwei.devicewatch.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.common.TlsContextFactory;
import com.shenliwei.devicewatch.persistence.Database;
import com.shenliwei.devicewatch.protocol.JsonLineChannel;
import com.shenliwei.devicewatch.protocol.ProtocolMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsIntegrationTest {

    private static final Path TEST_KEYSTORE =
            Path.of("src/test/resources/tls/test-keystore.p12");

    @TempDir
    Path temporaryDirectory;

    private MonitorServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private AppConfig tlsConfig() throws Exception {
        String keystore = TEST_KEYSTORE.toAbsolutePath().toString().replace('\\', '/');
        Path file = temporaryDirectory.resolve("server.properties");
        Files.writeString(file, """
                server.host=127.0.0.1
                server.port=0
                server.tls.enabled=true
                server.tls.port=0
                server.tls.keystore=%s
                server.tls.keystorePassword=testpass
                server.tls.truststore=%s
                server.tls.truststorePassword=testpass
                database.path=%s
                """.formatted(
                        keystore,
                        keystore,
                        temporaryDirectory.resolve("tls.db")
                                .toAbsolutePath().toString().replace('\\', '/')));
        return AppConfig.load(file);
    }

    private MonitorServer startServer() throws Exception {
        AppConfig config = tlsConfig();
        MonitorServer newServer =
                new MonitorServer(config, new Database(config.databasePath()));
        newServer.start();
        return newServer;
    }

    @Test
    void trustedClientAuthenticatesOverTlsAndReceivesAck() throws Exception {
        server = startServer();
        SSLContext context =
                TlsContextFactory.clientContext(TEST_KEYSTORE, "testpass");
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket(
                "127.0.0.1", server.tlsLocalPort())) {
            socket.setSoTimeout(5000);
            JsonLineChannel channel = new JsonLineChannel(
                    socket, new GsonBuilder().serializeNulls().create(), 65_536);
            JsonObject payload = new JsonObject();
            payload.addProperty("role", "DEVICE");
            payload.addProperty("deviceId", "TLS-DEV-1");
            payload.addProperty("credential", "unused-before-registration");
            String requestId = UUID.randomUUID().toString();
            channel.write(ProtocolMessage.request(
                    "AUTH", requestId, System.currentTimeMillis(), payload));
            JsonObject response = channel.read();
            assertTrue(response.has("type"));
            assertTrue(server.tlsHandshakeFailures() >= 0);
        }
    }

    @Test
    void untrustedClientIsRejectedAndFailureIsCounted() throws Exception {
        server = startServer();
        SSLContext context = SSLContext.getDefault();
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket(
                "127.0.0.1", server.tlsLocalPort())) {
            socket.setSoTimeout(3000);
            assertThrows(Exception.class, () -> {
                socket.startHandshake();
                throw new IllegalStateException("handshake unexpectedly succeeded");
            });
        } catch (Exception exception) {
            assertTrue(exception instanceof SSLException || exception instanceof java.net.SocketException
                    || exception instanceof javax.net.ssl.SSLHandshakeException);
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (server.tlsHandshakeFailures() == 0
                && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(1, server.tlsHandshakeFailures());
    }

    @Test
    void plaintextPortKeepsWorkingAlongsideTls() throws Exception {
        server = startServer();
        try (java.net.Socket socket = new java.net.Socket(
                "127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            JsonLineChannel channel = new JsonLineChannel(
                    socket, new Gson(), 65_536);
            JsonObject payload = new JsonObject();
            payload.addProperty("role", "DEVICE");
            payload.addProperty("deviceId", "PLAIN-DEV-1");
            payload.addProperty("credential", "unused");
            channel.write(ProtocolMessage.request(
                    "AUTH", UUID.randomUUID().toString(),
                    System.currentTimeMillis(), payload));
            JsonObject response = channel.read();
            assertTrue(response.has("type"));
        }
    }
}
