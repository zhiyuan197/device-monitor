package com.shenliwei.devicewatch.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebSocketHandshakeTest {

    @Test
    void acceptKeyMatchesRfc6455Sample() throws Exception {
        assertEquals(
                "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                WebSocketHandshake.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="));
    }

    @Test
    void acceptKeyRejectsMalformedBase64() {
        assertThrows(HandshakeException.class,
                () -> WebSocketHandshake.acceptKey("!!not-base64!!"));
    }

    @Test
    void validateAcceptsWellFormedHeadersAndReturnsAcceptKey() throws Exception {
        Map<String, List<String>> headers = Map.of(
                "upgrade", List.of("websocket"),
                "connection", List.of("keep-alive, Upgrade"),
                "sec-websocket-key", List.of("dGhlIHNhbXBsZSBub25jZQ=="),
                "sec-websocket-version", List.of("13"),
                "origin", List.of("http://127.0.0.1:8088"));
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                WebSocketHandshake.validate(headers));
    }

    @Test
    void validateRejectsMissingUpgradeHeader() {
        HandshakeException exception = assertThrows(HandshakeException.class,
                () -> WebSocketHandshake.validate(Map.of(
                        "connection", List.of("Upgrade"),
                        "sec-websocket-key", List.of("dGhlIHNhbXBsZSBub25jZQ=="),
                        "sec-websocket-version", List.of("13"),
                        "origin", List.of("http://127.0.0.1:8088"))));
        assertEquals(400, exception.statusCode());
    }

    @Test
    void validateRejectsMissingConnectionHeader() {
        assertThrows(HandshakeException.class, () -> WebSocketHandshake.validate(Map.of(
                "upgrade", List.of("websocket"),
                "sec-websocket-key", List.of("dGhlIHNhbXBsZSBub25jZQ=="),
                "sec-websocket-version", List.of("13"),
                "origin", List.of("http://127.0.0.1:8088"))));
    }

    @Test
    void validateRejectsMissingKey() {
        assertThrows(HandshakeException.class, () -> WebSocketHandshake.validate(Map.of(
                "upgrade", List.of("websocket"),
                "connection", List.of("Upgrade"),
                "sec-websocket-version", List.of("13"),
                "origin", List.of("http://127.0.0.1:8088"))));
    }

    @Test
    void validateRejectsUnsupportedVersionWith426() {
        HandshakeException exception = assertThrows(HandshakeException.class,
                () -> WebSocketHandshake.validate(Map.of(
                        "upgrade", List.of("websocket"),
                        "connection", List.of("Upgrade"),
                        "sec-websocket-key", List.of("dGhlIHNhbXBsZSBub25jZQ=="),
                        "sec-websocket-version", List.of("8"),
                        "origin", List.of("http://127.0.0.1:8088"))));
        assertEquals(426, exception.statusCode());
    }

    @Test
    void validateRejectsMissingVersion() {
        assertThrows(HandshakeException.class, () -> WebSocketHandshake.validate(Map.of(
                "upgrade", List.of("websocket"),
                "connection", List.of("Upgrade"),
                "sec-websocket-key", List.of("dGhlIHNhbXBsZSBub25jZQ=="),
                "origin", List.of("http://127.0.0.1:8088"))));
    }

    @Test
    void validateRejectsForeignOriginWith403() {
        HandshakeException exception = assertThrows(HandshakeException.class,
                () -> WebSocketHandshake.validate(Map.of(
                        "upgrade", List.of("websocket"),
                        "connection", List.of("Upgrade"),
                        "sec-websocket-key", List.of("dGhlIHNhbXBsZSBub25jZQ=="),
                        "sec-websocket-version", List.of("13"),
                        "origin", List.of("http://evil.example/"))));
        assertEquals(403, exception.statusCode());
    }

    @Test
    void validateRejectsMissingOrigin() {
        HandshakeException exception = assertThrows(HandshakeException.class,
                () -> WebSocketHandshake.validate(Map.of(
                        "upgrade", List.of("websocket"),
                        "connection", List.of("Upgrade"),
                        "sec-websocket-key", List.of("dGhlIHNhbXBsZSBub25jZQ=="),
                        "sec-websocket-version", List.of("13"))));
        assertEquals(403, exception.statusCode());
    }
}
