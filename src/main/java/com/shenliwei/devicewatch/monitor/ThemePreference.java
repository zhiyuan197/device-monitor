package com.shenliwei.devicewatch.monitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Persists UI preferences (light/dark theme, alert sound) in a small
 * properties file. Missing or unreadable files fall back to safe defaults.
 */
final class ThemePreference {
    enum Theme {
        LIGHT,
        DARK
    }

    static final Path DEFAULT_PATH = Path.of("config", "ui.properties");

    private final Path file;

    ThemePreference(Path file) {
        this.file = file;
    }

    Theme current() {
        String value = loadProperties().getProperty("ui.theme", "light");
        return "dark".equalsIgnoreCase(value.trim()) ? Theme.DARK : Theme.LIGHT;
    }

    Theme toggle() {
        Theme next = current() == Theme.LIGHT ? Theme.DARK : Theme.LIGHT;
        Properties properties = loadProperties();
        properties.setProperty("ui.theme", next.name().toLowerCase(Locale.ROOT));
        save(properties);
        return next;
    }

    boolean alertSoundEnabled() {
        String value = loadProperties().getProperty("ui.alertSound", "true");
        return !"false".equalsIgnoreCase(value.trim());
    }

    boolean toggleAlertSound() {
        boolean next = !alertSoundEnabled();
        Properties properties = loadProperties();
        properties.setProperty("ui.alertSound", Boolean.toString(next));
        save(properties);
        return next;
    }

    boolean toastEnabled() {
        String value = loadProperties().getProperty("ui.alertToast", "true");
        return !"false".equalsIgnoreCase(value.trim());
    }

    boolean toggleToast() {
        boolean next = !toastEnabled();
        Properties properties = loadProperties();
        properties.setProperty("ui.alertToast", Boolean.toString(next));
        save(properties);
        return next;
    }

    boolean trayOnCloseEnabled() {
        String value = loadProperties().getProperty("ui.trayOnClose", "true");
        return !"false".equalsIgnoreCase(value.trim());
    }

    boolean toggleTrayOnClose() {
        boolean next = !trayOnCloseEnabled();
        Properties properties = loadProperties();
        properties.setProperty("ui.trayOnClose", Boolean.toString(next));
        save(properties);
        return next;
    }

    String rawValue() {
        return current().name().toLowerCase(Locale.ROOT);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            } catch (IOException exception) {
                return new Properties();
            }
        }
        return properties;
    }

    private void save(Properties properties) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "UI preferences");
            }
        } catch (IOException exception) {
            // The in-memory preference still applies; persistence is best effort.
        }
    }
}
