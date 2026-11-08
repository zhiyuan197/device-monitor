package com.shenliwei.devicewatch.web;

import java.util.List;
import java.util.Map;

/**
 * Parsed HTTP/1.1 request: method, path and lower-cased header names.
 */
public record HttpRequest(
        String method,
        String path,
        Map<String, List<String>> headers) {
}
