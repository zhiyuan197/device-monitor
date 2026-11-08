package com.shenliwei.devicewatch.common;

import java.io.IOException;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Validated runtime configuration for the server and shared clients.
 */
public record AppConfig(
        String host,
        int port,
        int authTimeoutMillis,
        int maxMessageBytes,
        long heartbeatIntervalMillis,
        long offlineTimeoutMillis,
        double cpuTriggerThreshold,
        double cpuRecoverThreshold,
        int cpuRequiredConsecutiveSamples,
        double temperatureTriggerThreshold,
        double temperatureRecoverThreshold,
        double memoryTriggerThreshold,
        double memoryRecoverThreshold,
        int memoryRequiredConsecutiveSamples,
        Path databasePath,
        int telemetryRetentionDays,
        boolean tlsEnabled,
        int tlsPort,
        Path tlsKeystorePath,
        String tlsKeystorePassword,
        Path tlsTruststorePath,
        String tlsTruststorePassword) {

    public static final int DEFAULT_PORT = 5050;
    public static final int DEFAULT_TLS_PORT = 5051;

    public AppConfig(
            String host,
            int port,
            int authTimeoutMillis,
            int maxMessageBytes,
            long heartbeatIntervalMillis,
            long offlineTimeoutMillis,
            double cpuTriggerThreshold,
            double cpuRecoverThreshold,
            int cpuRequiredConsecutiveSamples,
            double temperatureTriggerThreshold,
            double temperatureRecoverThreshold,
            double memoryTriggerThreshold,
            double memoryRecoverThreshold,
            int memoryRequiredConsecutiveSamples,
            Path databasePath) {
        this(
                host,
                port,
                authTimeoutMillis,
                maxMessageBytes,
                heartbeatIntervalMillis,
                offlineTimeoutMillis,
                cpuTriggerThreshold,
                cpuRecoverThreshold,
                cpuRequiredConsecutiveSamples,
                temperatureTriggerThreshold,
                temperatureRecoverThreshold,
                memoryTriggerThreshold,
                memoryRecoverThreshold,
                memoryRequiredConsecutiveSamples,
                databasePath,
                30,
                false,
                DEFAULT_TLS_PORT,
                null,
                "",
                null,
                "");
    }

    public AppConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("server.host must not be blank");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("server.port must be between 0 and 65535");
        }
        if (authTimeoutMillis <= 0) {
            throw new IllegalArgumentException("server.authTimeoutMillis must be positive");
        }
        if (maxMessageBytes <= 0 || maxMessageBytes > 1024 * 1024) {
            throw new IllegalArgumentException(
                    "server.maxMessageBytes must be between 1 and 1048576");
        }
        if (heartbeatIntervalMillis <= 0) {
            throw new IllegalArgumentException("device.heartbeatIntervalMillis must be positive");
        }
        if (offlineTimeoutMillis <= heartbeatIntervalMillis) {
            throw new IllegalArgumentException(
                    "device.offlineTimeoutMillis must be greater than the heartbeat interval");
        }
        if (cpuTriggerThreshold <= cpuRecoverThreshold) {
            throw new IllegalArgumentException(
                    "alert.cpu.triggerThreshold must be greater than the recover threshold");
        }
        if (cpuRequiredConsecutiveSamples <= 0) {
            throw new IllegalArgumentException(
                    "alert.cpu.requiredConsecutiveSamples must be positive");
        }
        if (temperatureTriggerThreshold <= temperatureRecoverThreshold) {
            throw new IllegalArgumentException(
                    "alert.temperature.triggerThreshold must be greater than the recover threshold");
        }
        if (memoryTriggerThreshold <= memoryRecoverThreshold) {
            throw new IllegalArgumentException(
                    "alert.memory.triggerThreshold must be greater than the recover threshold");
        }
        if (memoryRequiredConsecutiveSamples <= 0) {
            throw new IllegalArgumentException(
                    "alert.memory.requiredConsecutiveSamples must be positive");
        }
        if (databasePath == null) {
            throw new IllegalArgumentException("database.path must not be null");
        }
        if (telemetryRetentionDays <= 0) {
            throw new IllegalArgumentException("data.retentionDays must be positive");
        }
        if (tlsPort < 0 || tlsPort > 65535) {
            throw new IllegalArgumentException("server.tls.port must be between 0 and 65535");
        }
        if (tlsEnabled && tlsKeystorePath == null) {
            throw new IllegalArgumentException(
                    "server.tls.keystore is required when TLS is enabled");
        }
    }

    public static AppConfig defaults() {
        return new AppConfig(
                "0.0.0.0",
                DEFAULT_PORT,
                5_000,
                65_536,
                5_000,
                15_000,
                80,
                75,
                3,
                75,
                70,
                85,
                78,
                3,
                Path.of("data", "device-monitor.db"),
                30,
                false,
                DEFAULT_TLS_PORT,
                null,
                "",
                null,
                "");
    }

    public static AppConfig load(Path configFile) throws IOException {
        Properties defaults = toProperties(defaults());
        Properties values = new Properties();
        values.putAll(defaults);

        if (configFile != null && Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(
                    configFile, StandardCharsets.UTF_8)) {
                values.load(reader);
            }
        }

        Path keystore = nullablePath(values.getProperty("server.tls.keystore"));
        String keystorePassword =
                values.getProperty("server.tls.keystorePassword", "").trim();
        boolean tlsEnabled = Boolean.parseBoolean(
                values.getProperty("server.tls.enabled", "false").trim());
        int tlsPort = DEFAULT_TLS_PORT;
        try {
            tlsPort = parseInt(values, "server.tls.port");
        } catch (IllegalArgumentException exception) {
            tlsPort = DEFAULT_TLS_PORT;
        }
        if (tlsPort < 0 || tlsPort > 65535) {
            tlsPort = DEFAULT_TLS_PORT;
        }
        if (tlsEnabled && (keystore == null || !Files.exists(keystore))) {
            tlsEnabled = false;
        }
        Path truststore = nullablePath(values.getProperty("server.tls.truststore"));
        if (truststore == null) {
            truststore = keystore;
        }
        String truststorePassword =
                values.getProperty("server.tls.truststorePassword", keystorePassword).trim();

        return new AppConfig(
                values.getProperty("server.host").trim(),
                parseInt(values, "server.port"),
                parseInt(values, "server.authTimeoutMillis"),
                parseInt(values, "server.maxMessageBytes"),
                parseLong(values, "device.heartbeatIntervalMillis"),
                parseLong(values, "device.offlineTimeoutMillis"),
                parseDouble(values, "alert.cpu.triggerThreshold"),
                parseDouble(values, "alert.cpu.recoverThreshold"),
                parseInt(values, "alert.cpu.requiredConsecutiveSamples"),
                parseDouble(values, "alert.temperature.triggerThreshold"),
                parseDouble(values, "alert.temperature.recoverThreshold"),
                parseDouble(values, "alert.memory.triggerThreshold"),
                parseDouble(values, "alert.memory.recoverThreshold"),
                parseInt(values, "alert.memory.requiredConsecutiveSamples"),
                Path.of(values.getProperty("database.path").trim()),
                parseInt(values, "data.retentionDays"),
                tlsEnabled,
                tlsPort,
                keystore,
                keystorePassword,
                truststore,
                truststorePassword);
    }

    public AppConfig withPort(int newPort) {
        return new AppConfig(
                host,
                newPort,
                authTimeoutMillis,
                maxMessageBytes,
                heartbeatIntervalMillis,
                offlineTimeoutMillis,
                cpuTriggerThreshold,
                cpuRecoverThreshold,
                cpuRequiredConsecutiveSamples,
                temperatureTriggerThreshold,
                temperatureRecoverThreshold,
                memoryTriggerThreshold,
                memoryRecoverThreshold,
                memoryRequiredConsecutiveSamples,
                databasePath,
                telemetryRetentionDays,
                tlsEnabled,
                tlsPort,
                tlsKeystorePath,
                tlsKeystorePassword,
                tlsTruststorePath,
                tlsTruststorePassword);
    }

    public AppConfig withDatabasePath(Path newDatabasePath) {
        return new AppConfig(
                host,
                port,
                authTimeoutMillis,
                maxMessageBytes,
                heartbeatIntervalMillis,
                offlineTimeoutMillis,
                cpuTriggerThreshold,
                cpuRecoverThreshold,
                cpuRequiredConsecutiveSamples,
                temperatureTriggerThreshold,
                temperatureRecoverThreshold,
                memoryTriggerThreshold,
                memoryRecoverThreshold,
                memoryRequiredConsecutiveSamples,
                newDatabasePath,
                telemetryRetentionDays,
                tlsEnabled,
                tlsPort,
                tlsKeystorePath,
                tlsKeystorePassword,
                tlsTruststorePath,
                tlsTruststorePassword);
    }

    private static Properties toProperties(AppConfig config) {
        Properties properties = new Properties();
        properties.setProperty("server.host", config.host());
        properties.setProperty("server.port", Integer.toString(config.port()));
        properties.setProperty(
                "server.authTimeoutMillis", Integer.toString(config.authTimeoutMillis()));
        properties.setProperty(
                "server.maxMessageBytes", Integer.toString(config.maxMessageBytes()));
        properties.setProperty(
                "device.heartbeatIntervalMillis",
                Long.toString(config.heartbeatIntervalMillis()));
        properties.setProperty(
                "device.offlineTimeoutMillis", Long.toString(config.offlineTimeoutMillis()));
        properties.setProperty(
                "alert.cpu.triggerThreshold", Double.toString(config.cpuTriggerThreshold()));
        properties.setProperty(
                "alert.cpu.recoverThreshold", Double.toString(config.cpuRecoverThreshold()));
        properties.setProperty(
                "alert.cpu.requiredConsecutiveSamples",
                Integer.toString(config.cpuRequiredConsecutiveSamples()));
        properties.setProperty(
                "alert.temperature.triggerThreshold",
                Double.toString(config.temperatureTriggerThreshold()));
        properties.setProperty(
                "alert.temperature.recoverThreshold",
                Double.toString(config.temperatureRecoverThreshold()));
        properties.setProperty(
                "alert.memory.triggerThreshold",
                Double.toString(config.memoryTriggerThreshold()));
        properties.setProperty(
                "alert.memory.recoverThreshold",
                Double.toString(config.memoryRecoverThreshold()));
        properties.setProperty(
                "alert.memory.requiredConsecutiveSamples",
                Integer.toString(config.memoryRequiredConsecutiveSamples()));
        properties.setProperty("database.path", config.databasePath().toString());
        properties.setProperty(
                "data.retentionDays", Integer.toString(config.telemetryRetentionDays()));
        properties.setProperty(
                "server.tls.enabled", Boolean.toString(config.tlsEnabled()));
        properties.setProperty(
                "server.tls.port", Integer.toString(config.tlsPort()));
        if (config.tlsKeystorePath() != null) {
            properties.setProperty(
                    "server.tls.keystore", config.tlsKeystorePath().toString());
        }
        properties.setProperty(
                "server.tls.keystorePassword", config.tlsKeystorePassword());
        if (config.tlsTruststorePath() != null) {
            properties.setProperty(
                    "server.tls.truststore", config.tlsTruststorePath().toString());
        }
        properties.setProperty(
                "server.tls.truststorePassword", config.tlsTruststorePassword());
        return properties;
    }

    private static Path nullablePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value.trim());
    }

    private static int parseInt(Properties values, String key) {
        try {
            return Integer.parseInt(values.getProperty(key).trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static long parseLong(Properties values, String key) {
        try {
            return Long.parseLong(values.getProperty(key).trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static double parseDouble(Properties values, String key) {
        try {
            double value = Double.parseDouble(values.getProperty(key).trim());
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("not finite");
            }
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " must be a finite number", exception);
        }
    }
}

