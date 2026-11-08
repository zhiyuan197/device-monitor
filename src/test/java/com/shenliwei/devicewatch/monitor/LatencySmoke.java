package com.shenliwei.devicewatch.monitor;

import com.shenliwei.devicewatch.simulator.SimulatorConfig;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

/**
 * Development smoke: connects a monitor and prints the first broadcast
 * link-latency value. Requires a running server and simulator.
 */
public final class LatencySmoke {
    private LatencySmoke() {
    }

    public static void main(String[] args) throws Exception {
        SimulatorConfig demo = SimulatorConfig.load(Path.of("config", "demo.properties"));
        DashboardFrame frame = new DashboardFrame(
                demo.host(), demo.port(),
                demo.monitorUsername(), demo.monitorPassword().toCharArray());
        SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
        frame.connectNow();
        long deadline = System.currentTimeMillis() + 12_000;
        long latency = -1;
        while (System.currentTimeMillis() < deadline) {
            DeviceView device = frame.devices.get("DEV-001");
            if (device != null && device.latencyMillis() > 0) {
                latency = device.latencyMillis();
                break;
            }
            Thread.sleep(250);
        }
        if (latency < 0) {
            System.out.println("LATENCY SMOKE FAIL: no latency sample received");
            System.exit(1);
        }
        System.out.println("LATENCY SMOKE PASS: observed latencyMillis=" + latency);
        frame.dispose();
        System.exit(0);
    }
}
