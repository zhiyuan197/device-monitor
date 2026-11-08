package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the theme persistence contract: light by default, toggle round-trips
 * through the on-disk properties file.
 */
class ThemePreferenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultsToLightWhenFileIsMissing() {
        ThemePreference preference =
                new ThemePreference(temporaryDirectory.resolve("ui.properties"));
        assertEquals(ThemePreference.Theme.LIGHT, preference.current());
    }

    @Test
    void togglePersistsAndReloads() {
        Path file = temporaryDirectory.resolve("ui.properties");
        ThemePreference preference = new ThemePreference(file);
        assertEquals(ThemePreference.Theme.LIGHT, preference.current());

        ThemePreference.Theme switched = preference.toggle();
        assertEquals(ThemePreference.Theme.DARK, switched);
        assertEquals("dark", preference.rawValue());

        ThemePreference reloaded = new ThemePreference(file);
        assertEquals(ThemePreference.Theme.DARK, reloaded.current());
    }

    @Test
    void alertSoundDefaultsToEnabledAndPersists() {
        Path file = temporaryDirectory.resolve("ui.properties");
        ThemePreference preference = new ThemePreference(file);
        assertTrue(preference.alertSoundEnabled());

        boolean switched = preference.toggleAlertSound();
        assertFalse(switched);

        ThemePreference reloaded = new ThemePreference(file);
        assertFalse(reloaded.alertSoundEnabled());
    }

    @Test
    void toastDefaultsToEnabledAndPersists() {
        Path file = temporaryDirectory.resolve("ui.properties");
        ThemePreference preference = new ThemePreference(file);
        assertTrue(preference.toastEnabled());

        assertFalse(preference.toggleToast());

        ThemePreference reloaded = new ThemePreference(file);
        assertFalse(reloaded.toastEnabled());
    }

    @Test
    void trayOnCloseDefaultsToEnabledAndPersists() {
        Path file = temporaryDirectory.resolve("ui.properties");
        ThemePreference preference = new ThemePreference(file);
        assertTrue(preference.trayOnCloseEnabled());

        assertFalse(preference.toggleTrayOnClose());

        ThemePreference reloaded = new ThemePreference(file);
        assertFalse(reloaded.trayOnCloseEnabled());
    }
}
