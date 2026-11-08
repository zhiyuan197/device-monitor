package com.shenliwei.devicewatch.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads one HTTP/1.1 request head from a stream with a hard size cap.
 */
public final class HttpRequestParser {

    public static final int MAX_HEADER_BYTES = 8192;

    private HttpRequestParser() {
    }

    public static HttpRequest parse(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
        String requestLine = readLine(in, buffer);
        if (requestLine == null) {
            throw new IOException("empty request");
        }
        String[] requestParts = requestLine.split(" ", 3);
        if (requestParts.length != 3 || requestParts[0].isBlank()
                || requestParts[1].isBlank()) {
            throw new IOException("malformed request line");
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine(in, buffer)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IOException("malformed header line");
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        if (!headers.containsKey("host")) {
            throw new IOException("HTTP/1.1 requests require a Host header");
        }
        return new HttpRequest(requestParts[0], requestParts[1], headers);
    }

    private static String readLine(InputStream in, ByteArrayOutputStream buffer)
            throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(128);
        int value;
        while ((value = in.read()) != -1) {
            buffer.write(value);
            if (buffer.size() > MAX_HEADER_BYTES) {
                throw new IOException("header block too large");
            }
            if (value == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length;
                if (length > 0 && bytes[length - 1] == '\r') {
                    length--;
                }
                return new String(bytes, 0, length,
                        java.nio.charset.StandardCharsets.ISO_8859_1);
            }
            line.write(value);
        }
        return null;
    }
}
