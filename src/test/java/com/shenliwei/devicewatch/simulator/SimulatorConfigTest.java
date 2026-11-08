package com.shenliwei.devicewatch.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimulatorConfigTest {

    @TempDir
    Path temporaryDirectory;

    private Path demoFile() throws Exception {
        Path file = temporaryDirectory.resolve("demo.properties");
        Files.writeString(file, """
                server.host=127.0.0.1
                server.port=5050
                monitor.username=monitor
                monitor.password=pw
                device.count=1
                device.0.id=DEV-001
                device.0.credential=cred
                """);
        return file;
    }

    @Test
    void tlsIsDisabledByDefault() throws Exception {
        SimulatorConfig config = SimulatorConfig.load(demoFile());
        assertFalse(config.tlsEnabled());
        assertNull(config.truststorePath());
        assertEquals(5051, config.tlsPort());
    }

    @Test
    void loadsTlsTruststore() throws Exception {
        Path file = demoFile();
        Files.writeString(file, """
                tls.enabled=true
                tls.truststore=config/tls/client-truststore.p12
                tls.truststorePassword=secret
                """, java.nio.file.StandardOpenOption.APPEND);
        SimulatorConfig config = SimulatorConfig.load(file);
        assertTrue(config.tlsEnabled());
        assertEquals(Path.of("config/tls/client-truststore.p12"), config.truststorePath());
        assertEquals("secret", config.truststorePassword());
    }
}
