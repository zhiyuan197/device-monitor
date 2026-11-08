package com.shenliwei.devicewatch.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HttpRequestParserTest {

    @Test
    void parsesRequestLineAndHeaders() throws Exception {
        HttpRequest request = HttpRequestParser.parse(new ByteArrayInputStream(
                ("GET /app.js HTTP/1.1\r\n"
                        + "Host: 127.0.0.1:8088\r\n"
                        + "Accept: */*\r\n"
                        + "\r\n").getBytes(StandardCharsets.US_ASCII)));
        assertEquals("GET", request.method());
        assertEquals("/app.js", request.path());
        assertEquals("127.0.0.1:8088",
                request.headers().get("host").get(0));
    }

    @Test
    void normalizesHeaderNamesToLowerCase() throws Exception {
        HttpRequest request = HttpRequestParser.parse(new ByteArrayInputStream(
                ("GET / HTTP/1.1\r\n"
                        + "HOST: localhost\r\n"
                        + "\r\n").getBytes(StandardCharsets.US_ASCII)));
        assertTrue(request.headers().containsKey("host"));
        assertTrue(!request.headers().containsKey("HOST"));
    }

    @Test
    void rejectsRequestWithoutHostHeader() {
        IOException exception = assertThrows(IOException.class,
                () -> HttpRequestParser.parse(new ByteArrayInputStream(
                        ("GET / HTTP/1.1\r\n"
                                + "Accept: */*\r\n"
                                + "\r\n").getBytes(StandardCharsets.US_ASCII))));
        assertTrue(exception.getMessage().contains("Host"));
    }

    @Test
    void rejectsMalformedRequestLine() {
        assertThrows(IOException.class,
                () -> HttpRequestParser.parse(new ByteArrayInputStream(
                        ("GET /\r\n\r\n").getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    void rejectsHeadersAboveLimit() {
        byte[] oversized = new byte[HttpRequestParser.MAX_HEADER_BYTES + 64];
        java.util.Arrays.fill(oversized, (byte) 'a');
        oversized[oversized.length - 1] = '\n';
        IOException exception = assertThrows(IOException.class,
                () -> HttpRequestParser.parse(new ByteArrayInputStream(oversized)));
        assertTrue(exception.getMessage().contains("too large"));
    }
}
