package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;

/**
 * Pins the flat, calm design-token contract used by the polished UI.
 * Visual decisions live in one place so the rest of the app stays consistent.
 */
class UiStylesTest {

    @Test
    void chipIsFlatRectangleNotRounded() {
        JLabel chip = UiStyles.chip("在线", UiStyles.GREEN_SOFT, UiStyles.GREEN);
        assertNull(chip.getClientProperty("JComponent.roundRect"),
                "chips must be flat rectangles, not rounded pills");
    }

    @Test
    void cardUsesUniformCompactPadding() {
        JPanel panel = new JPanel();
        UiStyles.card(panel);
        Insets insets = panel.getBorder().getBorderInsets(panel);
        assertEquals(new Insets(15, 17, 15, 17), insets,
                "cards must share one frame: 1px line border + 14/16 padding");
    }

    @Test
    void primaryButtonUsesBrandFill() {
        JButton button = UiStyles.primaryButton("确定");
        assertEquals(UiStyles.PRIMARY, button.getBackground());
        assertEquals(Color.WHITE, button.getForeground());
        assertEquals(UiStyles.PRIMARY_DARK,
                button.getClientProperty("JButton.hoverBackground"));
    }

    @Test
    void statusChipKeepsPlainSemanticLabels() {
        assertEquals("在线", UiStyles.statusChip(true, true).getText());
        assertEquals("离线", UiStyles.statusChip(false, true).getText());
        assertEquals("已停用", UiStyles.statusChip(true, false).getText());
    }

    @Test
    void severityChipKeepsPlainSemanticLabels() {
        assertEquals("严重", UiStyles.severityChip("CRITICAL").getText());
        assertEquals("警告", UiStyles.severityChip("WARNING").getText());
        assertEquals("提示", UiStyles.severityChip("INFO").getText());
    }

    @Test
    void chipTextKeepsReadableContrast() {
        assertContrast(UiStyles.chip("在线", UiStyles.GREEN_SOFT, UiStyles.GREEN_TEXT));
        assertContrast(UiStyles.chip("离线", UiStyles.RED_SOFT, UiStyles.RED_TEXT));
        assertContrast(UiStyles.chip("已停用", UiStyles.NEUTRAL, UiStyles.TEXT));
        assertContrast(UiStyles.chip("严重", UiStyles.RED_SOFT, UiStyles.RED_TEXT));
        assertContrast(UiStyles.chip("警告", UiStyles.AMBER_SOFT, UiStyles.AMBER_TEXT));
        assertContrast(UiStyles.chip("提示", UiStyles.BLUE_SOFT, UiStyles.BLUE_TEXT));
    }

    @Test
    void darkPaletteKeepsReadableContrast() {
        try {
            UiStyles.applyPalette(ThemePreference.Theme.DARK);
            assertContrast(UiStyles.chip("在线", UiStyles.GREEN_SOFT, UiStyles.GREEN_TEXT));
            assertContrast(UiStyles.chip("离线", UiStyles.RED_SOFT, UiStyles.RED_TEXT));
            assertContrast(UiStyles.chip("已停用", UiStyles.NEUTRAL, UiStyles.TEXT));
            assertContrast(UiStyles.chip("严重", UiStyles.RED_SOFT, UiStyles.RED_TEXT));
            assertContrast(UiStyles.chip("警告", UiStyles.AMBER_SOFT, UiStyles.AMBER_TEXT));
            assertContrast(UiStyles.chip("提示", UiStyles.BLUE_SOFT, UiStyles.BLUE_TEXT));
        } finally {
            UiStyles.applyPalette(ThemePreference.Theme.LIGHT);
        }
    }

    private static void assertContrast(JLabel chip) {
        double ratio = contrastRatio(chip.getForeground(), chip.getBackground());
        org.junit.jupiter.api.Assertions.assertTrue(ratio >= 4.5,
                () -> chip.getText() + " chip contrast " + ratio + " must be >= 4.5");
    }

    private static double contrastRatio(Color foreground, Color background) {
        double light = Math.max(luminance(foreground), luminance(background));
        double dark = Math.min(luminance(foreground), luminance(background));
        return (light + 0.05) / (dark + 0.05);
    }

    private static double luminance(Color color) {
        double[] channels = {
            color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0
        };
        double[] linear = new double[3];
        for (int i = 0; i < channels.length; i++) {
            linear[i] = channels[i] <= 0.03928
                    ? channels[i] / 12.92
                    : Math.pow((channels[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
    }

    @Test
    void countBadgeUsesSolidAccentFillAndWhiteText() {
        JLabel badge = UiStyles.countBadge();
        assertTrue(badge.isOpaque());
        assertEquals(UiStyles.RED, badge.getBackground());
        assertEquals(Color.WHITE, badge.getForeground());
    }

    @Test
    void vectorIconsLoadFromBundledSet() {
        Icon icon = UiStyles.icon("bell", 16, UiStyles.PRIMARY);
        assertEquals(16, icon.getIconWidth());
        assertEquals(16, icon.getIconHeight());
    }
}
