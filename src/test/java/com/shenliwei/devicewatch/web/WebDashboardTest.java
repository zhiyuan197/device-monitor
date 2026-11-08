package com.shenliwei.devicewatch.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shenliwei.devicewatch.monitor.AlertView;
import com.shenliwei.devicewatch.monitor.DeviceView;
import com.shenliwei.devicewatch.monitor.DeviceRulesView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebDashboardTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC);

    private WebDashboard dashboard() {
        return new WebDashboard(
                new WebDashboardConfig(true, "127.0.0.1", freePort()),
                "127.0.0.1", 5050, CLOCK);
    }

    @Test
    void snapshotReflectsDevicesStatsAndSorting() {
        WebDashboard dashboard = dashboard();
        dashboard.onDeviceSnapshot(List.of(
                device("DEV-002", true, true, 10L, 40.0),
                device("DEV-001", true, false, 30L, 60.0),
                device("DEV-003", false, false, 0L, null)));

        JsonObject snapshot = JsonParser.parseString(
                dashboard.buildSnapshotJson()).getAsJsonObject();
        assertEquals("snapshot", snapshot.get("type").getAsString());
        JsonObject stats = snapshot.getAsJsonObject("stats");
        assertEquals(2, stats.get("online").getAsInt());
        assertEquals(3, stats.get("total").getAsInt());
        assertEquals(1, stats.get("real").getAsInt());
        assertEquals(20, stats.get("avgLatencyMs").getAsInt());
        JsonArray devices = snapshot.getAsJsonArray("devices");
        assertEquals("DEV-001", devices.get(0).getAsJsonObject()
                .get("id").getAsString());
        assertEquals("DEV-003", devices.get(2).getAsJsonObject()
                .get("id").getAsString());
        assertTrue(snapshot.has("trends"));
    }

    @Test
    void alertUpdateReplacesSameAlertAndKeepsRecentOnly() {
        WebDashboard dashboard = dashboard();
        dashboard.onAlertUpdate(alert(7L, "DEV-001", "ACTIVE", 1_000L));
        dashboard.onAlertUpdate(alert(7L, "DEV-001", "RESOLVED", 2_000L));
        JsonObject snapshot = JsonParser.parseString(
                dashboard.buildSnapshotJson()).getAsJsonObject();
        JsonArray alerts = snapshot.getAsJsonArray("alerts");
        assertEquals(1, alerts.size());
        assertEquals("RESOLVED", alerts.get(0).getAsJsonObject()
                .get("status").getAsString());
    }

    @Test
    void trendBufferIsCappedAt120Points() {
        WebDashboard dashboard = dashboard();
        dashboard.onDeviceSnapshot(List.of(device("DEV-001", true, true, 10L, 40.0)));
        for (int i = 0; i < 150; i++) {
            dashboard.sampleTrends();
        }
        JsonObject snapshot = JsonParser.parseString(
                dashboard.buildSnapshotJson()).getAsJsonObject();
        JsonArray cpu = snapshot.getAsJsonObject("trends")
                .getAsJsonObject("DEV-001").getAsJsonArray("cpu");
        assertEquals(120, cpu.size());
    }

    @Test
    void connectionStateIsExposedInSnapshot() {
        WebDashboard dashboard = dashboard();
        dashboard.onConnectionState(false, "断了");
        JsonObject snapshot = JsonParser.parseString(
                dashboard.buildSnapshotJson()).getAsJsonObject();
        assertEquals(false, snapshot.get("connected").getAsBoolean());
    }

    @Test
    void sessionOpenedReceivesSnapshotImmediatelyWithoutTicker() throws Exception {
        WebDashboard dashboard = dashboard();
        dashboard.onDeviceSnapshot(List.of(device("DEV-001", true, true, 10L, 40.0)));
        try (ServerSocket listener = new ServerSocket(0)) {
            Socket client = new Socket("127.0.0.1", listener.getLocalPort());
            Socket serverSide = listener.accept();
            WebSocketSession session = new WebSocketSession(serverSide);
            try {
                dashboard.onSessionOpened(session);
                WebSocketFrame frame = WebSocketFrameCodec.readFrame(
                        client.getInputStream(), 65536, false);
                String text = new String(frame.payload(), StandardCharsets.UTF_8);
                assertTrue(text.contains("\"type\":\"snapshot\""), text);
                assertTrue(text.contains("DEV-001"), text);
            } finally {
                session.close();
                client.close();
            }
        }
    }

    @Test
    void startedServerPushesSnapshotAndAlertEventsOverWebSocket() throws Exception {
        WebDashboard dashboard = dashboard();
        dashboard.onDeviceSnapshot(List.of(
                device("DEV-001", true, true, 12L, 35.0)));
        dashboard.start();
        try (Socket socket = new Socket("127.0.0.1", dashboard.webPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write((
                    "GET /ws HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Upgrade: websocket\r\n"
                            + "Connection: Upgrade\r\n"
                            + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                            + "Sec-WebSocket-Version: 13\r\n"
                            + "Origin: http://127.0.0.1:8088\r\n"
                            + "\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            String head = readHead(in);
            assertTrue(head.startsWith("HTTP/1.1 101"), head);

            WebSocketFrame snapshotFrame = WebSocketFrameCodec.readFrame(in, 65536, false);
            String snapshotText = new String(
                    snapshotFrame.payload(), StandardCharsets.UTF_8);
            assertTrue(snapshotText.contains("\"type\":\"snapshot\""), snapshotText);

            dashboard.onAlertUpdate(alert(9L, "DEV-001", "ACTIVE", 3_000L));
            WebSocketFrame alertFrame = WebSocketFrameCodec.readFrame(in, 65536, false);
            String alertText = new String(alertFrame.payload(), StandardCharsets.UTF_8);
            assertTrue(alertText.contains("\"type\":\"alert\""), alertText);
            assertTrue(alertText.contains("DEV-001"), alertText);
        } finally {
            dashboard.stop();
        }
    }

    private static DeviceView device(
            String id, boolean online, boolean real, long latency, Double cpu) {
        return new DeviceView(
                id, "设备-" + id, "机房A", true, online,
                1_000L, 900L, cpu, 50.0, 45.0, 30.0,
                new DeviceRulesView(80, 75, 3, 85, 78, 3, 75, 70),
                latency, 8.0, real);
    }

    private static AlertView alert(String deviceId, String status) {
        return alert(1L, deviceId, status, 1_000L);
    }

    private static AlertView alert(long id, String deviceId, String status, long triggeredAt) {
        return new AlertView(
                id, deviceId, "CPU", "WARN", status, 87.5, triggeredAt,
                "RESOLVED".equals(status) ? 2_000L : null,
                null, "", "");
    }

    private static int freePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String readHead(InputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int value;
        while ((value = in.read()) != -1) {
            buffer.write(value);
            byte[] bytes = buffer.toByteArray();
            if (bytes.length >= 4
                    && bytes[bytes.length - 4] == '\r'
                    && bytes[bytes.length - 3] == '\n'
                    && bytes[bytes.length - 2] == '\r'
                    && bytes[bytes.length - 1] == '\n') {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        }
        return new String(buffer.toByteArray(), StandardCharsets.ISO_8859_1);
    }
}
