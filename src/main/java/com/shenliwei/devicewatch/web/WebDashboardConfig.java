package com.shenliwei.devicewatch.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Runtime settings for the browser-based real-time dashboard.
 */
public record WebDashboardConfig(boolean enabled, String bindHost, int port) {

    public static final int DEFAULT_PORT = 8088;
    public static final String DEFAULT_BIND_HOST = "127.0.0.1";

    public WebDashboardConfig {
        if (!DEFAULT_BIND_HOST.equals(bindHost) && !"localhost".equals(bindHost)) {
            throw new IllegalArgumentException("web.bind must be 127.0.0.1 or localhost");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("web.port must be between 1 and 65535");
        }
    }

    public static WebDashboardConfig defaults() {
        return new WebDashboardConfig(true, DEFAULT_BIND_HOST, DEFAULT_PORT);
    }

    public static WebDashboardConfig load(Path file) {
        if (file == null || !Files.exists(file)) {
            return defaults();
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            return defaults();
        }
        boolean enabled = switch (properties.getProperty("web.enabled", "true").trim()) {
            case "true" -> true;
            case "false" -> false;
            default -> defaults().enabled();
        };
        String bindHost = properties.getProperty("web.bind", DEFAULT_BIND_HOST).trim();
        if (!DEFAULT_BIND_HOST.equals(bindHost) && !"localhost".equals(bindHost)) {
            bindHost = DEFAULT_BIND_HOST;
        }
        int port = DEFAULT_PORT;
        try {
            port = Integer.parseInt(properties.getProperty("web.port").trim());
        } catch (RuntimeException ignored) {
            port = DEFAULT_PORT;
        }
        try {
            return new WebDashboardConfig(enabled, bindHost, port);
        } catch (IllegalArgumentException exception) {
            return defaults();
        }
    }

    public String url() {
        return "http://" + bindHost + ":" + port + "/";
    }
}
