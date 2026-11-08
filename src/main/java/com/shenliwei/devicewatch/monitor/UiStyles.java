package com.shenliwei.devicewatch.monitor;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Central design tokens and small reusable components for the monitor UI.
 *
 * <p>Visual language: flat, calm, Swiss-minimal. One accent color (brand
 * blue), semantic colors only where they carry meaning (green/red/amber
 * for status), no gradients, no decorative shadows, no violet.
 */
final class UiStyles {
    static Image appIcon() {
        try (InputStream input = UiStyles.class.getResourceAsStream("/app-icon.png")) {
            return input == null ? null : ImageIO.read(input);
        } catch (IOException exception) {
            return null;
        }
    }

    static Color PRIMARY = new Color(37, 99, 235);
    static Color PRIMARY_DARK = new Color(29, 78, 216);
    static Color PRIMARY_SOFT = new Color(224, 238, 255);
    static Color BACKGROUND = new Color(247, 249, 251);
    static Color SURFACE = Color.WHITE;
    static Color SIDEBAR = new Color(248, 250, 252);
    static Color TEXT = new Color(15, 23, 42);
    static Color MUTED = new Color(100, 116, 139);
    static Color BORDER = new Color(226, 232, 240);

    static Color GREEN = new Color(5, 150, 105);
    static Color GREEN_SOFT = new Color(209, 250, 229);
    static Color GREEN_TEXT = new Color(4, 120, 87);
    static Color RED = new Color(239, 68, 68);
    static Color RED_SOFT = new Color(254, 226, 226);
    static Color RED_TEXT = new Color(185, 28, 28);
    static Color AMBER = new Color(217, 119, 6);
    static Color AMBER_SOFT = new Color(254, 243, 199);
    static Color AMBER_TEXT = new Color(146, 64, 14);
    static Color BLUE = new Color(59, 130, 246);
    static Color BLUE_SOFT = new Color(219, 234, 254);
    static Color BLUE_TEXT = new Color(29, 78, 216);
    static Color CYAN = new Color(8, 145, 178);
    static Color NEUTRAL = new Color(241, 245, 249);

    static final Font FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
    static final Font FONT_BOLD = FONT.deriveFont(Font.BOLD);
    static final Font TITLE = FONT.deriveFont(Font.BOLD, 20f);
    static final Font SECTION = FONT.deriveFont(Font.BOLD, 15f);
    static final Font SMALL = FONT.deriveFont(12f);
    static final Font METRIC = FONT.deriveFont(Font.BOLD, 24f);

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SHORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private UiStyles() {
    }

    static void apply() {
        ThemePreference.Theme theme = ThemeManager.preference().current();
        if (theme == ThemePreference.Theme.DARK) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        applyPalette(theme);
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 6);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.focusColor", PRIMARY);
        UIManager.put("Component.accentColor", PRIMARY);
        UIManager.put("ScrollBar.width", 11);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.selectionBackground",
                theme == ThemePreference.Theme.DARK
                        ? new Color(37, 48, 66)
                        : new Color(233, 240, 253));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.selectionInactiveBackground",
                theme == ThemePreference.Theme.DARK
                        ? new Color(30, 36, 45)
                        : new Color(240, 244, 249));
        UIManager.put("Button.hoverBackground",
                theme == ThemePreference.Theme.DARK
                        ? new Color(40, 46, 56)
                        : new Color(240, 243, 248));
        UIManager.put("Label.font", FONT);
        UIManager.put("Button.font", FONT);
        UIManager.put("TextField.font", FONT);
        UIManager.put("PasswordField.font", FONT);
        UIManager.put("ComboBox.font", FONT);
        UIManager.put("Table.font", FONT);
        UIManager.put("TableHeader.font", FONT_BOLD);
        UIManager.put("TitledBorder.font", FONT_BOLD);
    }

    static void applyPalette(ThemePreference.Theme theme) {
        boolean dark = theme == ThemePreference.Theme.DARK;
        PRIMARY = dark ? new Color(59, 130, 246) : new Color(37, 99, 235);
        PRIMARY_DARK = dark ? new Color(37, 99, 235) : new Color(29, 78, 216);
        PRIMARY_SOFT = dark ? new Color(29, 42, 69) : new Color(224, 238, 255);
        BACKGROUND = dark ? new Color(18, 21, 26) : new Color(247, 249, 251);
        SURFACE = dark ? new Color(27, 31, 38) : Color.WHITE;
        SIDEBAR = dark ? new Color(21, 25, 32) : new Color(248, 250, 252);
        TEXT = dark ? new Color(231, 234, 238) : new Color(15, 23, 42);
        MUTED = dark ? new Color(152, 162, 179) : new Color(100, 116, 139);
        BORDER = dark ? new Color(43, 49, 59) : new Color(226, 232, 240);

        GREEN = dark ? new Color(52, 211, 153) : new Color(5, 150, 105);
        GREEN_SOFT = dark ? new Color(18, 51, 40) : new Color(209, 250, 229);
        GREEN_TEXT = dark ? new Color(110, 231, 183) : new Color(4, 120, 87);
        RED = dark ? new Color(248, 113, 113) : new Color(239, 68, 68);
        RED_SOFT = dark ? new Color(59, 30, 34) : new Color(254, 226, 226);
        RED_TEXT = dark ? new Color(252, 165, 165) : new Color(185, 28, 28);
        AMBER = dark ? new Color(251, 191, 36) : new Color(217, 119, 6);
        AMBER_SOFT = dark ? new Color(58, 46, 20) : new Color(254, 243, 199);
        AMBER_TEXT = dark ? new Color(252, 211, 77) : new Color(146, 64, 14);
        BLUE = dark ? new Color(96, 165, 250) : new Color(59, 130, 246);
        BLUE_SOFT = dark ? new Color(28, 42, 71) : new Color(219, 234, 254);
        BLUE_TEXT = dark ? new Color(147, 197, 253) : new Color(29, 78, 216);
        CYAN = dark ? new Color(34, 211, 238) : new Color(8, 145, 178);
        NEUTRAL = dark ? new Color(37, 43, 52) : new Color(241, 245, 249);
    }

    static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setForeground(Color.WHITE);
        button.setBackground(PRIMARY);
        button.setFont(FONT_BOLD);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.buttonType", "borderless");
        button.putClientProperty("JButton.hoverBackground", PRIMARY_DARK);
        button.putClientProperty("JButton.pressedBackground", PRIMARY_DARK);
        return button;
    }

    static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setForeground(TEXT);
        button.setBackground(SURFACE);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.hoverBackground",
                BACKGROUND.brighter());
        return button;
    }

    static JButton dangerButton(String text) {
        JButton button = new JButton(text);
        button.setForeground(RED);
        button.setBackground(SURFACE);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.hoverBackground", RED_SOFT);
        return button;
    }

    /**
     * Shared metric card: muted caption on top, accent value centered.
     */
    static JPanel metricCard(
            String caption,
            JLabel value,
            Color accent,
            int verticalPadding,
            int horizontalPadding) {
        JPanel card = new JPanel();
        card.setLayout(new java.awt.BorderLayout(0, 2));
        card.setBackground(SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        BorderFactory.createEmptyBorder(
                                verticalPadding, horizontalPadding,
                                verticalPadding, horizontalPadding))));
        JLabel label = new JLabel(caption);
        label.setForeground(MUTED);
        label.setFont(SMALL);
        value.setForeground(accent);
        card.add(label, java.awt.BorderLayout.NORTH);
        card.add(value, java.awt.BorderLayout.CENTER);
        return card;
    }

    /**
     * Theme-aware vector icon from the bundled Lucide icon set (ISC).
     */
    static Icon icon(String name, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/" + name + ".svg");
        icon.setColorFilter(new FlatSVGIcon.ColorFilter() {
            @Override
            public Color filter(Color ignored) {
                return color;
            }
        });
        return icon.derive(size, size);
    }

    /**
     * Standard flat card: white surface, 1px border, one shared padding scale.
     */
    static void card(JComponent component) {
        card(component, 14, 16);
    }

    static void card(JComponent component, int verticalPadding, int horizontalPadding) {
        component.setBackground(SURFACE);
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(
                        verticalPadding, horizontalPadding,
                        verticalPadding, horizontalPadding)));
    }

    /**
     * Flat semantic chip: soft background, darker text, no rounding tricks.
     */
    static JLabel chip(String text, Color background, Color foreground) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(background);
        label.setForeground(foreground);
        label.setFont(FONT.deriveFont(Font.BOLD, 12f));
        label.setBorder(BorderFactory.createEmptyBorder(2, 9, 2, 9));
        return label;
    }

    static JLabel severityChip(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> chip("严重", RED_SOFT, RED_TEXT);
            case "WARNING" -> chip("警告", AMBER_SOFT, AMBER_TEXT);
            case "INFO" -> chip("提示", BLUE_SOFT, BLUE_TEXT);
            default -> chip("未知", NEUTRAL, TEXT);
        };
    }

    static JLabel statusChip(boolean online, boolean enabled) {
        if (!enabled) {
            return chip("已停用", NEUTRAL, TEXT);
        }
        return online
                ? chip("在线", GREEN_SOFT, GREEN_TEXT)
                : chip("离线", RED_SOFT, RED_TEXT);
    }

    /**
     * Numeric notification badge: solid accent fill with white text.
     */
    static JLabel countBadge() {
        JLabel badge = new JLabel();
        badge.setOpaque(true);
        badge.setBackground(RED);
        badge.setForeground(Color.WHITE);
        badge.setFont(FONT.deriveFont(Font.BOLD, 11f));
        badge.setHorizontalAlignment(JLabel.CENTER);
        badge.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
        return badge;
    }

    static String formatTime(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    static String formatShortTime(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return SHORT_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        if (days > 0) {
            return "%d 天 %d 小时 %d 分".formatted(days, hours, minutes);
        }
        if (hours > 0) {
            return "%d 小时 %d 分".formatted(hours, minutes);
        }
        return "%d 分 %d 秒".formatted(minutes, seconds % 60);
    }

    static String formatPercent(Double value) {
        return value == null ? "-" : "%.1f%%".formatted(value);
    }

    static String formatTemperature(Double value) {
        return value == null ? "-" : "%.1f °C".formatted(value);
    }

    static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    static JLabel pageTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(TITLE);
        label.setForeground(TEXT);
        return label;
    }

    static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "-";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return unit == 0
                ? bytes + " B"
                : String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
