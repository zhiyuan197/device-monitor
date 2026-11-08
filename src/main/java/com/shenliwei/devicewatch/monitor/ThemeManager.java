package com.shenliwei.devicewatch.monitor;

/**
 * Process-wide holder for the persisted theme preference.
 */
final class ThemeManager {
    private static ThemePreference preference;

    private ThemeManager() {
    }

    static ThemePreference preference() {
        if (preference == null) {
            preference = new ThemePreference(ThemePreference.DEFAULT_PATH);
        }
        return preference;
    }
}
