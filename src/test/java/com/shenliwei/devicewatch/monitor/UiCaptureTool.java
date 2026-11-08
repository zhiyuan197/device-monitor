package com.shenliwei.devicewatch.monitor;

import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.simulator.SimulatorConfig;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Development tool that renders the real UI pages to PNG files without
 * occluding windows. It requires a running server and simulator.
 */
public final class UiCaptureTool {
    private UiCaptureTool() {
    }

    public static void main(String[] args) throws Exception {
        Path outDir = args.length > 0 ? Path.of(args[0]) : Path.of("screenshots");
        Files.createDirectories(outDir);

        AppConfig config = AppConfig.load(Path.of("config", "server.properties"));
        UiStyles.apply();

        LoginFrame login = new LoginFrame(config);
        showAndValidate(login);
        capture(login.getContentPane(), outDir.resolve("01-login.png"));
        login.dispose();

        SimulatorConfig demo = SimulatorConfig.load(Path.of("config", "demo.properties"));
        DashboardFrame dashboard = new DashboardFrame(
                demo.host(),
                demo.port(),
                demo.monitorUsername(),
                demo.monitorPassword().toCharArray());
        showAndValidate(dashboard);
        dashboard.connectNow();
        waitUntil(() -> dashboard.devices.size() >= 3, 15_000);

        // Let the mixed scenarios produce trends and a few alerts.
        Thread.sleep(16_000);
        capture(dashboard.getContentPane(), outDir.resolve("02-overview.png"));

        dashboard.showPageForCapture(DashboardFrame.PAGE_ALERTS);
        Thread.sleep(600);
        capture(dashboard.getContentPane(), outDir.resolve("03-alerts.png"));

        dashboard.showPageForCapture(DashboardFrame.PAGE_DEVICES);
        Thread.sleep(600);
        capture(dashboard.getContentPane(), outDir.resolve("04-devices.png"));

        dashboard.showPageForCapture(DashboardFrame.PAGE_MESSAGES);
        Thread.sleep(1_200);
        capture(dashboard.getContentPane(), outDir.resolve("05-messages.png"));

        dashboard.openReports("DEV-002");
        Thread.sleep(1_200);
        capture(dashboard.getContentPane(), outDir.resolve("06-reports.png"));

        dashboard.showPageForCapture(DashboardFrame.PAGE_SYSTEM);
        Thread.sleep(600);
        capture(dashboard.getContentPane(), outDir.resolve("07-system.png"));

        DemoControlDialog demoControl = new DemoControlDialog(dashboard, dashboard);
        demoControl.setLocationRelativeTo(dashboard);
        demoControl.setVisible(true);
        Thread.sleep(600);
        capture(demoControl.getContentPane(), outDir.resolve("08-demo-control.png"));
        demoControl.dispose();

        dashboard.toggleThemeForCapture();
        Thread.sleep(1_000);
        dashboard.showPageForCapture(DashboardFrame.PAGE_OVERVIEW);
        Thread.sleep(600);
        capture(dashboard.getContentPane(), outDir.resolve("09-overview-dark.png"));
        dashboard.dispose();
        System.out.println("Screenshots written to " + outDir.toAbsolutePath());
        System.exit(0);
    }

    private static void showAndValidate(java.awt.Window window) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            window.setLocationRelativeTo(null);
            window.setVisible(true);
            window.toFront();
        });
        Thread.sleep(800);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, long timeoutMillis)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean ready = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) {
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Timed out waiting for UI data");
    }

    private static void capture(java.awt.Container component, Path file) throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                component.doLayout();
                component.validate();
                BufferedImage image = new BufferedImage(
                        Math.max(1, component.getWidth()),
                        Math.max(1, component.getHeight()),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                component.paint(g);
                g.dispose();
                ImageIO.write(image, "png", file.toFile());
                done.set(true);
            } catch (Exception exception) {
                failure[0] = exception;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        if (!done.get()) {
            throw new IllegalStateException("Capture did not complete: " + file);
        }
    }
}
