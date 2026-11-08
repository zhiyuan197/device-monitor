package com.shenliwei.devicewatch.simulator;

import java.io.IOException;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public record SimulatorConfig(
        String host,
        int port,
        String monitorUsername,
        String monitorPassword,
        List<DeviceAccount> devices,
        boolean tlsEnabled,
        int tlsPort,
        Path truststorePath,
        String truststorePassword) {

    public SimulatorConfig(
            String host,
            int port,
            String monitorUsername,
            String monitorPassword,
            List<DeviceAccount> devices) {
        this(host, port, monitorUsername, monitorPassword, devices, false, 5051, null, "");
    }

    public SimulatorConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("server.host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("server.port must be between 1 and 65535");
        }
        if (devices == null || devices.isEmpty()) {
            throw new IllegalArgumentException("At least one demo device is required");
        }
        devices = List.copyOf(devices);
    }

    public static SimulatorConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        int count = Integer.parseInt(properties.getProperty("device.count", "0").trim());
        if (count < 1) {
            throw new IllegalArgumentException("device.count must be positive");
        }
        List<DeviceAccount> devices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = required(properties, "device." + i + ".id");
            String credential = required(properties, "device." + i + ".credential");
            devices.add(new DeviceAccount(id, credential));
        }
        boolean tlsEnabled = Boolean.parseBoolean(
                properties.getProperty("tls.enabled", "false").trim());
        Path truststore = nullablePath(properties.getProperty("tls.truststore"));
        if (tlsEnabled && truststore == null) {
            tlsEnabled = false;
        }
        String truststorePassword =
                properties.getProperty("tls.truststorePassword", "").trim();

        int tlsPort = 5051;
        try {
            tlsPort = Integer.parseInt(properties.getProperty("tls.port", "5051").trim());
        } catch (NumberFormatException exception) {
            tlsPort = 5051;
        }

        return new SimulatorConfig(
                properties.getProperty("server.host", "127.0.0.1").trim(),
                Integer.parseInt(properties.getProperty("server.port", "5050").trim()),
                properties.getProperty("monitor.username", "").trim(),
                properties.getProperty("monitor.password", "").trim(),
                devices,
                tlsEnabled,
                tlsPort,
                truststore,
                truststorePassword);
    }

    public SimulatorConfig withServer(String newHost, int newPort) {
        return new SimulatorConfig(
                newHost, newPort, monitorUsername, monitorPassword, devices,
                tlsEnabled, tlsPort, truststorePath, truststorePassword);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing demo configuration property: " + key);
        }
        return value.trim();
    }

    private static Path nullablePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value.trim());
    }

    public record DeviceAccount(String deviceId, String credential) {
    }
}

