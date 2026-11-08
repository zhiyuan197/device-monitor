package com.shenliwei.devicewatch.monitor;

import com.shenliwei.devicewatch.simulator.SimulatorConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/**
 * Development smoke: exercises the exact UI code path for adding and
 * deleting a service health check, including the broadcast refresh.
 */
public final class ServiceCheckSmoke {
    private ServiceCheckSmoke() {
    }

    public static void main(String[] args) throws Exception {
        SimulatorConfig demo = SimulatorConfig.load(Path.of("config", "demo.properties"));
        DashboardFrame frame = new DashboardFrame(
                demo.host(), demo.port(),
                demo.monitorUsername(), demo.monitorPassword().toCharArray());
        SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
        frame.connectNow();
        long deadline = System.currentTimeMillis() + 10_000;
        while (!frame.client().isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }

        ServiceCheckView registered = frame.client()
                .registerServiceCheck("SMOKE-WEB", "127.0.0.1", 9, 1000, 2)
                .get(5, TimeUnit.SECONDS);
        System.out.println("REGISTERED checkId=" + registered.checkId()
                + " name=" + registered.name());
        long broadcastDeadline = System.currentTimeMillis() + 5_000;
        boolean gotBroadcast = false;
        while (System.currentTimeMillis() < broadcastDeadline) {
            if (frame.serviceChecks.values().stream()
                    .anyMatch(check -> "SMOKE-WEB".equals(check.name()))) {
                gotBroadcast = true;
                break;
            }
            Thread.sleep(200);
        }
        System.out.println("BROADCAST REFRESH: " + (gotBroadcast ? "OK" : "MISSING"));
        List<ServiceCheckView> listed = frame.client().listServiceChecks().get(5, TimeUnit.SECONDS);
        System.out.println("LISTED count=" + listed.size()
                + " contains=" + listed.stream().anyMatch(c -> "SMOKE-WEB".equals(c.name())));
        frame.client().deleteServiceCheck(registered.checkId()).get(5, TimeUnit.SECONDS);
        System.out.println("DELETED");
        frame.dispose();
        System.exit(0);
    }
}
