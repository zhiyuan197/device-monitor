package com.shenliwei.devicewatch.web;

import com.shenliwei.devicewatch.monitor.AlertView;
import com.shenliwei.devicewatch.monitor.DeviceRulesView;
import com.shenliwei.devicewatch.monitor.DeviceView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manual verification helper: starts the loopback dashboard server with
 * representative demo data and keeps it alive so a headless browser can
 * capture the rendered big screen. Test scope only.
 */
public final class DashboardCaptureTool {

    private DashboardCaptureTool() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8088;
        WebDashboard dashboard = new WebDashboard(
                new WebDashboardConfig(true, "127.0.0.1", port),
                "127.0.0.1", 5050);
        dashboard.onDeviceSnapshot(demoDevices());
        dashboard.onAlertSnapshot(demoAlerts());
        dashboard.start();
        System.out.println("DASHBOARD_URL=" + dashboard.url());

        ScheduledExecutorService jitter = Executors.newSingleThreadScheduledExecutor();
        final long[] tick = {0};
        final int[] lastSessions = {-1};
        jitter.scheduleAtFixedRate(() -> {
            tick[0]++;
            int sessions = dashboard.sessionCount();
            if (sessions != lastSessions[0]) {
                lastSessions[0] = sessions;
                System.out.println("SESSIONS=" + sessions);
            }
            double cpu = 28 + 14 * Math.sin(tick[0] / 5.0);
            double latency = 22 + 18 * Math.abs(Math.sin(tick[0] / 7.0));
            dashboard.onDeviceUpdate(device(
                    "DEV-002", "Web 服务器", true, false,
                    Math.round(latency), cpu, 51.0, 46.0));
        }, 1, 1, TimeUnit.SECONDS);

        TimeUnit.MINUTES.sleep(5);
        jitter.shutdownNow();
        dashboard.stop();
    }

    private static List<DeviceView> demoDevices() {
        List<DeviceView> devices = new ArrayList<>();
        devices.add(device("DEV-001", "本机", true, true, 12, 34.0, 42.0, 41.0));
        devices.add(device("DEV-002", "Web 服务器", true, false, 24, 51.0, 58.0, 46.0));
        devices.add(device("DEV-003", "数据库服务器", true, false, 36, 63.0, 71.0, 44.0));
        devices.add(device("DEV-004", "文件服务器", true, false, 58, 42.0, 47.0, 39.0));
        devices.add(device("DEV-005", "网关设备", true, false, 96, 28.0, 36.0, 37.0));
        devices.add(device("DEV-006", "备份节点", true, false, 183, 22.0, 31.0, 35.0));
        devices.add(device("DEV-007", "测试终端", false, false, 0, 0.0, 0.0, 0.0));
        return devices;
    }

    private static List<AlertView> demoAlerts() {
        long now = System.currentTimeMillis();
        return List.of(
                new AlertView(1, "DEV-006", "CPU", "WARN", "ACTIVE",
                        87.1, now - 65_000, null, null, "", ""),
                new AlertView(2, "DEV-005", "TEMP", "CRITICAL", "ACTIVE",
                        78.5, now - 12_000, null, null, "", ""),
                new AlertView(3, "DEV-007", "OFFLINE", "WARN", "ACTIVE",
                        null, now - 240_000, null, null, "", ""),
                new AlertView(4, "DEV-005", "SERVICE_DOWN", "WARN", "ACTIVE",
                        null, now - 8_000, null, null, "", ""));
    }

    private static DeviceView device(
            String id, String name, boolean online, boolean real,
            long latency, double cpu, double memory, double temperature) {
        return new DeviceView(
                id, name, "演示机房", true, online,
                System.currentTimeMillis(), System.currentTimeMillis() - 60_000,
                online ? cpu : null, online ? memory : null,
                online ? temperature : null, online ? 40.0 : null,
                new DeviceRulesView(80, 75, 3, 85, 78, 3, 75, 70),
                latency, 12.5, real);
    }
}
