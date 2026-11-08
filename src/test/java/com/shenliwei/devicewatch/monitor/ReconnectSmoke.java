package com.shenliwei.devicewatch.monitor;

import com.shenliwei.devicewatch.simulator.SimulatorConfig;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

/**
 * Development smoke: connect a real dashboard, force-drop the socket,
 * and verify the client reconnects automatically without user input.
 * Requires a running server and simulator (scripts/start-demo.ps1).
 */
public final class ReconnectSmoke {
    private ReconnectSmoke() {
    }

    public static void main(String[] args) throws Exception {
        SimulatorConfig demo = SimulatorConfig.load(Path.of("config", "demo.properties"));
        DashboardFrame frame = new DashboardFrame(
                demo.host(), demo.port(),
                demo.monitorUsername(), demo.monitorPassword().toCharArray());
        SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
        frame.connectNow();
        waitFor(() -> frame.client().isConnected(), 10_000, "initial connect");

        Socket socket = socketOf(frame.client());
        socket.close();
        waitFor(() -> !frame.client().isConnected(), 5_000, "disconnect after drop");

        boolean reconnected = waitFor(() -> frame.client().isConnected(), 15_000,
                "automatic reconnect");
        frame.dispose();
        System.out.println(reconnected
                ? "RECONNECT SMOKE PASS: connection restored automatically"
                : "RECONNECT SMOKE FAIL: not reconnected in time");
        System.exit(reconnected ? 0 : 1);
    }

    private static Socket socketOf(MonitorClient client) throws Exception {
        Field field = MonitorClient.class.getDeclaredField("socket");
        field.setAccessible(true);
        return (Socket) field.get(client);
    }

    private static boolean waitFor(
            java.util.function.BooleanSupplier condition, long timeoutMillis, String label)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(250);
        }
        System.out.println("timeout waiting for: " + label);
        return false;
    }
}
