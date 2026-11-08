package com.shenliwei.devicewatch.monitor;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Optional system-tray residence: the monitor keeps receiving alerts in the
 * tray while the main window is hidden.
 */
final class TrayManager {
    private final TrayIcon icon;

    private TrayManager(TrayIcon icon) {
        this.icon = icon;
    }

    static boolean supported() {
        return SystemTray.isSupported();
    }

    static TrayManager install(DashboardFrame frame, Runnable onExit) throws Exception {
        SystemTray tray = SystemTray.getSystemTray();
        TrayIcon icon = new TrayIcon(trayImage(), "设备状态监控与告警系统");
        icon.setImageAutoSize(true);

        PopupMenu menu = new PopupMenu();
        MenuItem showItem = new MenuItem("显示主界面");
        showItem.addActionListener(event -> SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            frame.setExtendedState(JFrame.NORMAL);
            frame.toFront();
            frame.requestFocus();
        }));
        MenuItem exitItem = new MenuItem("退出程序");
        exitItem.addActionListener(event -> onExit.run());
        menu.add(showItem);
        menu.add(exitItem);
        icon.setPopupMenu(menu);
        icon.addActionListener(event -> SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            frame.setExtendedState(JFrame.NORMAL);
            frame.toFront();
            frame.requestFocus();
        }));
        tray.add(icon);
        return new TrayManager(icon);
    }

    void notify(String caption, String text) {
        icon.displayMessage(caption, text, TrayIcon.MessageType.INFO);
    }

    void dispose() {
        SystemTray.getSystemTray().remove(icon);
    }

    static Image trayImage() {
        Image source = UiStyles.appIcon();
        if (source == null) {
            return null;
        }
        BufferedImage scaled = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, 16, 16, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }
}
