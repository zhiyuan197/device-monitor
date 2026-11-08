package com.shenliwei.devicewatch.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Validates the RFC 6455 opening handshake and computes the accept key.
 */
public final class WebSocketHandshake {

    public static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private WebSocketHandshake() {
    }

    public static String acceptKey(String secWebSocketKey) throws HandshakeException {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secWebSocketKey);
            if (keyBytes.length != 16) {
                throw new HandshakeException(400, "Sec-WebSocket-Key must decode to 16 bytes");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] material = (secWebSocketKey + GUID).getBytes(StandardCharsets.US_ASCII);
            return Base64.getEncoder().encodeToString(digest.digest(material));
        } catch (IllegalArgumentException exception) {
            throw new HandshakeException(400, "Sec-WebSocket-Key is not valid base64");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 unavailable", exception);
        }
    }

    public static String validate(Map<String, List<String>> headers)
            throws HandshakeException {
        String upgrade = header(headers, "upgrade");
        String connection = header(headers, "connection");
        String key = header(headers, "sec-websocket-key");
        String version = header(headers, "sec-websocket-version");
        String origin = header(headers, "origin");

        if (upgrade == null || !containsToken(upgrade, "websocket")) {
            throw new HandshakeException(400, "Upgrade: websocket is required");
        }
        if (connection == null || !containsToken(connection, "upgrade")) {
            throw new HandshakeException(400, "Connection: Upgrade is required");
        }
        if (key == null) {
            throw new HandshakeException(400, "Sec-WebSocket-Key is required");
        }
        if (version == null) {
            throw new HandshakeException(400, "Sec-WebSocket-Version is required");
        }
        if (!"13".equals(version.trim())) {
            throw new HandshakeException(426, "Sec-WebSocket-Version: 13");
        }
        if (origin == null || !isAllowedOrigin(origin)) {
            throw new HandshakeException(403, "origin rejected");
        }
        return acceptKey(key);
    }

    private static String header(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)
                    && entry.getValue() != null
                    && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private static boolean containsToken(String headerValue, String token) {
        for (String part : headerValue.split(",")) {
            if (part.trim().equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedOrigin(String origin) {
        try {
            URI uri = URI.create(origin.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            return "127.0.0.1".equalsIgnoreCase(host) || "localhost".equalsIgnoreCase(host);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
