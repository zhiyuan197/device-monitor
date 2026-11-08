package com.shenliwei.devicewatch.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsValidConfigurationOverrides() throws Exception {
        Path file = temporaryDirectory.resolve("server.properties");
        Files.writeString(file, """
                server.host=127.0.0.1
                server.port=6060
                server.authTimeoutMillis=3000
                server.maxMessageBytes=32768
                device.heartbeatIntervalMillis=1000
                device.offlineTimeoutMillis=5000
                alert.cpu.triggerThreshold=90
                alert.cpu.recoverThreshold=80
                alert.cpu.requiredConsecutiveSamples=4
                alert.temperature.triggerThreshold=80
                alert.temperature.recoverThreshold=72
                data.retentionDays=45
                database.path=data/test.db
                """);

        AppConfig config = AppConfig.load(file);

        assertEquals(6060, config.port());
        assertEquals(32768, config.maxMessageBytes());
        assertEquals(90, config.cpuTriggerThreshold());
        assertEquals(45, config.telemetryRetentionDays());
        assertEquals(Path.of("data/test.db"), config.databasePath());
    }

    @Test
    void defaultsToThirtyDayRetention() {
        assertEquals(30, AppConfig.defaults().telemetryRetentionDays());
    }

    @Test
    void rejectsInvalidThresholdOrdering() throws Exception {
        Path file = temporaryDirectory.resolve("invalid.properties");
        Files.writeString(file, """
                alert.cpu.triggerThreshold=70
                alert.cpu.recoverThreshold=75
                """);

        assertThrows(IllegalArgumentException.class, () -> AppConfig.load(file));
    }

    @Test
    void tlsIsDisabledByDefault() {
        AppConfig config = AppConfig.defaults();
        assertFalse(config.tlsEnabled());
        assertEquals(5051, config.tlsPort());
    }

    @Test
    void loadsTlsConfigurationWhenKeystoreExists() throws Exception {
        Path keystore = temporaryDirectory.resolve("server-keystore.p12");
        Files.writeString(keystore, "fake-keystore-bytes");
        String keystoreProperty = keystore.toAbsolutePath().toString().replace('\\', '/');
        Path file = temporaryDirectory.resolve("server.properties");
        Files.writeString(file, """
                server.tls.enabled=true
                server.tls.port=6061
                server.tls.keystore=%s
                server.tls.keystorePassword=secret
                server.tls.truststore=%s
                server.tls.truststorePassword=secret
                """.formatted(keystoreProperty, keystoreProperty));

        AppConfig config = AppConfig.load(file);

        assertTrue(config.tlsEnabled());
        assertEquals(6061, config.tlsPort());
        assertEquals(keystore.toAbsolutePath(), config.tlsKeystorePath());
        assertEquals("secret", config.tlsKeystorePassword());
        assertEquals(keystore.toAbsolutePath(), config.tlsTruststorePath());
    }

    @Test
    void tlsIsForcedOffWhenKeystoreIsMissing() throws Exception {
        Path file = temporaryDirectory.resolve("server.properties");
        Files.writeString(file, """
                server.tls.enabled=true
                server.tls.keystore=absent-keystore.p12
                server.tls.keystorePassword=secret
                """);

        AppConfig config = AppConfig.load(file);

        assertFalse(config.tlsEnabled());
    }

    @Test
    void invalidTlsPortFallsBackToDefault() throws Exception {
        Path keystore = temporaryDirectory.resolve("server-keystore.p12");
        Files.writeString(keystore, "fake-keystore-bytes");
        String keystoreProperty = keystore.toAbsolutePath().toString().replace('\\', '/');
        Path file = temporaryDirectory.resolve("server.properties");
        Files.writeString(file, """
                server.tls.enabled=true
                server.tls.port=70000
                server.tls.keystore=%s
                server.tls.keystorePassword=secret
                """.formatted(keystoreProperty));

        AppConfig config = AppConfig.load(file);

        assertEquals(5051, config.tlsPort());
    }
}

