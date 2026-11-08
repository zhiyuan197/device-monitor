package com.shenliwei.devicewatch;

import com.shenliwei.devicewatch.monitor.MonitorApp;
import com.shenliwei.devicewatch.persistence.SeedDemoApp;
import com.shenliwei.devicewatch.loadtest.LoadTestApp;
import com.shenliwei.devicewatch.server.ServerApp;
import com.shenliwei.devicewatch.simulator.SimulatorApp;
import com.shenliwei.devicewatch.simulator.AgentApp;

/**
 * Entry point for the device monitoring application.
 */
public final class Main {
    public static final String BOOTSTRAP_MESSAGE =
            "Device Monitor application is ready.";

    private Main() {
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
                printUsage();
                return;
            }
            String command = args[0];
            String[] commandArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
            switch (command) {
                case "server" -> ServerApp.run(commandArgs);
                case "seed-demo" -> SeedDemoApp.run(commandArgs);
                case "monitor" -> MonitorApp.run(commandArgs);
                case "simulate" -> SimulatorApp.run(commandArgs);
                case "agent" -> AgentApp.run(commandArgs);
                case "load-test" -> LoadTestApp.run(commandArgs);
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(2);
                }
            }
        } catch (Exception exception) {
            System.err.println("Command failed: " + exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static void printUsage() {
        System.out.println(BOOTSTRAP_MESSAGE);
        System.out.println("Usage:");
        System.out.println("  server [--config config/server.properties]");
        System.out.println(
                "  seed-demo [--db data/device-monitor.db] [--devices 4] "
                        + "[--output config/demo.properties]");
        System.out.println("  monitor [--config config/server.properties]");
        System.out.println(
                "  simulate [--config config/demo.properties] [--devices 4] "
                        + "[--scenario MIXED] [--duration-seconds 60]");
        System.out.println("  agent [--config config/demo.properties] [--interval-millis 1000]");
        System.out.println(
                "  load-test [--devices 20] [--duration-seconds 600] "
                        + "[--interval-millis 1000] [--output-dir test-results]");
    }
}

