package com.shenliwei.devicewatch.monitor;

import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.simulator.SimulatorConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public final class MonitorApp {
    private MonitorApp() {
    }

    public static void run(String[] args) throws Exception {
        MonitorOptions options;
        try {
            options = MonitorOptions.parse(args);
        } catch (MonitorOptions.HelpRequestedException help) {
            System.out.println(
                    "monitor [--config config/server.properties] "
                            + "[--demo --demo-config config/demo.properties] "
                            + "[--demo-stop-on-exit]");
            return;
        }
        AppConfig config = AppConfig.load(options.configPath());
        if (options.demoMode()) {
            if (!Files.exists(options.demoConfigPath())) {
                throw new IllegalArgumentException(
                        "Demo config not found: "
                                + options.demoConfigPath().toAbsolutePath());
            }
            SimulatorConfig demo = SimulatorConfig.load(options.demoConfigPath());
            SwingUtilities.invokeLater(() -> {
                UiStyles.apply();
                DashboardFrame dashboard = new DashboardFrame(
                        demo.host(),
                        demo.tlsEnabled() ? demo.tlsPort() : demo.port(),
                        demo.monitorUsername(),
                        demo.monitorPassword().toCharArray(),
                        options.stopDemoOnExit(),
                        demo.tlsEnabled(),
                        demo.truststorePath(),
                        demo.truststorePassword());
                dashboard.setVisible(true);
                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        dashboard.connectNow();
                        return null;
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                        } catch (Exception exception) {
                            Throwable cause =
                                    exception.getCause() == null ? exception : exception.getCause();
                            dashboard.onConnectionState(false, "演示登录失败：" + cause.getMessage());
                        }
                    }
                }.execute();
            });
            return;
        }
        SwingUtilities.invokeLater(() -> {
            UiStyles.apply();
            new LoginFrame(config, options.stopDemoOnExit()).setVisible(true);
        });
    }
}

