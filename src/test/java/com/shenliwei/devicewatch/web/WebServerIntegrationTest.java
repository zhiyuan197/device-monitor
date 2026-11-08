package com.shenliwei.devicewatch.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WebServerIntegrationTest {

    private WebServer server;
    private final LinkedBlockingQueue<WebSocketSession> opened =
            new LinkedBlockingQueue<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void servesIndexAndCssWithContentLength() throws Exception {
        server = start(opened);
        String response = httpGet("/");
        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.contains("设备监控中心"), response);
        assertTrue(response.contains("Content-Length:"), response);

        String css = httpGet("/style.css");
        assertTrue(css.startsWith("HTTP/1.1 200"), css);
        assertTrue(css.contains("Content-Type: text/css"), css);
    }

    @Test
    void returns404ForUnknownPath() throws Exception {
        server = start(opened);
        String response = httpGet("/nope");
        assertTrue(response.startsWith("HTTP/1.1 404"), response);
    }

    @Test
    void returns405ForNonGetRequest() throws Exception {
        server = start(opened);
        String response = rawRequest("POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 405"), response);
    }

    @Test
    void servesEchartsBundleWithJavaScriptContentType() throws Exception {
        server = start(opened);
        String response = rawRequest(
                "GET /echarts.min.js HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.toLowerCase(java.util.Locale.ROOT)
                .contains("content-type: application/javascript"), response);
    }

    @Test
    void upgradesWebSocketAndEchoesTextAndPong() throws Exception {
        server = start(opened);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", server.port()), 3000);
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write((
                    "GET /ws HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Upgrade: websocket\r\n"
                            + "Connection: keep-alive, Upgrade\r\n"
                            + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                            + "Sec-WebSocket-Version: 13\r\n"
                            + "Origin: http://127.0.0.1:8088\r\n"
                            + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String response = readStatusHead(in);
            assertTrue(response.startsWith("HTTP/1.1 101"), response);
            assertTrue(response.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="),
                    response);

            WebSocketSession session = opened.poll(3, TimeUnit.SECONDS);
            assertTrue(session != null, "session was not reported");
            session.sendText("hello");

            WebSocketFrame frame = WebSocketFrameCodec.readFrame(in, 65536, false);
            assertEquals(WebSocketFrameCodec.OP_TEXT, frame.opcode());
            assertEquals("hello",
                    new String(frame.payload(), StandardCharsets.UTF_8));

            out.write(maskedFrame(WebSocketFrameCodec.OP_PING,
                    "abc".getBytes(StandardCharsets.UTF_8)));
            out.flush();
            WebSocketFrame pong = WebSocketFrameCodec.readFrame(in, 65536, false);
            assertEquals(WebSocketFrameCodec.OP_PONG, pong.opcode());
            assertEquals("abc", new String(pong.payload(), StandardCharsets.UTF_8));

            session.close();
        }
    }

    @Test
    void rejectsUpgradeWithoutOrigin() throws Exception {
        server = start(opened);
        String response = rawRequest(
                "GET /ws HTTP/1.1\r\n"
                        + "Host: 127.0.0.1\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");
        assertTrue(response.startsWith("HTTP/1.1 403"), response);
    }

    @Test
    void closeReleasesSessionsAndStopsAccepting() throws Exception {
        server = start(opened);
        int port = server.port();
        server.close();
        assertFalse(server.isRunning());
        for (WebSocketSession session : opened) {
            assertFalse(session.isOpen());
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            throw new AssertionError("port should be released");
        } catch (java.io.IOException expected) {
            // Port released.
        }
    }

    private WebServer start(LinkedBlockingQueue<WebSocketSession> sessions) throws Exception {
        WebServer newServer = new WebServer("127.0.0.1", 0, sessions::add);
        newServer.start();
        return newServer;
    }

    private String httpGet(String path) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", server.port()), 3000);
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(
                    ("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            String head = readStatusHead(in);
            int length = 0;
            for (String line : head.split("\r\n")) {
                if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                    length = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }
            byte[] body = in.readNBytes(length);
            return head + new String(body, StandardCharsets.UTF_8);
        }
    }

    private String rawRequest(String request) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", server.port()), 3000);
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return readStatusHead(socket.getInputStream());
        }
    }

    private static String readStatusHead(InputStream in) throws Exception {
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

    private static byte[] maskedFrame(int opcode, byte[] payload) {
        byte[] mask = new byte[] {9, 8, 7, 6};
        byte[] frame = new byte[2 + 4 + payload.length];
        frame[0] = (byte) (0x80 | opcode);
        frame[1] = (byte) (0x80 | payload.length);
        System.arraycopy(mask, 0, frame, 2, 4);
        for (int i = 0; i < payload.length; i++) {
            frame[6 + i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        return frame;
    }
}
