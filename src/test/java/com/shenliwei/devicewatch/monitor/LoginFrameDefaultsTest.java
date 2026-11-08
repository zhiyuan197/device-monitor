package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shenliwei.devicewatch.common.AppConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoginFrameDefaultsTest {

    @TempDir
    Path temporaryDirectory;

    private AppConfig configWithTls(boolean enabled) throws Exception {
        Path file = temporaryDirectory.resolve("server.properties");
        String keystore = temporaryDirectory.resolve("ks.p12")
                .toAbsolutePath().toString().replace('\\', '/');
        Files.writeString(temporaryDirectory.resolve("ks.p12"), "fake");
        Files.writeString(file, """
                server.host=127.0.0.1
                server.port=5050
                server.tls.enabled=%s
                server.tls.port=5051
                server.tls.keystore=%s
                server.tls.keystorePassword=secret
                """.formatted(enabled, keystore));
        return AppConfig.load(file);
    }

    private static Properties demoWithPort(int port) {
        Properties demo = new Properties();
        demo.setProperty("server.host", "127.0.0.1");
        demo.setProperty("server.port", Integer.toString(port));
        return demo;
    }

    @Test
    void keepsTlsPortWhenTlsIsEnabledEvenIfDemoSaysPlaintextPort() throws Exception {
        AppConfig config = configWithTls(true);
        assertEquals(5051, LoginFrame.defaultPort(config, demoWithPort(5050)));
    }

    @Test
    void usesDemoPortWhenTlsIsDisabled() throws Exception {
        AppConfig config = configWithTls(false);
        assertEquals(5050, LoginFrame.defaultPort(config, demoWithPort(5050)));
    }

    @Test
    void fallsBackToConfigPortWhenDemoPortIsGarbage() throws Exception {
        AppConfig config = configWithTls(false);
        Properties demo = demoWithPort(5050);
        demo.setProperty("server.port", "not-a-port");
        assertEquals(5050, LoginFrame.defaultPort(config, demo));
    }
}
