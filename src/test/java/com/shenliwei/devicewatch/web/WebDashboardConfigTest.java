package com.shenliwei.devicewatch.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebDashboardConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsEnableDashboardOnLoopbackPort8088() {
        WebDashboardConfig config = WebDashboardConfig.defaults();
        assertTrue(config.enabled());
        assertEquals("127.0.0.1", config.bindHost());
        assertEquals(8088, config.port());
        assertEquals("http://127.0.0.1:8088/", config.url());
    }

    @Test
    void loadReadsOverridesFromUiProperties() throws Exception {
        Path file = tempDir.resolve("ui.properties");
        Files.writeString(file, "web.enabled=false\nweb.bind=localhost\nweb.port=9099\n");
        WebDashboardConfig config = WebDashboardConfig.load(file);
        assertFalse(config.enabled());
        assertEquals("localhost", config.bindHost());
        assertEquals(9099, config.port());
    }

    @Test
    void loadFallsBackToDefaultsForInvalidValues() throws Exception {
        Path file = tempDir.resolve("ui.properties");
        Files.writeString(file, "web.enabled=not-a-bool\nweb.bind=0.0.0.0\nweb.port=70000\n");
        WebDashboardConfig config = WebDashboardConfig.load(file);
        assertTrue(config.enabled());
        assertEquals("127.0.0.1", config.bindHost());
        assertEquals(8088, config.port());
    }

    @Test
    void loadFallsBackToDefaultsWhenFileIsMissing() {
        WebDashboardConfig config = WebDashboardConfig.load(tempDir.resolve("absent.properties"));
        assertEquals(WebDashboardConfig.defaults(), config);
    }

    @Test
    void constructorRejectsUnsafeBindHost() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebDashboardConfig(true, "0.0.0.0", 8088));
    }

    @Test
    void constructorRejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebDashboardConfig(true, "127.0.0.1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WebDashboardConfig(true, "127.0.0.1", 65536));
    }
}
