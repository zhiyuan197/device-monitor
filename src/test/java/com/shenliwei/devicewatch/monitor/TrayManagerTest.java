package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Image;
import java.awt.SystemTray;
import org.junit.jupiter.api.Test;

/**
 * Pins the tray icon contract: a 16x16 image derived from the app icon,
 * and support reported only when the platform provides a system tray.
 */
class TrayManagerTest {

    @Test
    void trayImageScalesToSixteenPixels() {
        Image image = TrayManager.trayImage();
        assertNotNull(image);
        assertEquals(16, image.getWidth(null));
        assertEquals(16, image.getHeight(null));
    }

    @Test
    void supportedMatchesPlatformTraySupport() {
        assertEquals(SystemTray.isSupported(), TrayManager.supported());
    }
}
